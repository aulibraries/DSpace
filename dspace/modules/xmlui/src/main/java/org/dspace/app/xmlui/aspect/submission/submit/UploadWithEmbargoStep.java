/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.submission.submit;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.log4j.Logger;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.util.Util;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.LogManager;
import org.dspace.embargo.factory.EmbargoServiceFactory;
import org.dspace.embargo.service.EmbargoService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.*;

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
    protected static final Message T_head = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.head");
    protected static final Message T_file = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.file");
    protected static final Message T_file_help = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.file_help");
    protected static final Message T_file_error = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.file_error");
    protected static final Message T_upload_error =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.upload_error");

    protected static final Message T_virus_checker_error =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.virus_checker_error");
    protected static final Message T_virus_error =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.virus_error");

    protected static final Message T_description = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.description");
    protected static final Message T_description_help = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.description_help");
    protected static final Message T_submit_upload = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.submit_upload");
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
    protected static final Message T_submit_edit =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.submit_edit");

    protected static final Message T_submit_policy =
            message("xmlui.Submission.submit.UploadWithEmbargoStep.submit_policy");

    protected static final Message T_checksum = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.checksum");
    protected static final Message T_submit_remove = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.submit_remove");

    // Custom Constants Section
    protected static final Message AUETD_INVALID_FILE_FORMAT_ERROR =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_file_upload_invalid_file_format_error");
    protected static final Message AUETD_FILE_UPLOAD_FORM_HEAD =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_file_upload_form_head");
    protected static final Message AUETD_FILE_UPLOAD_HELP =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_file_upload_field_help");
    protected static final Message AUETD_FILE_UPLOAD_SUBMIT_BUTTON_NAME =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_file_upload_submit_button_name");

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
    protected static final Message AUETD_EMBARGO_SUMMARY_HEAD =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_summary_head");
    protected static final Message AUETD_T_COLUMN_STATUS =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_status_column");
    protected static final Message AUETD_T_COLUMN_ENDDATE =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_enddate_column");
    protected static final Message AUETD_EMBARGO_LENGTH_COLUMN = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_length_column");

    protected static final Message AUETD_SUBMIT_REMOVE_BUTTON_NAME =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_submit_remove_button_name");
    protected static final Message AUETD_SUBMIT_EDIT_RESTRICTIONS_BUTTON_NAME =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_submit_edit_restrictions_button_name");

    // End Custom Constants Section

    /**
     * Global reference to edit file page
     * (this is used when a user requests to edit a bitstream)
     **/
    private EditFileStep editFile = null;

    /*private EditBitstreamPolicies editBitstreamPolicies = null;

    private EditPolicyStep editPolicy = null;

    private boolean isAdvancedFormEnabled=true;*/

    protected EmbargoService embargoService = EmbargoServiceFactory.getInstance().getEmbargoService();

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
     */
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters parameters) 
    throws ProcessingException, SAXException, IOException
    { 
        super.setup(resolver,objectModel,src,parameters);

        if(this.errorFlag == org.dspace.submit.step.UploadWithEmbargoStep.STATUS_EDIT_BITSTREAM)
        {
            this.editFile = new EditFileStep();
            this.editFile.setup(resolver, objectModel, src, parameters);
        }
        else
        {
            this.editFile = null;
        }               
    }

    public void addPageMeta(PageMeta pageMeta) throws WingException, AuthorizeException, IOException, SAXException, SQLException {
        super.addPageMeta(pageMeta);
        //pageMeta.addMetadata("javascript", "static").addContent("static/js/accessFormUtil.js");
    }

    public void addBody(Body body) throws SAXException, WingException,
        UIException, SQLException, IOException, AuthorizeException
    {
        // If we are actually editing information of an uploaded file,
        // then display that body instead!
    	if(this.editFile!=null) {
            editFile.addBody(body);
            return;
        }

        // Get a list of all files in the original bundle
		Item item = submission.getItem();
		Collection collection = submission.getCollection();
		String actionURL = contextPath + "/handle/"+collection.getHandle() + "/submit/" + knot.getId() + ".continue";
		boolean disableFileEditing = (submissionInfo.isInWorkflow()) && !DSpaceServicesFactory.getInstance().getConfigurationService().getBooleanProperty("workflow.reviewer.file-edit");
        java.util.List<Bundle> bundles = itemService.getBundles(item, "ORIGINAL");
        java.util.List<Bitstream> bitstreams = new ArrayList<>();
		if (bundles.size() > 0) {
			bitstreams = bundles.get(0).getBitstreams();
		}

        int errorSize = 0;
        // if there were errors then stop execution here
        @SuppressWarnings("unchecked")
        java.util.List<String> subInfoKeyList = new ArrayList<String>(submissionInfo.keySet());
        for(String key : subInfoKeyList) {
            if(key.contains("ERROR")) {
                errorSize++;
            }
        }

		// Part A: 
		//  First ask the user if they would like to upload a new file (may be the first one)
    	Division div = body.addInteractiveDivision("submit-upload", actionURL, Division.METHOD_MULTIPART, "primary submission");
    	div.setHead(T_submission_head);
    	addSubmissionProgressList(div);

    	List upload = null;
    	if (bitstreams.size() <= 0 && !disableFileEditing && errorSize >= 0) {
    		// Only add the upload capabilities for new item submissions
	    	upload = div.addList("submit-upload-new", List.TYPE_FORM);
	        upload.setHead(T_head);    

	        File file = upload.addItem().addFile("file");
	        file.setLabel(T_file);
	        file.setHelp(T_file_help);
	        file.setRequired();

	        // if no files found error was thrown by processing class, display it!
	        if (this.errorFlag==org.dspace.submit.step.UploadWithEmbargoStep.STATUS_NO_FILES_ERROR) {
                file.addError(T_file_error);
            }

            // if an upload error was thrown by processing class, display it!
            if (this.errorFlag == org.dspace.submit.step.UploadWithEmbargoStep.STATUS_UPLOAD_ERROR) {
                file.addError(T_upload_error);
            }

            if(this.errorFlag == org.dspace.submit.step.UploadWithEmbargoStep.AUETD_STATUS_UNACCEPTABLE_FORMAT) {
                file.addError(AUETD_INVALID_FILE_FORMAT_ERROR);
            }

            // if virus checking was attempted and failed in error then let the user know
            if (this.errorFlag == org.dspace.submit.step.UploadWithEmbargoStep.STATUS_VIRUS_CHECKER_UNAVAILABLE) {
                file.addError(T_virus_checker_error);
            }

             // if virus checking was attempted and a virus found then let the user know
            if (this.errorFlag == org.dspace.submit.step.UploadWithEmbargoStep.STATUS_CONTAINS_VIRUS) {
                file.addError(T_virus_error);
            }

	        // Insert embargo form fields section to the page's body
            addEmbargoFieldSection(upload);

            org.dspace.app.xmlui.wing.element.Item uploadActions = upload.addItem();

            // add control buttons
            uploadActions.addButton(org.dspace.submit.AbstractProcessingStep.PREVIOUS_BUTTON).setValue(T_previous);
            uploadActions.addButton(org.dspace.submit.AbstractProcessingStep.CANCEL_BUTTON).setValue(T_save);
            uploadActions.addButton(org.dspace.submit.step.UploadStep.SUBMIT_UPLOAD_BUTTON).setValue(AUETD_FILE_UPLOAD_SUBMIT_BUTTON_NAME);

    	} else {
            Bitstream bitstream = bitstreams.get(0);
            Division fileSummaryDiv = null;
            fileSummaryDiv = div.addDivision("submit-file-summary");
            fileSummaryDiv.setHead(T_head2);

            printFileSummary(fileSummaryDiv, item, bitstream);

            Division embargoSummaryDiv = null;
            embargoSummaryDiv = div.addDivision("submit-embargo-summary");
            embargoSummaryDiv.setHead(AUETD_EMBARGO_SUMMARY_HEAD);

            printEmbargoSummary(embargoSummaryDiv, item, bitstream);

            Para p1 = div.addPara();
            Button b1 = p1.addButton("submit_remove_selected");
            b1.setValue(AUETD_SUBMIT_REMOVE_BUTTON_NAME);

            for(Bitstream bs : bitstreams) {
                Hidden h1 = div.addHidden("remove");
                h1.setValue(String.valueOf(bs.getID()));
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
     */
    public List addReviewSection(List reviewList) throws SAXException,
        WingException, UIException, SQLException, IOException,
        AuthorizeException
    {
        // Create a new list section for this step (and set its heading)
        List uploadSection = reviewList.addList("submit-review-" + this.stepAndPage, List.TYPE_FORM);
        uploadSection.setHead(T_head);

        // Review all uploaded files
        Item item = submission.getItem();
        java.util.List<Bundle> bundles = itemService.getBundles(item, "ORIGINAL");
        java.util.List<Bitstream> bitstreams = new ArrayList<>();
        if (bundles.size() > 0)
        {
            bitstreams = bundles.get(0).getBitstreams();
        }

        for (Bitstream bitstream : bitstreams) {
            BitstreamFormat bitstreamFormat = bitstream.getFormat(context);

            String name = bitstream.getName();
            String url = makeBitstreamLink(item, bitstream);
            String format = bitstreamFormat.getShortDescription();
            Message support = ReviewStep.T_unknown;
            if (bitstreamFormat.getSupportLevel() == BitstreamFormat.KNOWN) {
                support = T_known;
            } else if (bitstreamFormat.getSupportLevel() == BitstreamFormat.SUPPORTED) {
                support = T_supported;
            }

            org.dspace.app.xmlui.wing.element.Item file = uploadSection.addItem();
            file.addXref(url,name);
            file.addContent(" - "+ format + " ");
            file.addContent(support);    
        }

        uploadSection.addLabel(AUETD_T_COLUMN_STATUS);
        
        Message statusTxt = null;
        Map<String, String> embargoLengthMap = new HashMap<>();
        embargoLengthMap.put("1", "One year");
        embargoLengthMap.put("2", "Two years");
        embargoLengthMap.put("3", "Three years");
        embargoLengthMap.put("4", "Four years");
        embargoLengthMap.put("5", "Five years");

        if (submissionInfo.containsKey(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME) &&
                StringUtils.isNotBlank(submissionInfo.get(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME).toString())) {
            int embargoCreationAnswer = 0;
            embargoCreationAnswer = Integer.parseInt(submissionInfo.get(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME).toString());

            if (embargoCreationAnswer == 1) {
                statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON1;
            } else if (embargoCreationAnswer == 2) {
                statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON2;
            } else if (embargoCreationAnswer == 3) {
                statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON3;
            } else {
                statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON1;
            }
        } else {
            String embargoRights = null;
            String embargoStatus = null;
            java.util.List<MetadataValue> embargoRightsList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "rights", null, org.dspace.content.Item.ANY);
            java.util.List<MetadataValue> embargoStatusList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "status", org.dspace.content.Item.ANY);

            if (embargoRightsList != null && embargoRightsList.size() > 0) {
                embargoRights = embargoRightsList.get(0).getValue();
            }

            if (embargoStatusList != null & embargoStatusList.size() > 0) {
                embargoStatus = embargoStatusList.get(0).getValue();
            }

            if(StringUtils.isNotBlank(embargoStatus) && StringUtils.isNotBlank(embargoRights)) {
                if(embargoStatus.equals(Constants.EMBARGOED)) {
                    if(embargoRights.equals(Constants.EMBARGO_NOT_AUBURN_STR)) {
                        statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON2;
                        
                    } else if(embargoRights.equals(Constants.EMBARGO_GLOBAL_STR)) {
                        statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON3;
                    }
                } else {
                    statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON1;
                }
            } else if(StringUtils.isNotBlank(embargoStatus) && StringUtils.isBlank(embargoRights)) {
                statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON1;
            }
        }

        uploadSection.addItem().addContent(statusTxt);

        if (submissionInfo.containsKey(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME) &&
                StringUtils.isNotBlank(submissionInfo.get(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME).toString())) {
            uploadSection.addLabel(AUETD_EMBARGO_LENGTH_COLUMN);
            uploadSection.addItem().addContent(embargoLengthMap.get(submissionInfo.get(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME).toString()));
        } else {
            int embargoLengthNum = getEmbargoLengthInYears(item);
            if (embargoLengthNum > 0) {
                uploadSection.addLabel(AUETD_EMBARGO_LENGTH_COLUMN);
                uploadSection.addItem().addContent(embargoLengthMap.get(String.valueOf(embargoLengthNum)));
            }
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
        String format = null;
        BitstreamFormat bsFormat = bs.getFormat(context);

        if(bsFormat != null) {
            format = bsFormat.getMIMEType();
        }

        Division fileRowDiv = div.addDivision("fileRow");
        fileRowDiv.setHead(T_column2);
        Para fileRowPara = fileRowDiv.addPara();
        fileRowPara.addXref(makeBitstreamLink(item, bs),bs.getName());

        if (StringUtils.isNotBlank(format)) {
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
        // Embargo Status Information
        Division statusRow = div.addDivision("statusRow");
        statusRow.setHead(AUETD_T_COLUMN_STATUS);
        Message statusTxt = null;
        Map<String, String> embargoLengthMap = new HashMap<>();
        embargoLengthMap.put("1", "One year");
        embargoLengthMap.put("2", "Two years");
        embargoLengthMap.put("3", "Three years");
        embargoLengthMap.put("4", "Four years");
        embargoLengthMap.put("5", "Five years");

        if (submissionInfo.containsKey(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME) &&
                StringUtils.isNotBlank(submissionInfo.get(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME).toString())) {
            int embargoCreationAnswer = 0;
            embargoCreationAnswer = Integer.parseInt(submissionInfo.get(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME).toString());

            if (embargoCreationAnswer == 1) {
                statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON1;
            } else if (embargoCreationAnswer == 2) {
                statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON2;
            } else if (embargoCreationAnswer == 3) {
                statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON3;
            } else {
                statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON1;
            }
        } else {
            String embargoRights = null;
            String embargoStatus = null;
            java.util.List<MetadataValue> embargoRightsList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "rights", null, org.dspace.content.Item.ANY);
            java.util.List<MetadataValue> embargoStatusList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "status", org.dspace.content.Item.ANY);

            if (embargoRightsList != null && embargoRightsList.size() > 0) {
                embargoRights = embargoRightsList.get(0).getValue();
            }

            if (embargoStatusList != null & embargoStatusList.size() > 0) {
                embargoStatus = embargoStatusList.get(0).getValue();
            }

            if(StringUtils.isNotBlank(embargoStatus) && StringUtils.isNotBlank(embargoRights)) {
                if(embargoStatus.equals(Constants.EMBARGOED)) {
                    if(embargoRights.equals(Constants.EMBARGO_NOT_AUBURN_STR)) {
                        statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON2;
                        
                    } else if(embargoRights.equals(Constants.EMBARGO_GLOBAL_STR)) {
                        statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON3;
                    }
                } else {
                    statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON1;
                }
            } else if(StringUtils.isNotBlank(embargoStatus) && StringUtils.isBlank(embargoRights)) {
                statusTxt = AUETD_CREATE_EMBARGO_RADIO_BUTTON1;
            }
        }

        statusRow.addPara(statusTxt);

        if (submissionInfo.containsKey(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME) &&
                StringUtils.isNotBlank(submissionInfo.get(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME).toString())) {
            Division endDateRow = div.addDivision("enddateRow");
            endDateRow.setHead(AUETD_EMBARGO_LENGTH_COLUMN);
            endDateRow.addPara(embargoLengthMap.get(submissionInfo.get(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME).toString()));
        } else {
            int embargoLengthNum = getEmbargoLengthInYears(item);
            if(embargoLengthNum > 0) {
                Division endDateRow = div.addDivision("enddateRow");
                endDateRow.setHead(AUETD_EMBARGO_LENGTH_COLUMN);
                endDateRow.addPara(embargoLengthMap.get(String.valueOf(embargoLengthNum)));
            }
        }

        Para p1 = div.addPara();
        Button b1 = p1.addButton("submit_edit_"+bs.getID());
        b1.setValue(AUETD_SUBMIT_EDIT_RESTRICTIONS_BUTTON_NAME);
    }

    private int getEmbargoLengthInYears(Item item)
        throws AuthorizeException, IOException, SQLException
    {
        int embargoLength = 0;

        if (item != null) {
            java.util.List<MetadataValue> embargoLengthList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "length", org.dspace.content.Item.ANY);
            if (embargoLengthList != null && embargoLengthList.size() > 0) {
                ArrayList<String> embargoLengths = new ArrayList<String>();
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
        
