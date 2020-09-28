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
import org.apache.cocoon.environment.SourceResolver;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.xmlui.aspect.submission.AbstractStep;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.*;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.AUETDConstants;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;


public class EditPolicyStep extends AbstractStep
{

	/** Language Strings **/
    protected static final Message T_head =message("xmlui.Submission.submit.EditPolicyStep.head");
    protected static final Message T_submit_save = message("xmlui.general.save");
    protected static final Message T_submit_cancel =message("xmlui.general.cancel");

	private ResourcePolicy resourcePolicy;
    private Bitstream bitstream;


    // Custom Constants Section

    protected static final Message AUETD_EDIT_ACCESS_HEAD = 
        message("xmlui.Submission.submit.EditPolicyStep.AUETD_head");
    
    // Embargo Type field messages
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

    // Embargo Length field messages
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

    protected ItemService itemService = ContentServiceFactory.getInstance().getItemService();


	/**
	 * Establish our required parameters, abstractStep will enforce these.
	 */
	public EditPolicyStep()
	{
		this.requireSubmission = true;
		this.requireStep = true;
	}
	
	
	/**
	 * Get the bitstream we are editing
	 */
	public void setup(SourceResolver resolver, Map objectModel, String src, Parameters parameters) 
	throws ProcessingException, SAXException, IOException
	{ 
		super.setup(resolver,objectModel,src,parameters);
		/* this.resourcePolicy = (ResourcePolicy) submissionInfo.get(org.dspace.submit.step.AccessStep.SUB_INFO_SELECTED_RP);
        this.bitstream=submissionInfo.getBitstream(); */
	}

  
    public void addBody(Body body) throws SAXException, WingException,
            UIException, SQLException, IOException, AuthorizeException{

        Collection collection = submission.getCollection();
        Item item = submission.getItem();
		String actionURL = contextPath + "/handle/"+collection.getHandle() + "/submit/" + knot.getId() + ".continue";
        Division div = body.addInteractiveDivision("submit-edit-policy", actionURL, Division.METHOD_POST, "primary submission");
        div.setHead(T_submission_head);
        addSubmissionProgressList(div);
        List edit = div.addList("submit-edit-file", List.TYPE_FORM);
        edit.setHead(AUETD_EDIT_ACCESS_HEAD);

        addEmbargoFieldSection(edit, item);

        // Note, not standard control actions, this page just goes back to the upload step.
        org.dspace.app.xmlui.wing.element.Item actions = edit.addItem();
        actions.addButton(org.dspace.submit.step.AccessStep.FORM_EDIT_BUTTON_SAVE).setValue(T_submit_save);
		actions.addButton(org.dspace.submit.step.AccessStep.FORM_EDIT_BUTTON_CANCEL).setValue(T_submit_cancel);
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
        Radio embargoTypeField = form.addItem().addRadio(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME);
        addEmbargoTypeRadioFields(embargoTypeField);

        // Embargo Length Radio Button Group
        Radio embargoLengthField = form.addItem().addRadio(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME);
        addEmbargoLengthRadioFields(embargoLengthField);

        /**
         * Populate input field values and/or add error messages
         */
        if(submissionInfo.containsKey(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME_ERROR)) {
            embargoTypeField.addError(AUETD_STATUS_ERROR_EMBARGO_CREATION_REQUIRED);
        }

        if(submissionInfo.containsKey(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME)) {
            embargoTypeField.setOptionSelected(submissionInfo.get(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME).toString());
        } else if (getSelectedEmbargoType(item) > 0) {
            embargoTypeField.setOptionSelected(Integer.toString(getSelectedEmbargoType(item)));
        }

        if(submissionInfo.containsKey(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME_ERROR)) {
            embargoLengthField.addError(AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED);
            embargoLengthFieldDisplayInput.setValue(1);
        }

        if(submissionInfo.containsKey(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME)) {
            embargoLengthField.setOptionSelected(submissionInfo.get(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME).toString());
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
