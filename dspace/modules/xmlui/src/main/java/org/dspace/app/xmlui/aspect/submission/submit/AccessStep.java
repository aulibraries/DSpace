/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.submission.submit;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.xmlui.aspect.submission.AbstractSubmissionStep;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.AUETDConstants;
import static org.dspace.submit.step.AccessStep.AUETD_CREATE_EMBARGO_QUESTION_FIELD_NAME;
import static org.dspace.submit.step.AccessStep.AUETD_CREATE_EMBARGO_QUESTION_FIELD_NAME_ERROR;
import static org.dspace.submit.step.AccessStep.AUETD_EMBARGO_LENGTH_FIELD_NAME;
import static org.dspace.submit.step.AccessStep.AUETD_EMBARGO_LENGTH_FIELD_NAME_ERROR;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AccessStep extends AbstractSubmissionStep
{
    private static final Logger log = Logger.getLogger(AccessStep.class);

    /** Language Strings **/
    protected static final Message T_head = message("xmlui.Submission.submit.AccessStep.head");
    protected static final Message T_submit_add_policy = message("xmlui.Submission.submit.AccessStep.submit_add_policy");
    protected static final Message T_private_settings = message("xmlui.Submission.submit.AccessStep.private_settings");
    protected static final Message T_private_settings_help = message("xmlui.Submission.submit.AccessStep.private_settings_help");
	protected static final Message T_private_label = message("xmlui.Submission.submit.AccessStep.private_settings_label");
	protected static final Message T_private_item = message("xmlui.Submission.submit.AccessStep.review_private_item");
	protected static final Message T_public_item = message("xmlui.Submission.submit.AccessStep.review_public_item");
	protected static final Message T_policy_head = message("xmlui.Submission.submit.AccessStep.new_policy_head");

    public static final int CHECKBOX_PRIVATE_ITEM=1;
    public static final int RADIO_OPEN_ACCESS_ITEM_VISIBLE=0;
    public static final int RADIO_OPEN_ACCESS_ITEM_EMBARGOED=1;

    private EditPolicyStep editPolicy= null;

    // Custom Constants Section
    protected static final Message AUETD_CREATE_EMBARGO_QUESTION_LABEL =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_create_embargo_label");
    protected static final Message AUETD_CREATE_EMBARGO_RADIO_BUTTON1 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_create_embargo_radio_button1_text");
    protected static final Message AUETD_CREATE_EMBARGO_RADIO_BUTTON2 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_create_embargo_radio_button2_text");
    protected static final Message AUETD_CREATE_EMBARGO_RADIO_BUTTON3 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_create_embargo_radio_button3_text");
    protected static final Message AUETD_STATUS_ERROR_EMBARGO_CREATION_REQUIRED =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_create_embargo_required_error");

    // Date messages
    protected static final Message AUETD_EMBARGO_LENGTH_LABEL =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_length_label");
    protected static final Message AUETD_EMBARGO_LENGTH_HELP =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_length_help");
    protected static final Message AUETD_EMBARGO_LENGTH_RADIO_OPTION1 = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_length_radio_option1_text");
    protected static final Message AUETD_EMBARGO_LENGTH_RADIO_OPTION2 = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_length_radio_option2_text");
    protected static final Message AUETD_EMBARGO_LENGTH_RADIO_OPTION3 = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_length_radio_option3_text");
    protected static final Message AUETD_EMBARGO_LENGTH_RADIO_OPTION4 = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_length_radio_option4_text");
    protected static final Message AUETD_EMBARGO_LENGTH_RADIO_OPTION5 = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_length_radio_option5_text");
    protected static final Message AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_length_required_error");


    // Embargo Info Table Column Headers
    protected static final Message AUETD_EMBARGO_SUMMARY_HEAD =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_summary_head");
    protected static final Message AUETD_T_COLUMN_STATUS =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_status_column");
    protected static final Message AUETD_EMBARGO_LENGTH_COLUMN = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_length_column");
    protected static final Message AUETD_SUBMIT_EDIT_RESTRICTIONS_BUTTON_NAME =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_submit_edit_restrictions_button_name");

    public static final String AUETD_ACCESS_SAVE_BUTTON_ID = "submit_access";
    protected static final Message AUETD_ACCESS_SAVE_BUTTON_NAME = 
        message("xmlui.Submission.submit.AccessStep.AUETD_access_restriction_button_name");


    protected ItemService itemService = ContentServiceFactory.getInstance().getItemService();


	/**
     * Establish our required parameters, abstractStep will enforce these.
     */
    public AccessStep(){
        this.requireSubmission = true;
        this.requireStep = true;
    }

    /**
     * Check if user has requested to edit information about an
     * uploaded file
     */
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters parameters)
            throws ProcessingException, SAXException, IOException
    {
        super.setup(resolver,objectModel,src,parameters);
        if(this.errorFlag==org.dspace.submit.step.AccessStep.STATUS_EDIT_POLICY
                || this.errorFlag==org.dspace.submit.step.AccessStep.EDIT_POLICY_STATUS_DUPLICATED_POLICY){
            this.editPolicy = new EditPolicyStep();
            this.editPolicy.setup(resolver, objectModel, src, parameters);
        }
    }


    public void addPageMeta(PageMeta pageMeta) throws WingException, SAXException, SQLException, AuthorizeException, IOException {
	    super.addPageMeta(pageMeta);
        // pageMeta.addMetadata("javascript", "static").addContent("static/js/accessFormUtil.js");
    }


    public void addBody(Body body) throws SAXException, WingException,
        UIException, SQLException, IOException, AuthorizeException{

        // If we are actually editing information of an uploaded file,
        // then display that body instead!
        if(this.editPolicy!=null){
            editPolicy.addBody(body);
            return;
        }

        // Get our parameters and state
        Item item = submission.getItem();
        Collection collection = submission.getCollection();

        Division div = addMainDivision(body, collection);

        List form = div.addList("submit-access-settings", List.TYPE_FORM);
        form.setHead(T_head);

        if (StringUtils.isBlank(getEmbargoRights(item)) || StringUtils.isBlank(getEmbargoStatus(item))) {
            addEmbargoFieldSection(form, item);

            form.addItem().addButton(org.dspace.submit.AbstractProcessingStep.PREVIOUS_BUTTON).setValue(T_previous);
            form.addItem().addButton(org.dspace.submit.AbstractProcessingStep.CANCEL_BUTTON).setValue(T_save);
            form.addItem().addButton(AUETD_ACCESS_SAVE_BUTTON_ID).setValue(AUETD_ACCESS_SAVE_BUTTON_NAME);
        } else {
            form.addLabel(AUETD_T_COLUMN_STATUS);
            form.addItem().addContent(setAccessStatusText(item));

            int embargoLengthNum = getEmbargoLengthInYears(item);
            if (embargoLengthNum > 0) {
                form.addLabel(AUETD_EMBARGO_LENGTH_COLUMN);
                form.addItem().addContent(setEmbargoLengthText(Integer.toString(embargoLengthNum)));
            }

            Button b1 = form.addItem().addButton("submit_edit_access");
            b1.setValue(AUETD_SUBMIT_EDIT_RESTRICTIONS_BUTTON_NAME);
        }

        // add standard control/paging buttons
        addControlButtons(form);
    }

    private void addPrivateCheckBox(Request request, List form, Item item) throws WingException {
        CheckBox privateCheckbox = form.addItem().addCheckBox("private_option");
        privateCheckbox.setLabel(T_private_settings);
        privateCheckbox.setHelp(T_private_settings_help);
        if(request.getParameter("private_option")!=null || !item.isDiscoverable())
            privateCheckbox.addOption(true, CHECKBOX_PRIVATE_ITEM, T_private_label);
        else
            privateCheckbox.addOption(false, CHECKBOX_PRIVATE_ITEM, T_private_label);
    }

    private Division addMainDivision(Body body, Collection collection) throws WingException {
        // DIVISION: Main
        String actionURL = contextPath + "/handle/"+collection.getHandle() + "/submit/" + knot.getId() + ".continue";
        Division div = body.addInteractiveDivision("submit-restrict",actionURL, Division.METHOD_POST,"primary submission");
        div.setHead(T_submission_head);
        addSubmissionProgressList(div);
        return div;
    }



    /**
     * Each submission step must define its own information to be reviewed
     * during the final Review/Verify Step in the submission process.
     * <P>
     * The information to review should be tacked onto the passed in
     * List object.
     * <P>
     * NOTE: To remain consistent across all Steps, you should first
     * add a sub-List object (with this step's name as the heading),
     * by using a call to reviewList.addList().   This sublist is
     * the list you return from this method!
     *
     * @param reviewList
     *      The List to which all reviewable information should be added
     * @return
     *      The new sub-List object created by this step, which contains
     *      all the reviewable information.  If this step has nothing to
     *      review, then return null!
     */
    public List addReviewSection(List reviewList) throws SAXException,
            WingException, UIException, SQLException, IOException,
            AuthorizeException
    {
        Item item = submission.getItem();
        
	    List accessSection = reviewList.addList("submit-review-" + this.stepAndPage, List.TYPE_FORM);
        accessSection.setHead(T_head);
        
        accessSection.addLabel(AUETD_T_COLUMN_STATUS);

        accessSection.addItem().addContent(setAccessStatusText(item));

        int embargoLengthNum = getEmbargoLengthInYears(item);
        if (embargoLengthNum > 0) {
            accessSection.addLabel(AUETD_EMBARGO_LENGTH_COLUMN);
            accessSection.addItem().addContent(setEmbargoLengthText(Integer.toString(embargoLengthNum)));
        }

	    return accessSection;
    }

    private String setEmbargoLengthText(String embargoLengthNumber)
    {
        Map<String, String> embargoLengthMap = new HashMap<>();
        embargoLengthMap.put("1", "One year");
        embargoLengthMap.put("2", "Two years");
        embargoLengthMap.put("3", "Three years");
        embargoLengthMap.put("4", "Four years");
        embargoLengthMap.put("5", "Five years");

        return embargoLengthMap.get(embargoLengthNumber);
    }

    private Message setAccessStatusText(Item item) {
        Message statusTxt = null;

        if (submissionInfo.containsKey(AUETD_CREATE_EMBARGO_QUESTION_FIELD_NAME) &&
                StringUtils.isNotBlank(submissionInfo.get(AUETD_CREATE_EMBARGO_QUESTION_FIELD_NAME).toString())) {
            statusTxt = setAccessStatusTextFromSubmissionKey();
        } else {
            statusTxt = setAccessStatusTextFromMetadata(item);
        }

        return statusTxt;
    }

    private Message setAccessStatusTextFromSubmissionKey() {
        Message statusTxt = null;
        int embargoCreationAnswer = 0;
        embargoCreationAnswer = Integer.parseInt(submissionInfo.get(AUETD_CREATE_EMBARGO_QUESTION_FIELD_NAME).toString());

        if (embargoCreationAnswer == 1) {
            statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON1;
        } else if (embargoCreationAnswer == 2) {
            statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON2;
        } else if (embargoCreationAnswer == 3) {
            statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON3;
        } else {
            statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON1;
        }

        return statusTxt;
    }

    private Message setAccessStatusTextFromMetadata(Item item) {
        Message statusTxt = null;
        String embargoRights = null;
        String embargoStatus = null;
        java.util.List<MetadataValue> embargoRightsList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "rights", null, org.dspace.content.Item.ANY);
        java.util.List<MetadataValue> embargoStatusList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "status", org.dspace.content.Item.ANY);

        if (embargoRightsList != null && !embargoRightsList.isEmpty()) {
            embargoRights = embargoRightsList.get(0).getValue();
        }

        if (embargoStatusList != null && !embargoStatusList.isEmpty()) {
            embargoStatus = embargoStatusList.get(0).getValue();
        }

        if(StringUtils.isNotBlank(embargoStatus) && StringUtils.isNotBlank(embargoRights)) {
            if(embargoStatus.equals(AUETDConstants.EMBARGOED)) {
                if(embargoRights.equals(AUETDConstants.EMBARGO_NOT_AUBURN_STR)) {
                    statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON2;

                } else if(embargoRights.equals(AUETDConstants.EMBARGO_GLOBAL_STR)) {
                    statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON3;
                }
            } else {
                statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON1;
            }
        } else if(StringUtils.isNotBlank(embargoStatus) && StringUtils.isBlank(embargoRights)) {
            statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON1;
        }

        return statusTxt;
    }

    /**
     * Builds the form fields of ETD's custom embargo definition section
     * @param dso
     *      current DSpace object
     * @param form
     *      current list of form information
     * @param errorFlag
     *      error flag
     *
     * @throws java.sql.SQLException
     * @throws org.dspace.app.xmlui.wing.WingException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     *
     */
    private void addEmbargoFieldSection(List form, Item item)
        throws SQLException, WingException, IOException,
        AuthorizeException
    {
        // The value of this hidden input field is used
        // by a JavaScript method in the web UI.
        Hidden embargoLengthFieldDisplayInput = form.addItem().addHidden("embargoLengthFieldDisplay");
        embargoLengthFieldDisplayInput.setValue(0);

        //Embargo Question Radio Button Group
        Radio embargoTypeField = form.addItem().addRadio(AUETD_CREATE_EMBARGO_QUESTION_FIELD_NAME);
        addEmbargoTypeRadioFields(embargoTypeField);

        // Embargo Length Radio Button Group
        Radio embargoLengthField = form.addItem().addRadio(AUETD_EMBARGO_LENGTH_FIELD_NAME);
        addEmbargoLengthRadioFields(embargoLengthField);

        /**
         * Populate input field values and/or add error messages
         */
        if(submissionInfo.containsKey(AUETD_CREATE_EMBARGO_QUESTION_FIELD_NAME_ERROR)) {
            embargoTypeField.addError(AUETD_STATUS_ERROR_EMBARGO_CREATION_REQUIRED);
        }

        if(submissionInfo.containsKey(AUETD_CREATE_EMBARGO_QUESTION_FIELD_NAME)) {
            embargoTypeField.setOptionSelected(submissionInfo.get(AUETD_CREATE_EMBARGO_QUESTION_FIELD_NAME).toString());
        } else if (getSelectedEmbargoType(item) > 0) {
            embargoTypeField.setOptionSelected(Integer.toString(getSelectedEmbargoType(item)));
        }

        if(submissionInfo.containsKey(AUETD_EMBARGO_LENGTH_FIELD_NAME_ERROR)) {
            embargoLengthField.addError(AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED);
            embargoLengthFieldDisplayInput.setValue(1);
        }

        if(submissionInfo.containsKey(AUETD_EMBARGO_LENGTH_FIELD_NAME)) {
            embargoLengthField.setOptionSelected(submissionInfo.get(AUETD_EMBARGO_LENGTH_FIELD_NAME).toString());
            embargoLengthFieldDisplayInput.setValue(1);
        } else if (getEmbargoLengthInYears(item) > 0) {
            embargoLengthField.setOptionSelected(Integer.toString(getEmbargoLengthInYears(item)));
            embargoLengthFieldDisplayInput.setValue(1);
        }
    }

    private void addEmbargoTypeRadioFields(Radio embargoTypeField)
        throws WingException
    {
        embargoTypeField.setLabel(AUETD_CREATE_EMBARGO_QUESTION_LABEL);
        embargoTypeField.setRequired();
        embargoTypeField.addOption("1", AUETD_CREATE_EMBARGO_RADIO_BUTTON1);
        embargoTypeField.addOption("2", AUETD_CREATE_EMBARGO_RADIO_BUTTON2);
        embargoTypeField.addOption("3", AUETD_CREATE_EMBARGO_RADIO_BUTTON3);
    }

    private void addEmbargoLengthRadioFields(Radio embargoLengthField)
        throws WingException
    {
        embargoLengthField.setLabel(AUETD_EMBARGO_LENGTH_LABEL);
        embargoLengthField.setHelp(AUETD_EMBARGO_LENGTH_HELP);
        embargoLengthField.addOption("1", AUETD_EMBARGO_LENGTH_RADIO_OPTION1);
        embargoLengthField.addOption("2", AUETD_EMBARGO_LENGTH_RADIO_OPTION2);
        embargoLengthField.addOption("3", AUETD_EMBARGO_LENGTH_RADIO_OPTION3);
        embargoLengthField.addOption("4", AUETD_EMBARGO_LENGTH_RADIO_OPTION4);
        embargoLengthField.addOption("5", AUETD_EMBARGO_LENGTH_RADIO_OPTION5);
    }

    private int getSelectedEmbargoType(Item item) {
        int embargoType = 0;
        String embargoRights = getEmbargoRights(item);
        String embargoStatus = getEmbargoStatus(item);

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

    private String getEmbargoRights(Item item) {
        String embargoRights = null;

        java.util.List<MetadataValue> embargoRightsList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "rights", null, Item.ANY);

        if (embargoRightsList != null && !embargoRightsList.isEmpty()) {
            embargoRights = embargoRightsList.get(0).getValue();
        }

        return embargoRights;
    }

    private String getEmbargoStatus(Item item) {
        String embargoStatus = null;

        java.util.List<MetadataValue> embargoStatusList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "status", Item.ANY);

        if (embargoStatusList != null && !embargoStatusList.isEmpty()) {
            embargoStatus = embargoStatusList.get(0).getValue();
        }

        return embargoStatus;
    }

    private int getEmbargoLengthInYears(Item item) {
        int embargoLength = 0;

        if (item != null) {
            java.util.List<MetadataValue> embargoLengthList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "length", org.dspace.content.Item.ANY);
            if (!embargoLengthList.isEmpty()) {
                ArrayList<String> embargoLengths = new ArrayList<>();
                embargoLengths.addAll(Arrays.asList(embargoLengthList.get(0).getValue().split(":")));
                int lengthNum = Integer.parseInt(embargoLengths.get(1));
                if (lengthNum > 0) {
                    embargoLength = lengthNum / 12;
                }
            }
        }

        return embargoLength;
    }
}
