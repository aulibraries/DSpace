/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.submission.submit;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.aspect.submission.AbstractStep;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.Hidden;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.app.xmlui.wing.element.Radio;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamFormatService;
import org.xml.sax.SAXException;

/**
 * This is a sub step of the Upload step during item submission. This 
 * page allows the user to edit metadata about a bitstream (aka file) 
 * that has been uploaded. The user can change the format or change 
 * the file's description.
 * <P>
 * Since this page is a sub step, the normal control actions are not
 * present, the user only has the option of returning back to the 
 * upload step.
 * <P>
 * NOTE: As a sub step, it is called directly from the UploadStep class.
 * 
 * @author Scott Phillips
 * @author Tim Donohue (updated for Configurable Submission)
 */
public class EditFileStep extends AbstractStep
{

	/** Language Strings **/
    protected static final Message T_head = 
        message("xmlui.Submission.submit.EditFileStep.head");
    protected static final Message T_file = 
        message("xmlui.Submission.submit.EditFileStep.file");
    protected static final Message T_description = 
        message("xmlui.Submission.submit.EditFileStep.description");
    protected static final Message T_description_help = 
        message("xmlui.Submission.submit.EditFileStep.description_help");
    protected static final Message T_info1 = 
        message("xmlui.Submission.submit.EditFileStep.info1");
    protected static final Message T_format_detected = 
        message("xmlui.Submission.submit.EditFileStep.format_detected");
    protected static final Message T_format_selected = 
        message("xmlui.Submission.submit.EditFileStep.format_selected");
    protected static final Message T_format_default = 
        message("xmlui.Submission.submit.EditFileStep.format_default");
    protected static final Message T_info2 = 
        message("xmlui.Submission.submit.EditFileStep.info2");
    protected static final Message T_format_user = 
        message("xmlui.Submission.submit.EditFileStep.format_user");
    protected static final Message T_format_user_help = 
        message("xmlui.Submission.submit.EditFileStep.format_user_help");
    protected static final Message T_submit_save = 
        message("xmlui.general.save");
    protected static final Message T_submit_cancel = 
        message("xmlui.general.cancel");

     // Custom Constants Section
    protected static final Message AUETD_INVALID_FILE_FORMAT_ERROR =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_invalid_file_format_error");
    protected static final Message AUETD_FILE_UPLOAD_FORM_HEAD =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_head");
    protected static final Message AUETD_FILE_UPLOAD_HELP =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_file_help");
    protected static final Message AUETD_SUBMIT_UPLOAD_BUTTON =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_submit_upload");

    protected static final Message T_previous =
        message("xmlui.Submission.general.submission.previous");
    protected static final Message T_save =
        message("xmlui.Submission.general.submission.save");

    // Initial question
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
    protected static final Message AUETD_T_COLUMN_STATUS =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_status_column");
    protected static final Message AUETD_T_COLUMN_ENDDATE =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_enddate_column");

    /** The bitstream we are editing */
	private Bitstream bitstream;

    protected BitstreamFormatService bitstreamFormatService = ContentServiceFactory.getInstance().getBitstreamFormatService();

	/**
	 * Establish our required parameters, abstractStep will enforce these.
	 */
	public EditFileStep()
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

		//the bitstream should be stored in our Submission Info object
        this.bitstream = submissionInfo.getBitstream();
	}


    public void addBody(Body body) throws SAXException, WingException,
            UIException, SQLException, IOException, AuthorizeException
    {
		Collection collection = submission.getCollection();
		String actionURL = contextPath + "/handle/"+collection.getHandle() + "/submit/" + knot.getId() + ".continue";

        UUID itemID = submissionInfo.getSubmissionItem().getItem().getID();
    	String fileUrl = contextPath + "/bitstream/item/" + itemID + "/" + bitstream.getName();
    	String fileName = bitstream.getName();

    	// Build the form that describes an item.
    	Division div = body.addInteractiveDivision("submit-edit-file", actionURL, Division.METHOD_POST, "primary submission");
    	div.setHead(T_submission_head);
    	addSubmissionProgressList(div);

    	List edit = div.addList("submit-edit-file", List.TYPE_FORM);
        edit.setHead(T_head);

        edit.addLabel(T_file);
        edit.addItem().addXref(fileUrl, fileName);

        //Add the embargo editing field section
        addEmbargoFieldSection(edit);

        // add ID of bitstream we're editing
        div.addHidden("bitstream_id").setValue(bitstream.getID().toString());

        // Note, not standard control actions, this page just goes back to the upload step.
        org.dspace.app.xmlui.wing.element.Item actions = edit.addItem();
        actions.addButton("submit_save").setValue(T_submit_save);
		actions.addButton("submit_edit_cancel").setValue(T_submit_cancel);
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
    private void addEmbargoFieldSection(List form)
        throws SQLException, WingException, IOException,
        AuthorizeException
    {
        // The value of this hidden input field is used
        // by a JavaScript method in the web UI.
        Hidden embargoLengthFieldDisplayInput = form.addItem().addHidden("embargoLengthFieldDisplay");
        embargoLengthFieldDisplayInput.setValue(0);

        //Embargo Question Radio Button Group
        Radio embargoTypeRadio = form.addItem().addRadio(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME);
        embargoTypeRadio.setLabel(AUETD_CREATE_EMBARGO_QUESTION_LABEL);
        embargoTypeRadio.setRequired();
        embargoTypeRadio.addOption("1", AUETD_CREATE_EMBARGO_RADIO_BUTTON1);
        embargoTypeRadio.addOption("2", AUETD_CREATE_EMBARGO_RADIO_BUTTON2);
        embargoTypeRadio.addOption("3", AUETD_CREATE_EMBARGO_RADIO_BUTTON3);

        // Embargo Length Radio Button Group
        Radio embargoLengthField = form.addItem().addRadio(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME);
        embargoLengthField.setLabel(AUETD_EMBARGO_LENGTH_LABEL);
        embargoLengthField.setHelp(AUETD_EMBARGO_LENGTH_HELP);
        embargoLengthField.addOption("1", AUETD_EMBARGO_LENGTH_RADIO_OPTION1);
        embargoLengthField.addOption("2", AUETD_EMBARGO_LENGTH_RADIO_OPTION2);
        embargoLengthField.addOption("3", AUETD_EMBARGO_LENGTH_RADIO_OPTION3);
        embargoLengthField.addOption("4", AUETD_EMBARGO_LENGTH_RADIO_OPTION4);
        embargoLengthField.addOption("5", AUETD_EMBARGO_LENGTH_RADIO_OPTION5);

        /**
         * Populate input field values and/or add error messages
         */
        if(submissionInfo.containsKey(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME_ERROR)) {
            embargoTypeRadio.addError(AUETD_STATUS_ERROR_EMBARGO_CREATION_REQUIRED);
        }

        if(submissionInfo.containsKey(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME)) {
            embargoTypeRadio.setOptionSelected(submissionInfo.get(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME).toString());
        }

        if(submissionInfo.containsKey(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME_ERROR)) {
            embargoLengthField.addError(AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED);
            embargoLengthFieldDisplayInput.setValue(1);
        }

        if(submissionInfo.containsKey(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME)) {
            embargoLengthField.setOptionSelected(submissionInfo.get(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME).toString());
            embargoLengthFieldDisplayInput.setValue(1);
        }
    }
}
