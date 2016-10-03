/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.xmlui.aspect.submission.submit;

// Java class import
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.log4j.Logger;

import org.dspace.app.util.Util;
import static org.dspace.app.xmlui.aspect.submission.submit.UploadWithEmbargoStep.ETD_DATE_FIELD_NAME;
import org.dspace.app.xmlui.utils.UIException;
import static org.dspace.app.xmlui.wing.AbstractWingTransformer.message;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Button;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.File;
import org.dspace.app.xmlui.wing.element.Hidden;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.app.xmlui.wing.element.PageMeta;
import org.dspace.app.xmlui.wing.element.Para;
import org.dspace.app.xmlui.wing.element.Radio;
import org.dspace.app.xmlui.wing.element.Text;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.LogManager;
import org.dspace.embargo.ETDEmbargoSetter;
import org.dspace.embargo.EmbargoManager;
import org.dspace.submit.AbstractProcessingStep;
import static org.dspace.submit.step.AccessStep.STATUS_ERROR_MISSING_DATE;
import static org.dspace.submit.step.UploadStep.SUBMIT_UPLOAD_BUTTON;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import org.xml.sax.SAXException;



/**
 * This class manages the upload step with embargo fields during the submission.
 * Edit submission.xml to enable it.
 *
 * @author Fabio Bolognesi (fabio at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 *
 */
public class UploadWithEmbargoStep extends UploadStep
{
	/** Language Strings for Uploading **/

    // Main Head/Title message
    protected static final Message T_head =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.head");

    // Messages for the file input field
    protected static final Message T_file =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.file");
    protected static final Message T_file_help =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.file_help");
    protected static final Message T_file_error =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.file_error");
    protected static final Message T_upload_error =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.upload_error");

    // Message constants for virus checking
    protected static final Message T_virus_checker_error =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.virus_checker_error");
    protected static final Message T_virus_error =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.virus_error");

    // Message constants for the description input field
    protected static final Message T_description =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.description");
    protected static final Message T_description_help =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.description_help");

    // Table column names
    protected static final Message T_head2 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.head2");
    protected static final Message T_column0 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.column0");
    protected static final Message T_column1 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.column1");
    protected static final Message T_column2 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.column2");
    protected static final Message T_column3 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.column3");
    protected static final Message T_column4 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.column4");
    protected static final Message T_column5 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.column5");
    protected static final Message T_column6 =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.column6");

    //Message constants related to a file's description information
    protected static final Message T_unknown_name =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.unknown_name");
    protected static final Message T_unknown_format =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.unknown_format");
    protected static final Message T_supported =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.supported");
    protected static final Message T_known =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.known");
    protected static final Message T_unsupported =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.unsupported");
    protected static final Message T_checksum =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.checksum");

    /*protected static final Message T_submit_policy =
            message("xmlui.Submission.submit.UploadWithEmbargoStep.submit_policy");*/

    // Message constants for buttons
    protected static final Message T_submit_upload =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.submit_upload");
    protected static final Message T_submit_edit =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.submit_edit");
    protected static final Message T_submit_remove =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.submit_remove");

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
    protected static final Message ETD_EMBARGO_SUMMARY_HEAD =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_embargo_summary_head");
    protected static final Message T_COLUMN_STATUS =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_status_column");
    protected static final Message T_COLUMN_ENDDATE =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_enddate_column");

    // Field names
    protected static final String ETD_CREATE_QUESTION_FIELD_NAME = "create_embargo_radio";
    protected static final String ETD_DATE_FIELD_NAME = "embargo_until_date";

    protected static final Message ETD_SUBMIT_REMOVE_BUTTON_NAME =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_submit_remove_button_name");
    protected static final Message ETD_SUBMIT_EDIT_RESTRICTIONS_BUTTON_NAME =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.ETD_submit_edit_restrictions_button_name");

    // Form processing status error codes
    public static final int STATUS_ERROR_DATE_IN_PAST = 35;
    public static final int STATUS_ERROR_EMBARGO_CREATION_REQUIRED = 36;
    public static final int STATUS_ERROR_DATE_IS_CURRENT = 37;


    // End Custom Constants Section

    /** log4j logger */
    private static final Logger log = Logger.getLogger(UploadWithEmbargoStep.class);

    /**
     * Global reference to edit file page
     * (this is used when a user requests to edit a bitstream)
     **/
    private EditFileStep editFile = null;

    //private EditBitstreamPolicies editBitstreamPolicies = null;

    //private EditPolicyStep editPolicy = null;

    //private boolean isAdvancedFormEnabled=true;

    private ResourcePolicy rp;

    /**
     * Establish our required parameters, abstractStep will enforce these.
     */
    public UploadWithEmbargoStep()
    {
        this.requireSubmission = true;
        this.requireStep = true;
    }


    /**
     * Check if user has requested to edit information about an
     * uploaded file
     *
     * @param resolver
     *          Source resolver object
     * @param objectModel
     *
     * @param src
     *
     * @param parameters
     *          Additional parameters to apply
     *
     * @throws org.apache.cocoon.ProcessingException
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    @Override
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters parameters)
        throws ProcessingException, SAXException, IOException
    {
        /*super.setup(resolver,objectModel,src,parameters);

        // If this page for editing an uploaded file's information
        // was requested, then we need to load EditFileStep instead!
        if(this.errorFlag==org.dspace.submit.step.UploadWithEmbargoStep.STATUS_EDIT_BITSTREAM)
        {
            this.editFile = new EditFileStep();
            this.editFile.setup(resolver, objectModel, src, parameters);
        }
        else if(this.errorFlag==org.dspace.submit.step.UploadWithEmbargoStep.STATUS_EDIT_POLICIES
                || this.errorFlag==org.dspace.submit.step.UploadWithEmbargoStep.STATUS_EDIT_POLICIES_ERROR_SELECT_GROUP
                  || this.errorFlag==org.dspace.submit.step.UploadWithEmbargoStep.STATUS_EDIT_POLICIES_DUPLICATED_POLICY){
            this.editBitstreamPolicies = new EditBitstreamPolicies();
            this.editBitstreamPolicies.setup(resolver, objectModel, src, parameters);
        }
        else if(this.errorFlag==org.dspace.submit.step.AccessStep.STATUS_EDIT_POLICY
                || this.errorFlag==org.dspace.submit.step.UploadWithEmbargoStep.STATUS_EDIT_POLICY_ERROR_SELECT_GROUP
                 || this.errorFlag==org.dspace.submit.step.UploadWithEmbargoStep.STATUS_EDIT_POLICY_DUPLICATED_POLICY){
            this.editPolicy = new EditPolicyStep();
            this.editPolicy.setup(resolver, objectModel, src, parameters);
        }
        else
        {
            this.editFile = null;
            editBitstreamPolicies = null;
            editPolicy=null;
        }

        isAdvancedFormEnabled=ConfigurationManager.getBooleanProperty("webui.submission.restrictstep.enableAdvancedForm", false);*/

        super.setup(resolver,objectModel,src,parameters);

        //log.debug(LogManager.getHeader(context, "Submission Response", " Flag ID = "+String.valueOf(this.errorFlag)));

        // If this page is for editing an uploaded file's information,
        // then we need to load EditFileStep instead!
        if(this.errorFlag == org.dspace.submit.step.UploadWithEmbargoStep.STATUS_EDIT_BITSTREAM)
        {
            this.editFile = new EditFileStep();
            this.editFile.setup(resolver, objectModel, src, parameters);
        }
        else
        {
            this.editFile = null;
        }

        this.rp = (ResourcePolicy) submissionInfo.get(org.dspace.submit.step.AccessStep.SUB_INFO_SELECTED_RP);
    }

    /**
     *
     * Inserts additional information into a page's
     * metadata block
     *
     * @param pageMeta
     *          page meta object
     *
     * @throws org.dspace.app.xmlui.wing.WingException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     * @throws java.sql.SQLException
     */
    public void addPageMeta(PageMeta pageMeta)
        throws WingException, AuthorizeException, IOException, SAXException, SQLException
    {
        super.addPageMeta(pageMeta);
    }

    /**
     *
     * Builds the various pieces of a page's body and inserts
     * them into the page.
     *
     * @param body
     *          Body object
     *
     * @throws org.xml.sax.SAXException
     * @throws org.dspace.app.xmlui.wing.WingException
     * @throws org.dspace.app.xmlui.utils.UIException
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     */
    @Override
    public void addBody(Body body)
        throws SAXException, WingException,
        UIException, SQLException, IOException,
        AuthorizeException
    {
        // If we are actually editing information of an uploaded file,
        // then display that body instead!
    	if(this.editFile!=null)
        {
            this.editFile.addBody(body);
            return;
        }

        Request request = ObjectModelHelper.getRequest(objectModel);

        // Get a list of all files in the original bundle
        Item item = submission.getItem();
        Collection collection = submission.getCollection();
        String actionURL = contextPath + "/handle/"+collection.getHandle() + "/submit/" + knot.getId() + ".continue";
        boolean disableFileEditing = (submissionInfo.isInWorkflow()) && !ConfigurationManager.getBooleanProperty("workflow", "reviewer.file-edit");
        Bundle[] bundles = item.getBundles("ORIGINAL");
        Bitstream[] bitstreams = new Bitstream[0];
        String embargoStatus = null;
        Division uploadSummaryMainDiv = null;
        List upload = null;

        embargoStatus = EmbargoManager.getEmbargoStatusMDV(context, submission.getItem());

        //log.debug(LogManager.getHeader(context, "Viewing Bundles Array Size", " bundles.length = "+String.valueOf(bundles.length)));
        if (bundles.length > 0)
        {
            bitstreams = bundles[0].getBitstreams();
        }

        //log.debug(LogManager.getHeader(context, "Viewing Bitstream Array Size", " bitstreams.length = "+String.valueOf(bundles.length)));

        int errorSize = 0;
        // if there were errors then stop execution here
        @SuppressWarnings("unchecked")
        java.util.List<String> subInfoKeyList = new ArrayList<String>(submissionInfo.keySet());
        for(String key : subInfoKeyList)
        {
            if(key.contains("ERROR"))
            {
                errorSize++;
            }
        }

        // Part A:
        //  First ask the user if they would like to upload a new file (may be the first one)
    	Division div = body.addInteractiveDivision("submit-upload", actionURL, Division.METHOD_MULTIPART, "primary submission");
    	div.setHead(T_submission_head);
    	addSubmissionProgressList(div);

        // if no bitstreams have been uploaded already and
        // file editing is allowed then render the file form
        // field otherwise print a summary of the uploaded
        // file's information
        if(bitstreams.length <= 0 && !disableFileEditing && errorSize >= 0)
        {
            // Only add the upload capabilities for new item submissions
            upload = div.addList("submit-upload-new", List.TYPE_FORM);
            upload.setHead(ETD_FILE_UPLOAD_FORM_HEAD);

            File file = upload.addItem().addFile("file");
            file.setLabel(T_file);
            file.setHelp(ETD_FILE_UPLOAD_HELP);
            file.setRequired();

            // if no files found error was thrown by processing class, display it!
            if(submissionInfo.containsKey("file_ERROR"))
            {
                int errFlag = Integer.parseInt(submissionInfo.get("file_ERROR").toString());

                if (errFlag == org.dspace.submit.step.UploadWithEmbargoStep.STATUS_NO_FILES_ERROR)
                {
                    file.addError(T_file_error);
                }

                // if an upload error was thrown by processing class, display it!
                if (errFlag == org.dspace.submit.step.UploadWithEmbargoStep.STATUS_UPLOAD_ERROR)
                {
                    file.addError(T_upload_error);
                }

                if(errFlag == org.dspace.submit.step.UploadWithEmbargoStep.STATUS_UNACCEPTABLE_FORMAT ||
                   errFlag == org.dspace.submit.step.UploadWithEmbargoStep.STATUS_UNKNOWN_FORMAT)
                {
                    file.addError(ETD_INVALID_FILE_FORMAT_ERROR);
                }

                // if virus checking was attempted and failed in error then let the user know
                if (errFlag == org.dspace.submit.step.UploadWithEmbargoStep.STATUS_VIRUS_CHECKER_UNAVAILABLE)
                {
                    file.addError(T_virus_checker_error);
                }

                 // if virus checking was attempted and a virus found then let the user know
                if (errFlag == org.dspace.submit.step.UploadWithEmbargoStep.STATUS_CONTAINS_VIRUS)
                {
                    file.addError(T_virus_error);
                }
            }

            // Insert embargo form fields section to the page's body
            addEmbargoFieldSection(item, upload);

            org.dspace.app.xmlui.wing.element.Item uploadActions = upload.addItem();

            // add control buttons
            uploadActions.addButton(AbstractProcessingStep.PREVIOUS_BUTTON).setValue(T_previous);
            uploadActions.addButton(AbstractProcessingStep.CANCEL_BUTTON).setValue(T_save);
            uploadActions.addButton(SUBMIT_UPLOAD_BUTTON).setValue(ETD_SUBMIT_UPLOAD_BUTTON);
    	}
        else
        {
            Division fileSummaryDiv = null;
            fileSummaryDiv = div.addDivision("submit-file-summary");
            fileSummaryDiv.setHead(T_head2);

            for(Bitstream bs : bitstreams)
            {
                printFileSummary(fileSummaryDiv, item, bs);
            }

            Division embargoSummaryDiv = null;
            embargoSummaryDiv = div.addDivision("submit-embargo-summary");
            embargoSummaryDiv.setHead(ETD_EMBARGO_SUMMARY_HEAD);

            for(Bitstream bs : bitstreams)
            {
                printEmbargoSummary(embargoSummaryDiv, item, bs);
            }

            Para p1 = div.addPara();
            Button b1 = p1.addButton("submit_remove_selected");
            b1.setValue(ETD_SUBMIT_REMOVE_BUTTON_NAME);

            for(Bitstream bs : bitstreams)
            {
                Hidden h1 = div.addHidden("remove");
                h1.setValue(bs.getID());
            }

            upload = div.addList("submit-upload-new-part2", List.TYPE_FORM);

            // add standard control/paging buttons
            addControlButtons(upload);
        }

        make_sherpaRomeo_submission(item, div);
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
     * by using a call to reviewList.addList().  This sublist is
     * the list you return from this method!
     *
     * @param reviewList
     *      The List to which all reviewable information should be added
     * @return
     *      The new sub-List object created by this step, which contains
     *      all the reviewable information.  If this step has nothing to
     *      review, then return null!
     * @throws org.xml.sax.SAXException
     * @throws java.sql.SQLException
     * @throws org.dspace.app.xmlui.utils.UIException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     */
    @Override
    public List addReviewSection(List reviewList) throws SAXException,
        WingException, UIException, SQLException, IOException,
        AuthorizeException
    {
        // Create a new list section for this step (and set its heading)
        List uploadSection = reviewList.addList("submit-review-" + this.stepAndPage, List.TYPE_FORM);
        uploadSection.setHead(T_head);

        // Review all uploaded files
        Item item = submission.getItem();
        Bundle[] bundles = item.getBundles("ORIGINAL");
        Bitstream[] bitstreams = new Bitstream[0];
        if (bundles.length > 0)
        {
            bitstreams = bundles[0].getBitstreams();
        }

        for (Bitstream bitstream : bitstreams)
        {
            BitstreamFormat bitstreamFormat = bitstream.getFormat();

            String name = bitstream.getName();
            String url = makeBitstreamLink(item, bitstream);
            String format = bitstreamFormat.getShortDescription();
            Message support = ReviewStep.T_unknown;
            if (bitstreamFormat.getSupportLevel() == BitstreamFormat.KNOWN)
            {
                support = T_known;
            }
            else if (bitstreamFormat.getSupportLevel() == BitstreamFormat.SUPPORTED)
            {
                support = T_supported;
            }

            uploadSection.addLabel("File");
            org.dspace.app.xmlui.wing.element.Item file = uploadSection.addItem();
            file.addXref(url,name);
            file.addContent(" - "+ format + " ");
            file.addContent(support);
        }

        /**
         * The code below adds information about the uploaded file's
         * embargo/restriction status and, if the file is under embargo,
         * the restriction's ending date.
         */
        String status = null;
        String rights = null;
        DateTimeFormatter dft = DateTimeFormat.forPattern("MM-dd-yyyy");
        DateTime enddate = null;

        status = EmbargoManager.getEmbargoStatusMDV(context, submission.getItem());
        rights = EmbargoManager.getEmbargoRightsMDV(context, submission.getItem());

        uploadSection.addLabel(T_COLUMN_STATUS);

        if(status != null && rights != null)
        {
            if(status.equals(ETDEmbargoSetter.EMBARGOED))
            {
                switch (rights)
                {
                    case ETDEmbargoSetter.EMBARGO_NOT_AUBURN_STR:
                        uploadSection.addItem().addContent(EMBARGO_RADIO_BUTTON2);
                        break;
                    case ETDEmbargoSetter.EMBARGO_GLOBAL_STR:
                        uploadSection.addItem().addContent(EMBARGO_RADIO_BUTTON3);
                        break;
                }

                for(Bitstream bs : bitstreams)
                {
                    for(ResourcePolicy bsRP : AuthorizeManager.getPoliciesActionFilter(context, bs, Constants.READ))
                    {
                        if(bsRP.getEndDate() != null)
                        {
                            enddate = new DateTime(bsRP.getEndDate());
                        }
                    }
                }

                if(enddate != null)
                {
                    uploadSection.addLabel(T_COLUMN_ENDDATE);
                    uploadSection.addItem().addContent(dft.print(enddate));
                }
            }
            else
            {
                uploadSection.addItem().addContent(EMBARGO_RADIO_BUTTON1);
            }
        }
        else
        {
            uploadSection.addItem().addContent(EMBARGO_RADIO_BUTTON1);
        }

        // return this new "upload" section
        return uploadSection;
    }

    /**
     * Returns canonical link to a bitstream in the item.
     *
     * @param item The DSpace Item that the bitstream is part of
     * @param bitstream The bitstream to link to
     * @returns a String link to the bitstream
     */
    private String makeBitstreamLink(Item item, Bitstream bitstream)
    {
        String name = bitstream.getName();
        StringBuilder result = new StringBuilder(contextPath);
        result.append("/bitstream/item/").append(String.valueOf(item.getID()));
        // append name although it isn't strictly necessary
        try
        {
            if (name != null)
            {
                result.append("/").append(Util.encodeBitstreamName(name, "UTF-8"));
            }
        }
        catch (UnsupportedEncodingException uee)
        {
            // just ignore it, we don't have to have a pretty
            // name on the end of the url because the sequence id will
            // locate it. However it means that links in this file might
            // not work....
        }
        result.append("?sequence=").append(String.valueOf(bitstream.getSequenceID()));
        return result.toString();
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
    private void addEmbargoFieldSection(DSpaceObject dso, List form)
        throws SQLException, WingException, IOException,
        AuthorizeException
    {
        DateTime rpEndDate = null;
        String embargoStatus = null;
        String embargoRadioVal = null;
        String embargoType = null;
        DateTimeFormatter dft = DateTimeFormat.forPattern("yyyy-MM-dd");

        // The value of this hidden input field is used
        // by a JavaScript method in the web UI.
        Hidden datefieldDisplayInput = form.addItem().addHidden("datefieldDisplay");
        datefieldDisplayInput.setValue(0);

        //Embargo Question Radio Button Group
        Radio embargoTypeRadio = form.addItem().addRadio(ETD_CREATE_QUESTION_FIELD_NAME);
        embargoTypeRadio.setLabel(ETD_CREATE_QUESTION_LABEL);
        embargoTypeRadio.setRequired();
        embargoTypeRadio.addOption("1", EMBARGO_RADIO_BUTTON1);
        embargoTypeRadio.addOption("2", EMBARGO_RADIO_BUTTON2);
        embargoTypeRadio.addOption("3", EMBARGO_RADIO_BUTTON3);

        // Date Input Text Field
        Text embargoDateField = form.addItem().addText(ETD_DATE_FIELD_NAME);
        embargoDateField.setLabel(ETD_DATE_LABEL);
        embargoDateField.setHelp(ETD_DATE_HELP);

        /**
         * Populate input field values and/or add error messages
         */
        if(submissionInfo.containsKey(ETD_CREATE_QUESTION_FIELD_NAME+"_ERROR"))
        {
            embargoTypeRadio.addError(STATUS_ERROR_EMBARGO_CREATION_REQUIRED_ERROR);
        }

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

    /**
     *
     * @param div
     * @param item
     * @param bs
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws org.dspace.app.xmlui.wing.WingException
     */
    private void printFileSummary(Division div, Item item, Bitstream bs)
            throws SQLException, IOException, AuthorizeException,
            WingException
    {
        BitstreamFormat bsFormat = null;
        String format = null;
        bsFormat = bs.getFormat();

        if(bsFormat != null)
        {
            format = bsFormat.getMIMEType();
        }

        Division fileRowDiv = div.addDivision("fileRow");
        fileRowDiv.setHead(T_column2);
        Para fileRowPara = fileRowDiv.addPara();
        fileRowPara.addXref(makeBitstreamLink(item, bs),bs.getName());

        if(format != null)
        {
            fileRowPara.addContent(" ("+format+")");
        }

        Division sizeRow = div.addDivision("sizeRow");
        sizeRow.setHead(T_column3);
        sizeRow.addPara(String.valueOf(bs.getSize()));
    }

    /**
     *
     * @param div
     * @param item
     * @param bs
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws org.dspace.app.xmlui.wing.WingException
     */
    private void printEmbargoSummary(Division div, Item item, Bitstream bs)
        throws SQLException, IOException, AuthorizeException,
            WingException
    {
        String status = null;
        String rights = null;
        String endDateStr = null;
        DateTime enddate = null;
        DateTimeFormatter dft = DateTimeFormat.forPattern("MM-dd-yyyy");

        // Embargo Status Information
        Division statusRow = div.addDivision("statusRow");
        statusRow.setHead(T_COLUMN_STATUS);
        Message statusTxt = null;

        rights = EmbargoManager.getEmbargoRightsMDV(context, submission.getItem());
        status = EmbargoManager.getEmbargoStatusMDV(context, submission.getItem());

        if(status != null && rights != null)
        {
            if(status.equals(ETDEmbargoSetter.EMBARGOED))
            {
                if(rights.equals(ETDEmbargoSetter.EMBARGO_NOT_AUBURN_STR))
                {
                    statusTxt = EMBARGO_RADIO_BUTTON2;
                }
                else if(rights.equals(ETDEmbargoSetter.EMBARGO_GLOBAL_STR))
                {
                    statusTxt = EMBARGO_RADIO_BUTTON3;
                }
            }
            else
            {
                statusTxt = EMBARGO_RADIO_BUTTON1;
            }
        }
        else if(status != null && rights == null)
        {
            statusTxt = EMBARGO_RADIO_BUTTON1;
        }

        statusRow.addPara(statusTxt);

        // Embargo End Date Information
        endDateStr = EmbargoManager.getEmbargoEndDateMDV(context, item);

        if(endDateStr != null)
        {
            enddate = new DateTime(endDateStr);

            Division endDateRow = div.addDivision("enddateRow");
            endDateRow.setHead(T_COLUMN_ENDDATE);
            endDateRow.addPara(dft.print(enddate));
        }

        Para p1 = div.addPara();
        Button b1 = p1.addButton("submit_edit_"+bs.getID());
        b1.setValue(ETD_SUBMIT_EDIT_RESTRICTIONS_BUTTON_NAME);
    }
}