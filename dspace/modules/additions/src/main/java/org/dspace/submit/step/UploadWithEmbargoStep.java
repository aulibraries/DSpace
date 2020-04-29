/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submit.step;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Date;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.util.SubmissionInfo;
import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.*;
import org.dspace.core.AUETDConstants;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.curate.Curator;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.dspace.embargo.factory.EmbargoServiceFactory;
import org.dspace.embargo.service.EmbargoService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * Upload step with the advanced embargo system for DSpace. Processes the actual
 * upload of files for an item being submitted into DSpace.
 * <P>
 * This class performs all the behind-the-scenes processing that this particular
 * step requires. This class's methods are utilized by both the JSP-UI and the
 * Manakin XML-UI
 *
 * @see org.dspace.app.util.SubmissionConfig
 * @see org.dspace.app.util.SubmissionStepConfig
 * @see org.dspace.submit.step.UploadStep
 * @see org.dspace.submit.AbstractProcessingStep
 *
 * @author Tim Donohue
 * @author Keiji Suzuki
 * @version $Revision$
 */
public class UploadWithEmbargoStep extends UploadStep {
    public static final int STATUS_EDIT_POLICIES = 30;

    public static final int STATUS_EDIT_POLICIES_ERROR_SELECT_GROUP = 31;
    public static final int STATUS_EDIT_POLICIES_DUPLICATED_POLICY = 32;

    public static final int STATUS_EDIT_POLICY_ERROR_SELECT_GROUP = 33;
    public static final int STATUS_EDIT_POLICY_DUPLICATED_POLICY = 34;

    /** log4j logger */
    private static Logger log = Logger.getLogger(UploadWithEmbargoStep.class);

    protected HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    protected GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    protected ResourcePolicyService resourcePolicyService = AuthorizeServiceFactory.getInstance()
            .getResourcePolicyService();
    protected EmbargoService embargoService = EmbargoServiceFactory.getInstance().getEmbargoService();
    protected ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    /**
     * Custom constants
     */
    public static final String AUETD_SUBMIT_REMOVE_SELECTED = "submit_remove_selected";
    public static final String AUETD_SUBMIT_EDIT_PREFIX = "submit_edit_";
    public static final String AUETD_SUBMIT_REMOVE_PREFIX = "submit_remove_";
    public static final String AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME = "create_embargo_radio";
    public static final String AUETD_EMBARGO_LENGTH_FIELD_NAME = "embargo_length";
    public static final String AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME_ERROR = AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME+ "_ERROR";
    public static final String AUETD_FILE_ERROR_NAME = "file_error";
    public static final String AUETD_EMBARGO_LENGTH_FIELD_NAME_ERROR = AUETD_EMBARGO_LENGTH_FIELD_NAME + "_ERROR";

    public static final int AUETD_STATUS_UNACCEPTABLE_FORMAT = 11;
    public static final int AUETD_STATUS_ERROR = 35;
    public static final int AUETD_STATUS_ERROR_EMBARGO_CREATION_REQUIRED = 36;
    public static final int AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED = 37;

    /**
     * Do any processing of the information input by the user, and/or perform step
     * processing (if no user interaction required)
     * <P>
     * It is this method's job to save any data to the underlying database, as
     * necessary, and return error messages (if any) which can then be processed by
     * the appropriate user interface (JSP-UI or XML-UI)
     * <P>
     * NOTE: If this step is a non-interactive step (i.e. requires no UI), then it
     * should perform *all* of its processing in this method!
     *
     * @param context  current DSpace context
     * @param request  current servlet request object
     * @param response current servlet response object
     * @param subInfo  submission info object
     * @return Status or error flag which will be processed by doPostProcessing()
     *         below! (if STATUS_COMPLETE or 0 is returned, no errors occurred!)
     */
    @Override
    public int doProcessing(Context context, HttpServletRequest request, HttpServletResponse response, SubmissionInfo subInfo)
        throws AuthorizeException, IOException, ServletException, SQLException
    {
        // get button user pressed
        String buttonPressed = Util.getSubmitButton(request, NEXT_BUTTON);

        // get reference to item
        Item item = subInfo.getSubmissionItem().getItem();

        // -----------------------------------
        // Step #0: Upload new files (if any)
        // -----------------------------------
        String contentType = request.getContentType();

        int embargoCreationAnswer = 0;

        if (buttonPressed.equalsIgnoreCase(SUBMIT_UPLOAD_BUTTON) || buttonPressed.equalsIgnoreCase("submit_save")) {

            if (StringUtils.isNotBlank(request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME))) {
                embargoCreationAnswer = Integer
                        .parseInt(request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME));
            } else {
                log.error(LogManager.getHeader(context, "Submission Error Thrown",
                        " Error Flag = " + String.valueOf(AUETD_STATUS_ERROR_EMBARGO_CREATION_REQUIRED)));
                subInfo.putIfAbsent(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME_ERROR,
                        AUETD_STATUS_ERROR_EMBARGO_CREATION_REQUIRED);
            }

            /**
             * If the user has chosen to create an embargo, but forgot to set a lift date
             * then return an error code. Adding this check here will prevent the file the
             * user has selected from being uploaded until they've filled out an embargo
             * date.
             */
            if (embargoCreationAnswer == 2 || embargoCreationAnswer == 3) {
                log.debug(LogManager.getHeader(context, "Upload Request Param",
                        " request.getParameter(\"" + AUETD_EMBARGO_LENGTH_FIELD_NAME + "\") = "
                                + String.valueOf(request.getParameter(AUETD_EMBARGO_LENGTH_FIELD_NAME))));

                log.debug(LogManager.getHeader(context, "Upload Request Param",
                        " StringUtils.isBlank(request.getParameter(\"" + AUETD_EMBARGO_LENGTH_FIELD_NAME + ")) = "
                                + String.valueOf(
                                        StringUtils.isBlank(request.getParameter(AUETD_EMBARGO_LENGTH_FIELD_NAME)))));

                // if the requested parameter is empty then throw an error
                if (StringUtils.isBlank(request.getParameter(AUETD_EMBARGO_LENGTH_FIELD_NAME))) {
                    log.error(LogManager.getHeader(context, "Submission Error Thrown",
                            " Error Flag = " + String.valueOf(AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED)));

                    subInfo.putIfAbsent(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME,
                            request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME));
                    subInfo.putIfAbsent(AUETD_EMBARGO_LENGTH_FIELD_NAME_ERROR,
                            AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED);
                } else {
                    subInfo.putIfAbsent(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME,
                            request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME));
                    subInfo.putIfAbsent(AUETD_EMBARGO_LENGTH_FIELD_NAME,
                            request.getParameter(AUETD_EMBARGO_LENGTH_FIELD_NAME));
                }
            } else if (embargoCreationAnswer == 1) {
                subInfo.putIfAbsent(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME,
                        request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME));
            }

            // if there were errors then stop execution here
            @SuppressWarnings("unchecked")
            List<String> subInfoKeyList = new ArrayList<String>(subInfo.keySet());
            for (String key : subInfoKeyList) {
                log.debug(LogManager.getHeader(context, "Submission Error Key", " Key Name = " + key));

                log.debug(LogManager.getHeader(context, "Submission Error Key",
                        " key.contains(\"ERROR\") = " + String.valueOf(key.contains("ERROR"))));

                if (key.contains("ERROR")) {
                    return AUETD_STATUS_ERROR;
                }
            }

            if (buttonPressed.equalsIgnoreCase(SUBMIT_UPLOAD_BUTTON) && !itemService.hasUploadedFiles(item)) {
                if ((contentType != null) && (contentType.indexOf("multipart/form-data") != -1)) {
                    // This is a multipart request, so it's a file upload
                    // (return any status messages or errors reported)
                    int status = processUploadFile(context, request, response, subInfo);

                    // if error occurred, return immediately
                    if (status != STATUS_COMPLETE) {
                        log.error(LogManager.getHeader(context, "Submission Error Thrown",
                                " Error Flag = " + String.valueOf(status)));

                        subInfo.put("file_ERROR", status);
                    }
                }
            }

            // if there were errors then stop execution here
            @SuppressWarnings("unchecked")
            List<String> subInfoKeyList2 = new ArrayList<String>(subInfo.keySet());
            for (String key2 : subInfoKeyList2) {
                log.debug(LogManager.getHeader(context, "Submission Error Key", " Key Name = " + key2));

                log.debug(LogManager.getHeader(context, "Submission Error Key",
                        " key.contains(\"ERROR\") = " + String.valueOf(key2.contains("ERROR"))));

                if (key2.contains("ERROR")) {
                    return AUETD_STATUS_ERROR;
                }
            }

            if (buttonPressed.equals("submit_save")) {
                // Remove any residual bitstream info from the submission object
                subInfo.setBitstream(null);

                if (StringUtils.isNotBlank(request.getParameter("bitstream_id"))) {
                    // load info for bitstream we are editing
                    Bitstream b = bitstreamService.find(context, Util.getUUIDParameter(request, "bitstream_id"));

                    // save bitstream to submission info
                    subInfo.setBitstream(b);
                }
            }

            /**
             * If the user has chosen to create an embargo then call the method
             * processAccessFields otherwise call the ETDEmbargoSetter method
             * setEmbargoStatusMDV. The processAccessFields method is designed to generate
             * the correct resource policies and add specific values to an item's metadata
             * field list. If the user has chosen not to create an embargo then we will
             * create and populate the item's embargo status metadata field.
             */
            processAUETDEmbargoAccessFields(context, request, subInfo);
        }

        // ---------------------------------------------
        // Step #1: Check if this was just a request to
        // edit file information.
        // (or canceled editing information)
        // ---------------------------------------------
        // check if we're already editing a specific bitstream
        if (request.getParameter("bitstream_id") != null) {
            if (buttonPressed.equals(CANCEL_EDIT_BUTTON)) {
                // canceled an edit bitstream request
                subInfo.setBitstream(null);

                // this flag will just return us to the normal upload screen
                return STATUS_EDIT_COMPLETE;
            } else {
                // load info for bitstream we are editing
                Bitstream b = bitstreamService.find(context, Util.getUUIDParameter(request, "bitstream_id"));

                // save bitstream to submission info
                subInfo.setBitstream(b);
            }
        } else if (buttonPressed.startsWith("submit_edit_")) {
            // get ID of bitstream that was requested for editing
            String bitstreamID = buttonPressed.substring("submit_edit_".length());

            Bitstream b = bitstreamService.find(context, UUID.fromString(bitstreamID));

            // save bitstream to submission info
            subInfo.setBitstream(b);

            subInfo.put(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME, String.valueOf(getSelectedEmbargoType(item)));
            subInfo.put(AUETD_EMBARGO_LENGTH_FIELD_NAME, String.valueOf(getEmbargoLengthInYears(context, item)));

            // return appropriate status flag to say we are now editing the
            // bitstream
            return STATUS_EDIT_BITSTREAM;
        }

        if (buttonPressed.startsWith("submit_remove_")) {
            UUID id = null;
            int status = 0;

            // A single file "remove" button must have been pressed
            if (StringUtils.isNotBlank(request.getParameter("remove"))) {
                id = UUID.fromString(request.getParameter("remove"));
            }

            if (id != null) {
                status = processRemoveFile(context, item, id);
            }

            // if error occurred, return immediately
            if (status != STATUS_COMPLETE) {
                return status;
            }

            // remove current bitstream from Submission Info
            subInfo.setBitstream(null);

            processAUETDEmbargoAccessFields(context, request, subInfo);
        }

        return STATUS_COMPLETE;
    }

    /**
     * Process the upload of a new file!
     *
     * @param context  current DSpace context
     * @param request  current servlet request object
     * @param response current servlet response object
     * @param subInfo  submission info object
     *
     * @return Status or error flag which will be processed by UI-related code! (if
     *         STATUS_COMPLETE or 0 is returned, no errors occurred!)
     */
    @Override
    public int processUploadFile(Context context, HttpServletRequest request, HttpServletResponse response, SubmissionInfo subInfo)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
        //boolean formatKnown = true;
        //boolean fileOK = false;
        BitstreamFormat bf = null;
        Bitstream b = null;

        // NOTE: File should already be uploaded.
        // Manakin does this automatically via Cocoon.
        // For JSP-UI, the SubmissionController.uploadFiles() does the actual upload

        Enumeration attNames = request.getAttributeNames();

        if (subInfo == null) {
            // In any event, if we don't have the submission info, the request
            // was malformed
            return STATUS_INTEGRITY_ERROR;
        }

        // loop through our request attributes
        while (attNames.hasMoreElements()) {
            String attr = (String) attNames.nextElement();

            // if this ends with "-path", this attribute
            // represents a newly uploaded file
            if (attr.endsWith("-path")) {
                // strip off the -path to get the actual parameter
                // that the file was uploaded as
                String param = attr.replace("-path", "");

                // Load the file's path and input stream and description
                String filePath = (String) request.getAttribute(param + "-path");
                InputStream fileInputStream = (InputStream) request.getAttribute(param + "-inputstream");

                // if information wasn't passed by User Interface, we had a problem
                // with the upload
                // if (filePath == null || fileInputStream == null)
                if (StringUtils.isBlank(filePath) || fileInputStream == null) {
                    log.error(LogManager.getHeader(context, "Upload Request Param", " File = NULL"));

                    log.error(LogManager.getHeader(context, "Submission Error Thrown",
                            " Error Flag = " + String.valueOf(STATUS_NO_FILES_ERROR)));

                    subInfo.put("file_ERROR", STATUS_NO_FILES_ERROR);

                    return STATUS_UPLOAD_ERROR;
                }

                // Create the bitstream
                Item item = subInfo.getSubmissionItem().getItem();

                // do we already have a bundle?
                List<Bundle> bundles = itemService.getBundles(item, "ORIGINAL");

                if (bundles.size() < 1) {
                    // set bundle's name to ORIGINAL
                    b = itemService.createSingleBitstream(context, fileInputStream, item, "ORIGINAL");
                } else {
                    // we have a bundle already, just add bitstream
                    b = bitstreamService.create(context, bundles.get(0), fileInputStream);
                }

                // Strip all but the last filename. It would be nice
                // to know which OS the file came from.
                String noPath = filePath;

                while (noPath.indexOf('/') > -1) {
                    noPath = noPath.substring(noPath.indexOf('/') + 1);
                }

                while (noPath.indexOf('\\') > -1) {
                    noPath = noPath.substring(noPath.indexOf('\\') + 1);
                }

                b.setName(context, noPath);
                b.setSource(context, filePath);

                // Identify the format
                bf = bitstreamFormatService.guessFormat(context, b);

                /**
                 * Limit the type of file that can be uploaded to PDFs. Note: Probably not the
                 * best method of limiting the type of file that can be accepted, but it's the
                 * only way that doesn't require a lot of customization to other parts of
                 * DSpace's native source code.
                 */
                // Only PDF type files are acceptable.
                if (bf == null || !bf.getMIMEType().equalsIgnoreCase("application/pdf")) {
                    log.error(LogManager.getHeader(context, "File Upload",
                            "ERROR - Attempting to upload file with a bad file format. File " + noPath));
                    backoutBitstream(context, subInfo, b, item);
                    log.error(LogManager.getHeader(context, "Submission Error Thrown",
                            " Error Flag = " + String.valueOf(AUETD_STATUS_UNACCEPTABLE_FORMAT)));
                    return AUETD_STATUS_UNACCEPTABLE_FORMAT;
                }

                b.setFormat(context, bf);

                // Update to DB
                bitstreamService.update(context, b);
                itemService.update(context, item);

                if ((bf != null) && (bf.isInternal())) {
                    log.warn("Attempt to upload file format marked as internal system use only");
                    backoutBitstream(context, subInfo, b, item);
                    return STATUS_UPLOAD_ERROR;
                }

                // Check for virus
                if (configurationService.getBooleanProperty("submission-curation.virus-scan")) {
                    Curator curator = new Curator();
                    curator.addTask("vscan").curate(item);
                    int status = curator.getStatus("vscan");
                    if (status == Curator.CURATE_ERROR) {
                        backoutBitstream(context, subInfo, b, item);
                        return STATUS_VIRUS_CHECKER_UNAVAILABLE;
                    } else if (status == Curator.CURATE_FAIL) {
                        backoutBitstream(context, subInfo, b, item);
                        return STATUS_CONTAINS_VIRUS;
                    }
                }

                // If we got this far then everything is more or less ok.

                // Comment - not sure if this is the right place for a commit here
                // but I'm not brave enough to remove it - Robin.
                context.dispatchEvents();

                // save this bitstream to the submission info, as the
                // bitstream we're currently working with
                subInfo.setBitstream(b);

            } // end if attribute ends with "-path"
        } // end while

        return STATUS_COMPLETE;
    }

    private void processAccessFields(Context context, HttpServletRequest request, SubmissionInfo subInfo, Bitstream b)
        throws SQLException, AuthorizeException
    {
        // ResourcePolicy Management
        boolean isAdvancedFormEnabled = configurationService
                .getBooleanProperty("webui.submission.restrictstep.enableAdvancedForm", false);
        // if it is a simple form we should create the policy for Anonymous
        // if Anonymous does not have right on this collection, create policies for any
        // other groups with
        // DEFAULT_ITEM_READ specified.
        if (!isAdvancedFormEnabled) {
            Date startDate = null;
            try {
                startDate = DateUtils.parseDate(request.getParameter("embargo_until_date"),
                        new String[] { "yyyy-MM-dd", "yyyy-MM", "yyyy" });
            } catch (Exception e) {
                // Ignore start date already null
            }
            String reason = request.getParameter("reason");
            authorizeService.generateAutomaticPolicies(context, startDate, reason, b,
                    (Collection) handleService.resolveToObject(context, subInfo.getCollectionHandle()));
        }
    }

    private void processAUETDEmbargoAccessFields(Context context, HttpServletRequest request, SubmissionInfo subInfo)
        throws AuthorizeException, IOException, SQLException
    {
        Item item = subInfo.getSubmissionItem().getItem();
        int embargoCreationAnswer = 0;

        if (StringUtils.isNotBlank(request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME))) {
            embargoCreationAnswer = Integer.parseInt(request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME));

            if (embargoCreationAnswer == 2 || embargoCreationAnswer == 3) {
                String embargoRights = null;
                if (embargoCreationAnswer == 2) {
                    embargoRights = AUETDConstants.EMBARGO_NOT_AUBURN_STR;
                } else if (embargoCreationAnswer == 3) {
                    embargoRights = AUETDConstants.EMBARGO_GLOBAL_STR;
                }

                if (StringUtils.isNotBlank(embargoRights)) {
                    log.debug(LogManager.getHeader(context, "Generating ETD Embargo Rights MDV",
                            " embargo rights = " + embargoRights));
                    embargoService.createOrModifyEmbargoMetadataValue(context, item, "rights", null, embargoRights);

                    log.debug(LogManager.getHeader(context, "Generating ETD Embargo Status MDV",
                            " embargo status = "+AUETDConstants.EMBARGOED));
                    embargoService.createOrModifyEmbargoMetadataValue(context, item, "embargo", "status",
                            AUETDConstants.EMBARGOED);
                }

                if (StringUtils.isNotBlank(request.getParameter(AUETD_EMBARGO_LENGTH_FIELD_NAME))) {
                    String selectedEmbargoLengthValue = request.getParameter(AUETD_EMBARGO_LENGTH_FIELD_NAME);
                    if (StringUtils.isNotBlank(selectedEmbargoLengthValue)) {
                        String embargoLength = embargoService.generateEmbargoLength(context, item, selectedEmbargoLengthValue);
                        log.debug(LogManager.getHeader(context, "Generating ETD Embargo Length MDV",
                                " embargo length = " + String.valueOf(embargoLength)));
                        if (StringUtils.isNotBlank(embargoLength)) {
                            embargoService.createOrModifyEmbargoMetadataValue(context, item, "embargo", "length",
                                    embargoLength);
                        }
                    }
                }

                if (subInfo.getBitstream() != null) {
                    embargoService.generateAUETDEmbargoPolicies(context, subInfo.getBitstream(), embargoCreationAnswer, 
                        (Collection) handleService.resolveToObject(context, subInfo.getCollectionHandle()));
                }
            } else if (embargoCreationAnswer == 1 || embargoCreationAnswer == 0) {
                embargoService.createOrModifyEmbargoMetadataValue(context, item, "embargo", "status", AUETDConstants.NOT_EMBARGOED);
                itemService.clearMetadata(context, item, MetadataSchema.DC_SCHEMA, "embargo", "enddate", Item.ANY);
                itemService.clearMetadata(context, item, MetadataSchema.DC_SCHEMA, "embargo", "length", Item.ANY);
                itemService.clearMetadata(context, item, MetadataSchema.DC_SCHEMA, "rights", null, Item.ANY);

                if (subInfo.getBitstream() != null) {
                    authorizeService.generateAutomaticPolicies(context, null, null, subInfo.getBitstream(), 
                        (Collection) handleService.resolveToObject(context, subInfo.getCollectionHandle()));
                }
            }
        }
    }

    private int editBitstreamPolicies(HttpServletRequest request, Context context, SubmissionInfo subInfo,
            String buttonPressed) throws SQLException, AuthorizeException {

        // FORM: EditBitstreamPolicies SELECTED OPERATION: Return
        if (buttonPressed.equals("bitstream_list_submit_return")) {
            return STATUS_COMPLETE;
        }
        // FORM: UploadStep SELECTED OPERATION: go to EditBitstreamPolicies
        else if (buttonPressed.startsWith("submit_editPolicy_")) {
            UUID bitstreamID = UUID.fromString(buttonPressed.substring("submit_editPolicy_".length()));
            Bitstream b = bitstreamService.find(context, bitstreamID);
            subInfo.setBitstream(b);
            return STATUS_EDIT_POLICIES;
        }
        // FORM: EditBitstreamPolicies SELECTED OPERATION: Add New Policy.
        else if (buttonPressed.startsWith(AccessStep.FORM_ACCESS_BUTTON_ADD)) {
            Bitstream b = bitstreamService.find(context, Util.getUUIDParameter(request, "bitstream_id"));
            subInfo.setBitstream(b);

            int result = -1;
            if ((result = AccessStep.checkForm(request)) != 0) {
                return result;
            }
            Date dateStartDate = AccessStep.getEmbargoUntil(request);
            String reason = request.getParameter("reason");
            String name = request.getParameter("name");

            Group group = null;
            if (request.getParameter("group_id") != null) {
                try {
                    group = groupService.find(context, Util.getUUIDParameter(request, "group_id"));
                } catch (NumberFormatException nfe) {
                    return STATUS_EDIT_POLICIES_ERROR_SELECT_GROUP;
                }
            }
            ResourcePolicy rp;
            if ((rp = authorizeService.createOrModifyPolicy(null, context, name, group, null, dateStartDate,
                    org.dspace.core.Constants.READ, reason, b)) == null) {
                return STATUS_EDIT_POLICIES_DUPLICATED_POLICY;
            }
            resourcePolicyService.update(context, rp);
            context.dispatchEvents();
            return STATUS_EDIT_POLICIES;
        }
        // FORM: EditBitstreamPolicies SELECTED OPERATION: go to EditPolicyForm
        else if (org.dspace.submit.step.AccessStep.wasEditPolicyPressed(context, buttonPressed, subInfo)) {
            Bitstream b = bitstreamService.find(context, Util.getUUIDParameter(request, "bitstream_id"));
            subInfo.setBitstream(b);
            return org.dspace.submit.step.AccessStep.STATUS_EDIT_POLICY;
        }
        // FORM: EditPolicy SELECTED OPERATION: Save or Cancel.
        else if (org.dspace.submit.step.AccessStep.comeFromEditPolicy(request)) {
            Bitstream b = bitstreamService.find(context, Util.getUUIDParameter(request, "bitstream_id"));
            subInfo.setBitstream(b);
            String reason = request.getParameter("reason");
            String name = request.getParameter("name");

            Group group = groupService.findByName(context, Group.ANONYMOUS);
            if (request.getParameter("group_id") != null) {
                try {
                    group = groupService.find(context, UUID.fromString(request.getParameter("group_id")));
                } catch (NumberFormatException nfe) {
                    return STATUS_EDIT_POLICIES_ERROR_SELECT_GROUP;
                }
            }
            if (org.dspace.submit.step.AccessStep.saveOrCancelEditPolicy(context, request, subInfo, buttonPressed, b,
                    name, group, reason) == org.dspace.submit.step.AccessStep.EDIT_POLICY_STATUS_DUPLICATED_POLICY)
                return STATUS_EDIT_POLICY_DUPLICATED_POLICY;

            return STATUS_EDIT_POLICIES;
        }
        // FORM: EditBitstreamPolicies SELECTED OPERATION: Remove Policies
        if (org.dspace.submit.step.AccessStep.wasRemovePolicyPressed(buttonPressed)) {
            Bitstream b = bitstreamService.find(context, Util.getUUIDParameter(request, "bitstream_id"));
            subInfo.setBitstream(b);
            org.dspace.submit.step.AccessStep.removePolicy(context, buttonPressed);
            context.dispatchEvents();
            return STATUS_EDIT_POLICIES;
        }
        return -1;
    }

    private int getSelectedEmbargoType(Item item)
        throws AuthorizeException, IOException, SQLException
    {
        int embargoType = 0;
        String embargoRights = null;
        String embargoStatus = null;

        List<MetadataValue> embargoRightsList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "rights", null, Item.ANY);
        List<MetadataValue> embargoStatusList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "status", Item.ANY);

        if (embargoRightsList != null && embargoRightsList.size() > 0) {
            embargoRights = embargoRightsList.get(0).getValue();
        }

        if (embargoStatusList != null & embargoStatusList.size() > 0) {
            embargoStatus = embargoStatusList.get(0).getValue();
        }

        if (StringUtils.isNotBlank(embargoStatus)) {
            if (embargoStatus.equals(AUETDConstants.EMBARGOED)) {
                if (StringUtils.isNotBlank(embargoRights)) {
                    if (embargoRights.equals(AUETDConstants.EMBARGO_NOT_AUBURN_STR)) {
                        embargoType = 2;
                    } else if (embargoRights.equals(AUETDConstants.EMBARGO_GLOBAL_STR)) {
                        embargoType = 3;
                    }
                }
            } else if (embargoStatus.equals(AUETDConstants.NOT_EMBARGOED)) {
                embargoType = 1;
            }
        }

        return embargoType;
    }

    private int getEmbargoLengthInYears(Context context, Item item)
        throws AuthorizeException, IOException, SQLException
    {
        int embargoLength = 0;

        if (item != null) {
            java.util.List<MetadataValue> embargoLengthList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "length", Item.ANY);
            if (embargoLengthList != null && embargoLengthList.size() > 0) {
                ArrayList<String> embargoLengths = new ArrayList<String>();
                embargoLengths.addAll(Arrays.asList(embargoLengthList.get(0).getValue().split(":")));
                log.debug(LogManager.getHeader(context, "getting_embargo_length ", " Size of embargoLengths = "+String.valueOf(embargoLengths.size())));
                if (embargoLengths.size() > 1) {
                    int lengthNum = Integer.parseInt(embargoLengths.get(1));
                    if (lengthNum > 0) {
                        embargoLength = lengthNum / 12;
                    }
                }
            }
        }

        return embargoLength;
    }
}
