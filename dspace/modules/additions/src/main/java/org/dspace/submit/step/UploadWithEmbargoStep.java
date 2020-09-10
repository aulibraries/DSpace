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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.util.SubmissionInfo;
import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.*;
import org.dspace.core.AUETDConstants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.curate.Curator;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.dspace.embargo.factory.EmbargoServiceFactory;
import org.dspace.embargo.service.EmbargoService;

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

    /**
     * Custom constants
     */
    public static final String AUETD_SUBMIT_REMOVE_SELECTED = "submit_remove_selected";
    public static final String AUETD_SUBMIT_EDIT_PREFIX = "submit_edit_";
    public static final String AUETD_SUBMIT_REMOVE_PREFIX = "submit_remove_";
    public static final String AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME = "create_embargo_radio";
    public static final String AUETD_EMBARGO_LENGTH_FIELD_NAME = "embargo_length";
    public static final String AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME_ERROR = AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME+ "_ERROR";
    public static final String AUETD_FILE_UPLOAD_ERROR_KEY = "FILE_UPLOAD_ERROR";
    public static final String AUETD_EMBARGO_LENGTH_FIELD_NAME_ERROR = AUETD_EMBARGO_LENGTH_FIELD_NAME + "_ERROR";
    protected static final String BITSTREAM_ID_NAME = "bitstream_id";
    protected static final String AUETD_ERROR_FLAG_LOG_MESSAGE = " Error Flag = ";

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

            if ((contentType != null) && (contentType.indexOf("multipart/form-data") != -1)) {
                // This is a multipart request, so it's a file upload
                // (return any status messages or errors reported)
                int status = processUploadFile(context, request, response, subInfo);

                log.debug(LogManager.getHeader(context, "File Upload Status", " " + Integer.toString(status)));

                // if error occurred, return immediately
                if (status != STATUS_COMPLETE) {
                    log.error(LogManager.getHeader(context, "File Upload Error",
                            AUETD_ERROR_FLAG_LOG_MESSAGE + Integer.toString(status)));

                    subInfo.putIfAbsent(AUETD_FILE_UPLOAD_ERROR_KEY, status);
                }
            }

            if (StringUtils.isNotBlank(request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME))) {
                embargoCreationAnswer = Integer
                        .parseInt(request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME));
            } else {
                log.error(LogManager.getHeader(context, "Embargo Creation Error",
                        AUETD_ERROR_FLAG_LOG_MESSAGE + String.valueOf(AUETD_STATUS_ERROR_EMBARGO_CREATION_REQUIRED)));
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
                log.debug(LogManager.getHeader(context, "Embargo Creation Request Param",
                        " request.getParameter(\"" + AUETD_EMBARGO_LENGTH_FIELD_NAME + "\") = "
                                + request.getParameter(AUETD_EMBARGO_LENGTH_FIELD_NAME)));

                log.debug(LogManager.getHeader(context, "Embargo Creation Request Param",
                        " StringUtils.isBlank(request.getParameter(\"" + AUETD_EMBARGO_LENGTH_FIELD_NAME + ")) = "
                                + Boolean.toString(
                                        StringUtils.isBlank(request.getParameter(AUETD_EMBARGO_LENGTH_FIELD_NAME)))));

                // if the requested parameter is empty then throw an error
                if (StringUtils.isBlank(request.getParameter(AUETD_EMBARGO_LENGTH_FIELD_NAME))) {
                    log.error(LogManager.getHeader(context, "Embargo Creation Error",
                            AUETD_ERROR_FLAG_LOG_MESSAGE + String.valueOf(AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED)));

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
            List<String> subInfoKeyList = new ArrayList<>(subInfo.keySet());
            for (String key : subInfoKeyList) {
                log.debug(LogManager.getHeader(context, "File Upload Processing Error Key", " " + key));

                if (key.contains("ERROR")) {
                    log.error(LogManager.getHeader(context, "File Upload Processing Error", " Throwing error " + key.toUpperCase()));
                    log.error(LogManager.getHeader(context, "File Upload Processing Error", " Returning AUETD_STATUS_ERROR (" + Integer.toString(AUETD_STATUS_ERROR) + ")"));
                    return AUETD_STATUS_ERROR;
                }
            }

            if (buttonPressed.equals("submit_save")) {
                // Remove any residual bitstream info from the submission object
                subInfo.setBitstream(null);

                if (StringUtils.isNotBlank(request.getParameter(BITSTREAM_ID_NAME))) {
                    // load info for bitstream we are editing
                    Bitstream b = bitstreamService.find(context, Util.getUUIDParameter(request, BITSTREAM_ID_NAME));

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
        if (request.getParameter(BITSTREAM_ID_NAME) != null) {
            if (buttonPressed.equals(CANCEL_EDIT_BUTTON)) {
                // canceled an edit bitstream request
                subInfo.setBitstream(null);

                // this flag will just return us to the normal upload screen
                return STATUS_EDIT_COMPLETE;
            } else {
                // load info for bitstream we are editing
                Bitstream b = bitstreamService.find(context, Util.getUUIDParameter(request, BITSTREAM_ID_NAME));

                // save bitstream to submission info
                subInfo.setBitstream(b);
            }
        } else if (buttonPressed.startsWith(AUETD_SUBMIT_EDIT_PREFIX)) {
            // get ID of bitstream that was requested for editing
            String bitstreamID = buttonPressed.substring(AUETD_SUBMIT_EDIT_PREFIX.length());

            Bitstream b = bitstreamService.find(context, UUID.fromString(bitstreamID));

            // save bitstream to submission info
            subInfo.setBitstream(b);

            subInfo.put(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME, String.valueOf(getSelectedEmbargoType(item)));
            subInfo.put(AUETD_EMBARGO_LENGTH_FIELD_NAME, String.valueOf(getEmbargoLengthInYears(context, item)));

            // return appropriate status flag to say we are now editing the
            // bitstream
            return STATUS_EDIT_BITSTREAM;
        }

        if (buttonPressed.startsWith(AUETD_SUBMIT_REMOVE_PREFIX)) {
            int status = 0;

            // Get all previously uploaded files.
            String[] ids = request.getParameterValues("remove");

            // Remove all previously uploaded files
            for (String id : ids) {
                status = processRemoveFile(context, item, UUID.fromString(id));

                // if error occurred, return immediately
                if (status != STATUS_COMPLETE) {
                    return status;
                }
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
        throws AuthorizeException, IOException, ServletException, SQLException
    {
        BitstreamFormat bf = null;
        Bitstream bitstream = null;
        String fileAttributeName = null;
        String pathStr = "-path";

        if (subInfo == null) {
            // In any event, if we don't have the submission info, the request
            // was malformed
            return STATUS_INTEGRITY_ERROR;
        }

        @SuppressWarnings("unchecked")
        Enumeration<String> requestAttributeNames = request.getAttributeNames();

        fileAttributeName = getFileRequestAttributeName(requestAttributeNames);

        if (StringUtils.isBlank(fileAttributeName)) {
            log.error(LogManager.getHeader(context, "Upload Request Param", " No file path provided."));

            log.error(LogManager.getHeader(context, "Submission Error Thrown",
                    AUETD_ERROR_FLAG_LOG_MESSAGE + Integer.toString(STATUS_NO_FILES_ERROR)));
            return STATUS_NO_FILES_ERROR;
        }

        // Load the file's path and input stream and description
        Path filePath = Paths.get((String) request.getAttribute(fileAttributeName + pathStr));
        InputStream fileInputStream = (InputStream) request.getAttribute(fileAttributeName + "-inputstream");
        
        // Create the bitstream
        Item item = subInfo.getSubmissionItem().getItem();

        // do we already have a bundle?
        List<Bundle> bundles = itemService.getBundles(item, "ORIGINAL");

        if (bundles.isEmpty()) {
            // set bundle's name to ORIGINAL
            bitstream = itemService.createSingleBitstream(context, fileInputStream, item, "ORIGINAL");
        } else {
            // we have a bundle already, just add bitstream
            bitstream = bitstreamService.create(context, bundles.get(0), fileInputStream);
        }

        // Strip all but the last filename. It would be nice
        // to know which OS the file came from.
        String uploadedFileName = filePath.toFile().getName();

        bitstream.setName(context, uploadedFileName);
        bitstream.setSource(context, filePath.toString());

        // Identify format
        bf = bitstreamFormatService.guessFormat(context, bitstream);

        /**
         * Limit the type of file that can be uploaded to PDFs. Note: Probably not the
         * best method of limiting the type of file that can be accepted, but it's the
         * only way that doesn't require a lot of customization to other parts of
         * DSpace's native source code.
         */
        if (!bf.getMIMEType().equalsIgnoreCase("application/pdf")) {
            log.error(LogManager.getHeader(context, "File Upload",
                    "ERROR - Attempting to upload file with a bad file format. File " + uploadedFileName));
            log.error(LogManager.getHeader(context, "File Upload Error Thrown",
                    AUETD_ERROR_FLAG_LOG_MESSAGE + Integer.toString(AUETD_STATUS_UNACCEPTABLE_FORMAT)));
            
            backoutBitstream(context, subInfo, bitstream, item);

            return AUETD_STATUS_UNACCEPTABLE_FORMAT;
        }

        if (bf.isInternal()) {
            log.warn("Attempt to upload file format marked as internal system use only");

            backoutBitstream(context, subInfo, bitstream, item);

            return STATUS_UPLOAD_ERROR;
        }

        // Set bitstream's format
        bitstream.setFormat(context, bf);

        // Check for virus
        if (configurationService.getBooleanProperty("submission-curation.virus-scan")) {
            Curator curator = new Curator();
            curator.addTask("vscan").curate(item);
            int status = curator.getStatus("vscan");
            if (status == Curator.CURATE_ERROR) {
                backoutBitstream(context, subInfo, bitstream, item);
                return STATUS_VIRUS_CHECKER_UNAVAILABLE;
            } else if (status == Curator.CURATE_FAIL) {
                backoutBitstream(context, subInfo, bitstream, item);
                return STATUS_CONTAINS_VIRUS;
            }
        }

        // Update to DB
        bitstreamService.update(context, bitstream);
        itemService.update(context, item);

        // If we got this far then everything is more or less ok.

        // Comment - not sure if this is the right place for a commit here
        // but I'm not brave enough to remove it - Robin.
        context.dispatchEvents();

        // save this bitstream to the submission info, as the
        // bitstream we're currently working with
        subInfo.setBitstream(bitstream);
        
        return STATUS_COMPLETE;
    }

    private String getFileRequestAttributeName(Enumeration<String> requestAttributes)
    {
        String fileAttributeName = null;
        String pathStr = "-path";

        while(requestAttributes.hasMoreElements()) {
            String attributeName = requestAttributes.nextElement();

            if (attributeName.endsWith(pathStr)) {
                fileAttributeName = attributeName.replace(pathStr, "");
            }
        }

        return fileAttributeName;
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
                                " embargo length = " + embargoLength));
                        if (StringUtils.isNotBlank(embargoLength)) {
                            embargoService.createOrModifyEmbargoMetadataValue(context, item, "embargo", "length",
                                    embargoLength);
                        }
                    }
                }

                if (StringUtils.isNotBlank(embargoService.getEmbargoMetadataValue(context, item, "embargo", "enddate"))) {
                    itemService.clearMetadata(context, item, MetadataSchema.DC_SCHEMA, "embargo", "enddate", Item.ANY);
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

    private int getSelectedEmbargoType(Item item)
        throws AuthorizeException, IOException, SQLException
    {
        int embargoType = 0;
        String embargoRights = null;
        String embargoStatus = null;

        List<MetadataValue> embargoRightsList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "rights", null, Item.ANY);
        List<MetadataValue> embargoStatusList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "status", Item.ANY);

        if (embargoRightsList != null && !embargoRightsList.isEmpty()) {
            embargoRights = embargoRightsList.get(0).getValue();
        }

        if (embargoStatusList != null && !embargoStatusList.isEmpty()) {
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
            if (embargoLengthList != null && !embargoLengthList.isEmpty()) {
                ArrayList<String> embargoLengths = new ArrayList<>();
                embargoLengths.addAll(Arrays.asList(embargoLengthList.get(0).getValue().split(":")));
                log.debug(LogManager.getHeader(context, "getting_embargo_length ", " Size of embargoLengths = " + Integer.toString(embargoLengths.size())));
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
