/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.xmlui.aspect.submission.submit;

// Java class imports
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.log4j.Logger;
import org.dspace.app.xmlui.aspect.submission.AbstractStep;
import org.dspace.app.xmlui.utils.UIException;
import static org.dspace.app.xmlui.wing.AbstractWingTransformer.message;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.Hidden;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.app.xmlui.wing.element.Radio;
import org.dspace.app.xmlui.wing.element.Text;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Collection;
import org.dspace.content.FormatIdentifier;
import org.dspace.core.LogManager;
import org.dspace.embargo.ETDEmbargoSetter;
import org.dspace.embargo.EmbargoManager;
import static org.dspace.submit.step.AccessStep.STATUS_ERROR_MISSING_DATE;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
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

    // The file we're editing
    private Bitstream bitstream;

     // Custom Constants Section
    protected static final Message ETD_INVALID_FILE_FORMAT_ERROR =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_invalid_file_format_error");
    protected static final Message ETD_FILE_UPLOAD_FORM_HEAD =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_head");
    protected static final Message ETD_FILE_UPLOAD_HELP =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_file_help");
    protected static final Message ETD_SUBMIT_UPLOAD_BUTTON =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_submit_upload");

    protected static final Message T_previous =
        message("xmlui.Submission.general.submission.previous");
    protected static final Message T_save =
        message("xmlui.Submission.general.submission.save");

    // Initial question
    protected static final Message ETD_CREATE_QUESTION_LABEL =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_create_embargo_label");
    protected static final Message EMBARGO_RADIO_BUTTON1 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_radio_button1_text");
    protected static final Message EMBARGO_RADIO_BUTTON2 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_radio_button2_text");
    protected static final Message EMBARGO_RADIO_BUTTON3 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_radio_button3_text");
    protected static final Message STATUS_ERROR_EMBARGO_CREATION_REQUIRED_ERROR =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_create_required_error");

    // Date messages
    protected static final Message ETD_DATE_LABEL =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_date_label");
    protected static final Message ETD_DATE_HELP =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_date_help");
    protected static final Message ETD_DATE_REQUIRED_ERROR =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_date_required_error");
    protected static final Message ETD_DATE_FORMAT_ERROR =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_date_format_error");
    protected static final Message ETD_DATE_IN_PAST_ERROR =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_date_in_past_error");
    protected static final Message ETD_DATE_IS_CURRENT_ERROR =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_date_is_current_error");

    // Embargo Info Table Column Headers
    protected static final Message T_COLUMN_STATUS =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_status_column");
    protected static final Message T_COLUMN_ENDDATE =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_enddate_column");

    // Field names
    protected static final String ETD_CREATE_QUESTION_FIELD_NAME = "create_embargo_radio";
    protected static final String ETD_DATE_FIELD_NAME = "embargo_until_date";

    // Form processing status error codes
    public static final int STATUS_ERROR_DATE_IN_PAST = 35;
    public static final int STATUS_ERROR_EMBARGO_CREATION_REQUIRED = 36;
    public static final int STATUS_ERROR_DATE_IS_CURRENT = 37;

    /** log4j logger */
    private static final Logger log = Logger.getLogger(EditFileStep.class);

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
     * @param resolver
     * @param objectModel
     * @param src
     * @param parameters
     * @throws org.apache.cocoon.ProcessingException
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    @Override
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters parameters)
        throws ProcessingException, SAXException, IOException
    {
        super.setup(resolver,objectModel,src,parameters);

        //the bitstream should be stored in our Submission Info object
        this.bitstream = submissionInfo.getBitstream();
    }

    /**
     *
     * @param body
     * @throws org.xml.sax.SAXException
     * @throws org.dspace.app.xmlui.wing.WingException
     * @throws org.dspace.app.xmlui.utils.UIException
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     */
    public void addBody(Body body)
        throws SAXException, WingException, UIException,
        SQLException, IOException, AuthorizeException
    {
        Collection collection = submission.getCollection();
        String actionURL = contextPath + "/handle/"+collection.getHandle() + "/submit/" + knot.getId() + ".continue";

    	// Get the bitstream and all the various formats
        BitstreamFormat currentFormat = bitstream.getFormat();
        BitstreamFormat guessedFormat = FormatIdentifier.guessFormat(context, bitstream);

        int itemID = submissionInfo.getSubmissionItem().getItem().getID();
    	String fileUrl = contextPath + "/bitstream/item/" + itemID + "/" + bitstream.getName();
    	String fileName = bitstream.getName();

    	// Build the form that describes an item.
    	Division div = body.addInteractiveDivision("submit-edit-file", actionURL, Division.METHOD_POST, "primary submission");
    	div.setHead(T_submission_head);
    	addSubmissionProgressList(div);

        // Create the list of form fields directly related to
        // the bitstream's information
    	List edit = div.addList("submit-edit-file", List.TYPE_FORM);
        edit.setHead(T_head);
        edit.addLabel(T_file);
        edit.addItem().addXref(fileUrl, fileName);

        if (guessedFormat != null)
        {
            edit.addLabel(T_format_detected);
            edit.addItem(guessedFormat.getShortDescription());
        }

        //Add the embargo editing field section
        addEmbargoFieldSection(edit);

        // add ID of bitstream we're editing
        div.addHidden("bitstream_id").setValue(bitstream.getID());

        // Note, not standard control actions, this page just goes back to the upload step.
        org.dspace.app.xmlui.wing.element.Item actions = edit.addItem();
        actions.addButton("submit_save").setValue(T_submit_save);
        actions.addButton("submit_edit_cancel").setValue(T_submit_cancel);
    }

    /**
     *
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
     */
    private void addEmbargoFieldSection(List form)
        throws SQLException, WingException, IOException, AuthorizeException
    {
        DateTime rpEndDate = null;
        String embargoStatus = null;
        String endDateMDV = null;
        DateTimeFormatter dft = DateTimeFormat.forPattern("yyyy-MM-dd");

        Hidden datefieldDisplayInput = form.addItem().addHidden("datefieldDisplay");
        datefieldDisplayInput.setValue(0);

        //Embargo Question radios
        Radio embargoTypeRadio = form.addItem().addRadio(ETD_CREATE_QUESTION_FIELD_NAME);
        embargoTypeRadio.setLabel(ETD_CREATE_QUESTION_LABEL);
        embargoTypeRadio.setRequired();
        embargoTypeRadio.addOption("1", EMBARGO_RADIO_BUTTON1);
        embargoTypeRadio.addOption("2", EMBARGO_RADIO_BUTTON2);
        embargoTypeRadio.addOption("3", EMBARGO_RADIO_BUTTON3);

         // Build date field
        Text embargoDateField = form.addItem().addText(ETD_DATE_FIELD_NAME);
        embargoDateField.setLabel(ETD_DATE_LABEL);
        embargoDateField.setHelp(ETD_DATE_HELP);

        if(submissionInfo != null)
        {
            if(submissionInfo.containsKey(ETD_CREATE_QUESTION_FIELD_NAME))
            {
                embargoTypeRadio.setOptionSelected(submissionInfo.get(ETD_CREATE_QUESTION_FIELD_NAME).toString());
            }

            if(submissionInfo.containsKey(ETD_DATE_FIELD_NAME+"_ERROR"))
            {
                int dateErrFlag = Integer.parseInt(submissionInfo.get(ETD_DATE_FIELD_NAME).toString());

                if(dateErrFlag == STATUS_ERROR_MISSING_DATE)
                {
                    embargoDateField.addError(ETD_DATE_REQUIRED_ERROR);
                    datefieldDisplayInput.setValue(1);
                }

                if(dateErrFlag == STATUS_ERROR_DATE_IN_PAST)
                {
                    embargoDateField.addError(ETD_DATE_IN_PAST_ERROR);
                    datefieldDisplayInput.setValue(1);
                }

                if(dateErrFlag == STATUS_ERROR_DATE_IS_CURRENT)
                {
                    embargoDateField.addError(ETD_DATE_IS_CURRENT_ERROR);
                    datefieldDisplayInput.setValue(1);
                }
            }

            if(submissionInfo.containsKey(ETD_DATE_FIELD_NAME))
            {
                embargoDateField.setValue(submissionInfo.get(ETD_DATE_FIELD_NAME).toString());
            }
        }
    }
}
