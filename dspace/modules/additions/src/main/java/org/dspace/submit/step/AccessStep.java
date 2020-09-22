/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submit.step;

import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.util.SubmissionInfo;
import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.core.AUETDConstants;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.embargo.factory.EmbargoServiceFactory;
import org.dspace.embargo.service.EmbargoService;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.dspace.submit.AbstractProcessingStep;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * This class manages the access step during the submission
 *
 * @author Fabio Bolognesi (fabio at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 *
 */
public class AccessStep extends AbstractProcessingStep {

    public static final int STATUS_ERROR_FORMAT_DATE = 1;
    public static final int STATUS_ERROR_MISSING_DATE = 2;
    public static final int STATUS_ERROR_SELECT_GROUP = 3;
    public static final int STATUS_DUPLICATED_POLICY = 4;
    public static final int EDIT_POLICY_STATUS_DUPLICATED_POLICY = 5;

    // edit file information
    public static final int STATUS_EDIT_POLICY = 10;

    public static final String SUB_INFO_SELECTED_RP = "SUB_INFO_SELECTED_RP";

    /** log4j logger */
    private static Logger log = Logger.getLogger(AccessStep.class);

    // OPERATIONS
    public static final String FORM_EDIT_BUTTON_CANCEL = "submit_edit_cancel";
    public static final String FORM_EDIT_BUTTON_SAVE = "submit_save";
    public static final String FORM_ACCESS_BUTTON_ADD = "submit_add_policy";

    protected static GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    protected HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    protected static AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    protected static ResourcePolicyService resourcePolicyService = AuthorizeServiceFactory.getInstance()
            .getResourcePolicyService();
    
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

    protected EmbargoService embargoService = EmbargoServiceFactory.getInstance().getEmbargoService();

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
    public int doProcessing(Context context, HttpServletRequest request, HttpServletResponse response,
            SubmissionInfo subInfo) throws ServletException, IOException, SQLException, AuthorizeException {

        String buttonPressed = Util.getSubmitButton(request, CANCEL_BUTTON);

        // get reference to item
        Item item = subInfo.getSubmissionItem().getItem();

        int embargoCreationAnswer = 0;

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
            log.debug(LogManager.getHeader(context, "Access Embargo Processing Error Key", " " + key));

            if (key.contains("ERROR")) {
                log.error(LogManager.getHeader(context, "Access Embargo Processing Error", " Throwing error " + key.toUpperCase()));
                log.error(LogManager.getHeader(context, "Access Embargo Processing Error", " Returning AUETD_STATUS_ERROR (" + Integer.toString(AUETD_STATUS_ERROR) + ")"));
                return AUETD_STATUS_ERROR;
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

        /* Group group = null;
        if (request.getParameter("group_id") != null) {
            try {
                group = groupService.find(context, UUID.fromString(request.getParameter("group_id")));
            } catch (NumberFormatException nfe) {
                return STATUS_ERROR_SELECT_GROUP;
            }
        } */

        /* String name = request.getParameter("name");
        String reason = request.getParameter("reason"); */

        // SELECTED OPERATION: go to EditPolicyForm
        /* if (wasEditPolicyPressed(context, buttonPressed, subInfo))
            return STATUS_EDIT_POLICY; */

        // SELECTED OPERATION: Remove Policies
        /* if (wasRemovePolicyPressed(buttonPressed)) {
            removePolicy(context, buttonPressed);
            context.dispatchEvents();
            return STATUS_COMPLETE;
        } */

        // SELECTED OPERATION: Save or Cancel EditPolicy.
        /* if (comeFromEditPolicy(request)) {
            return saveOrCancelEditPolicy(context, request, subInfo, buttonPressed, item, name, group, reason);
        } */

        // SELECTED OPERATION: ADD Policy
        /* if (wasAddPolicyPressed(buttonPressed)) {

            int result = -1;
            if ((result = checkForm(request)) != 0) {
                return result;
            }

            // handle private checkbox
            item.setDiscoverable(true);
            if (request.getParameter("private_option") != null) {
                item.setDiscoverable(false);
            }

            Date dateStartDate = getEmbargoUntil(request);
            ResourcePolicy rp = null;
            if ((rp = authorizeService.createOrModifyPolicy(null, context, name, group, null, dateStartDate,
                    org.dspace.core.Constants.READ, reason, item)) == null) {
                return STATUS_DUPLICATED_POLICY;
            }
            resourcePolicyService.update(context, rp);
            context.dispatchEvents();
            return STATUS_COMPLETE;
        } */

        // if arrive here Next, Previous or Save has been pressed
        /* boolean isAdvancedFormEnabled = configurationService
                .getBooleanProperty("webui.submission.restrictstep.enableAdvancedForm", false); */

        // if it is a simple form we should create the policy for Anonymous
        // if Anonymous does not have right on this collection, create policies for any
        // other groups with
        // DEFAULT_ITEM_READ specified.
        /* if (!isAdvancedFormEnabled) {
            int result = checkForm(request);
            if (result != 0) {
                return result;
            }
            authorizeService.generateAutomaticPolicies(context, getEmbargoUntilDate(request), reason, item,
                    (Collection) handleService.resolveToObject(context, subInfo.getCollectionHandle()));
        } */
        // else{
        // Date dateStartDate = getEmbargoUntil(request);
        // createOrModifyPolicy(null, context, name, groupID, null, dateStartDate,
        // org.dspace.core.Constants.READ, reason, item);
        // }
        /* item.setDiscoverable(true);
        if (request.getParameter("private_option") != null) {
            item.setDiscoverable(false);
        } */
        itemService.update(context, item);
        context.dispatchEvents();

        return STATUS_COMPLETE;
    }

    public static boolean wasEditPolicyPressed(Context context, String buttonPressed, SubmissionInfo subInfo)
            throws SQLException {
        if (buttonPressed.startsWith("submit_edit_edit_policies_") && !buttonPressed.equals(FORM_EDIT_BUTTON_CANCEL)) {
            String idPolicy = buttonPressed.substring("submit_edit_edit_policies_".length());
            ResourcePolicy rp = resourcePolicyService.find(context, Integer.parseInt(idPolicy));
            subInfo.put(SUB_INFO_SELECTED_RP, rp);
            return true;
        }
        return false;
    }

    public boolean wasAddPolicyPressed(String buttonPressed) throws SQLException {
        return (buttonPressed.equalsIgnoreCase(FORM_ACCESS_BUTTON_ADD));
    }

    public static boolean wasRemovePolicyPressed(String buttonPressed) throws SQLException {
        return (buttonPressed.startsWith("submit_delete_edit_policies_"));
    }

    public static boolean comeFromEditPolicy(HttpServletRequest request) throws SQLException {
        return (request.getParameter("policy_id") != null);
    }

    public static int saveOrCancelEditPolicy(Context context, HttpServletRequest request, SubmissionInfo subInfo,
            String buttonPressed, DSpaceObject dso, String name, Group group, String reason)
            throws AuthorizeException, SQLException {
        if (buttonPressed.equals(FORM_EDIT_BUTTON_CANCEL)) {
            return STATUS_COMPLETE;
        } else if (buttonPressed.equals(FORM_EDIT_BUTTON_SAVE)) {
            String idPolicy = request.getParameter("policy_id");
            ResourcePolicy resourcePolicy = resourcePolicyService.find(context, Integer.parseInt(idPolicy));
            subInfo.put(SUB_INFO_SELECTED_RP, resourcePolicy);
            Date dateStartDate = getEmbargoUntil(request);
            if ((resourcePolicy = authorizeService.createOrModifyPolicy(resourcePolicy, context, name, group, null,
                    dateStartDate, Constants.READ, reason, dso)) == null) {
                return EDIT_POLICY_STATUS_DUPLICATED_POLICY;
            }

            resourcePolicyService.update(context, resourcePolicy);
            context.dispatchEvents();
        }
        return STATUS_COMPLETE;
    }

    public static void removePolicy(Context context, String buttonPressed) throws SQLException, AuthorizeException {
        String idPolicy = buttonPressed.substring("submit_delete_edit_policies_".length());
        ResourcePolicy rp = resourcePolicyService.find(context, Integer.parseInt(idPolicy));
        resourcePolicyService.delete(context, rp);
    }

    public static int checkForm(HttpServletRequest request) {

        String selectedRadio = null;
        String dateEmbargoUntil = request.getParameter("embargo_until_date");

        // RADIO_OPEN_ACCESS_ITEM_VISIBLE=0;
        // RADIO_OPEN_ACCESS_ITEM_EMBARGOED=1;
        if ((selectedRadio = request.getParameter("open_access_radios")) != null && Integer.parseInt(selectedRadio) == 1
                && (dateEmbargoUntil == null || dateEmbargoUntil.equals(""))) {
            return STATUS_ERROR_MISSING_DATE;
        }

        if (dateEmbargoUntil != null && !dateEmbargoUntil.equals("")) {
            Date startDate = getEmbargoUntilDate(request);
            if (startDate == null) {
                return STATUS_ERROR_FORMAT_DATE;
            }
        }
        return 0;
    }

    public static Date getEmbargoUntil(HttpServletRequest request) {
        // RADIO_OPEN_ACCESS_ITEM_VISIBLE=0;
        // RADIO_OPEN_ACCESS_ITEM_EMBARGOED=1;
        String selectedRadio;
        Date dateStartDate = null;
        if ((selectedRadio = request.getParameter("open_access_radios")) != null
                && Integer.parseInt(selectedRadio) == 1) {
            Date startDate = getEmbargoUntilDate(request);
            if (startDate != null)
                dateStartDate = startDate;
        }
        return dateStartDate;
    }

    private static Date getEmbargoUntilDate(HttpServletRequest request) {
        Date startDate = null;
        try {
            startDate = DateUtils.parseDate(request.getParameter("embargo_until_date"),
                    new String[] { "yyyy-MM-dd", "yyyy-MM", "yyyy" });
        } catch (Exception e) {
            // Ignore start date is already null
        }
        return startDate;
    }

    /**
     * Retrieves the number of pages that this "step" extends over. This method is
     * used to build the progress bar.
     * <P>
     * This method may just return 1 for most steps (since most steps consist of a
     * single page). But, it should return a number greater than 1 for any "step"
     * which spans across a number of HTML pages. For example, the configurable
     * "Describe" step (configured using input-forms.xml) overrides this method to
     * return the number of pages that are defined by its configuration file.
     * <P>
     * Steps which are non-interactive (i.e. they do not display an interface to the
     * user) should return a value of 1, so that they are only processed once!
     * 
     * @param request The HTTP Request
     * @param subInfo The current submission information object
     * 
     * @return the number of pages in this step
     */
    @Override
    public int getNumberOfPages(HttpServletRequest request, SubmissionInfo subInfo) throws ServletException {
        return 1;

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
