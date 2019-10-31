/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.administrative.item;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

//import org.apache.cocoon.environment.ObjectModelHelper;
//import org.apache.cocoon.environment.Request;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
//import org.dspace.app.xmlui.aspect.submission.submit.AccessStepUtil;
import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.Hidden;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.app.xmlui.wing.element.PageMeta;
import org.dspace.app.xmlui.wing.element.Radio;
//import org.dspace.app.xmlui.wing.element.Select;
import org.dspace.app.xmlui.wing.element.Text;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
//import org.dspace.content.BitstreamFormat;
//import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.LogManager;
import org.dspace.embargo.factory.EmbargoServiceFactory;
import org.dspace.embargo.service.EmbargoService;
//import org.dspace.services.factory.DSpaceServicesFactory;
import org.xml.sax.SAXException;

/**
 * 
 * Show a form allowing the user to edit a bitstream's metadata, the description
 * and format.
 *
 * @author Scott Phillips
 */
public class EditBitstreamForm extends AbstractDSpaceTransformer
{

	/** Language strings */
	private static final Message T_dspace_home = message("xmlui.general.dspace_home");
	private static final Message T_submit_save = message("xmlui.general.save");
	private static final Message T_submit_cancel = message("xmlui.general.cancel");
	private static final Message T_item_trail = message("xmlui.administrative.item.general.item_trail");

	private static final Message T_title = message("xmlui.administrative.item.EditBitstreamForm.title");
	private static final Message T_trail = message("xmlui.administrative.item.EditBitstreamForm.trail");
	private static final Message T_head1 = message("xmlui.administrative.item.EditBitstreamForm.head1");
	private static final Message T_file_label = message("xmlui.administrative.item.EditBitstreamForm.file_label");
	/*private static final Message T_primary_label = message("xmlui.administrative.item.EditBitstreamForm.primary_label");
	private static final Message T_primary_option_yes = message("xmlui.administrative.item.EditBitstreamForm.primary_option_yes");
	private static final Message T_primary_option_no = message("xmlui.administrative.item.EditBitstreamForm.primary_option_no");
	private static final Message T_description_label = message("xmlui.administrative.item.EditBitstreamForm.description_label");
	private static final Message T_description_help = message("xmlui.administrative.item.EditBitstreamForm.description_help");
	private static final Message T_para1 = message("xmlui.administrative.item.EditBitstreamForm.para1");
	private static final Message T_format_label = message("xmlui.administrative.item.EditBitstreamForm.format_label");
	private static final Message T_format_default = message("xmlui.administrative.item.EditBitstreamForm.format_default");
	private static final Message T_para2 = message("xmlui.administrative.item.EditBitstreamForm.para2");
	private static final Message T_user_label = message("xmlui.administrative.item.EditBitstreamForm.user_label");
	private static final Message T_user_help = message("xmlui.administrative.item.EditBitstreamForm.user_help");*/
    private static final Message T_filename_label = message("xmlui.administrative.item.EditBitstreamForm.name_label");
    private static final Message T_filename_help = message("xmlui.administrative.item.EditBitstreamForm.name_help");

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
    protected static final Message AUETD_STATUS_ERROR_EMBARGO_LENGTH_OUTOFDATE = 
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_embargo_length_outofdate_error");

    // Embargo Info Table Column Headers
    protected static final Message AUETD_T_COLUMN_STATUS =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_status_column");
    protected static final Message AUETD_T_COLUMN_ENDDATE =
        message("xmlui.Submission.submit.UploadWithEmbargoStep.AUETD_enddate_column");

    //private boolean isAdvancedFormEnabled=true;

	protected AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
	protected BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
	protected BitstreamFormatService bitstreamFormatService = ContentServiceFactory.getInstance().getBitstreamFormatService();
    protected EmbargoService embargoService = EmbargoServiceFactory.getInstance().getEmbargoService();
    protected ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private static final Logger log = Logger.getLogger(EditBitstreamForm.class);

    @Override
	public void addPageMeta(PageMeta pageMeta) throws WingException
	{
		pageMeta.addMetadata("title").addContent(T_title);

		pageMeta.addTrailLink(contextPath + "/", T_dspace_home);
		pageMeta.addTrailLink(contextPath + "/admin/item",T_item_trail);
		pageMeta.addTrail().addContent(T_trail);
        pageMeta.addMetadata("javascript", "static").addContent("static/js/editItemUtil.js");
	}

    @Override
	public void addBody(Body body) throws SAXException, WingException, UIException,
        SQLException, IOException, AuthorizeException
	{
        //isAdvancedFormEnabled= DSpaceServicesFactory.getInstance().getConfigurationService().getBooleanProperty("webui.submission.restrictstep.enableAdvancedForm", false);

		// Get our parameters
		UUID bitstreamID = UUID.fromString(parameters.getParameter("bitstreamID", null));

		// Get the bitstream and all the various formats
                // Administrator is allowed to see internal formats too.
		Bitstream bitstream = bitstreamService.find(context, bitstreamID);
		//BitstreamFormat currentFormat = bitstream.getFormat(context);
        /*java.util.List<BitstreamFormat> bitstreamFormats = authorizeService.isAdmin(context) ?
					bitstreamFormatService.findAll(context) :
					bitstreamFormatService.findNonInternal(context);*/

		/*boolean primaryBitstream = false;
		java.util.List<Bundle> bundles = bitstream.getBundles();
		if (bundles != null && bundles.size() > 0) {
			if (bitstream.equals(bundles.get(0).getPrimaryBitstream())) {
				primaryBitstream = true;
			}
		}*/

		// File name & url
		String fileUrl = contextPath + "/bitstream/id/" +bitstream.getID() + "/" + bitstream.getName();
		String fileName = bitstream.getName();

		// DIVISION: main
		Division div = body.addInteractiveDivision("edit-bitstream", contextPath+"/admin/item", Division.METHOD_MULTIPART, "primary administrative item");
		div.setHead(T_head1);

		// LIST: edit form
		List edit = div.addList("edit-bitstream-list", List.TYPE_FORM);
        edit.addLabel(T_file_label);
        edit.addItem(null,"break-all").addXref(fileUrl, fileName);

        Text bitstreamName = edit.addItem().addText("bitstreamName");
        bitstreamName.setLabel(T_filename_label);
        bitstreamName.setHelp(T_filename_help);
        bitstreamName.setValue(fileName);

        // EMBARGO FIELDS
        addEmbargoFieldSection(bitstream, edit);

		// ITEM: form actions
		org.dspace.app.xmlui.wing.element.Item actions = edit.addItem();
		actions.addButton("submit_save").setValue(T_submit_save);
		actions.addButton("submit_cancel").setValue(T_submit_cancel);

		div.addHidden("administrative-continue").setValue(knot.getId()); 
	}

    private void addEmbargoFieldSection(Bitstream bitstream, List form)
        throws AuthorizeException, IOException, SQLException, WingException
    {
        // The value of this hidden input field is used
        // by a JavaScript method in the web UI.
        Hidden embargoLengthFieldDisplayInput = form.addItem().addHidden("embargoLengthFieldDisplay");
        embargoLengthFieldDisplayInput.setValue(0);

        String errorString = parameters.getParameter("errors",null);
        ArrayList<String> errors = new ArrayList<String>();

        if (errorString != null) {
            errors.addAll(Arrays.asList(errorString.split(",")));
        }

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

        int embargoType = getSelectedEmbargoType(bitstream);
        int embargoLength = getEmbargoLengthInYears(bitstream);

        if (embargoType >= 1 && embargoType <=3) {
            embargoTypeRadio.setOptionSelected(String.valueOf(embargoType));

            if (embargoType == 2 || embargoType == 3) {
                embargoLengthFieldDisplayInput.setValue(1);

                if (errors.contains(org.dspace.app.xmlui.aspect.administrative.FlowItemUtils.AUETD_EMBARGO_LENGTH_FIELD_NAME_REQUIRED_ERROR) ||
                        errors.contains(org.dspace.app.xmlui.aspect.administrative.FlowItemUtils.AUETD_EMBARGO_LENGTH_FIELD_NAME_OUT0FDATE_ERROR)) {

                    if (errors.contains(org.dspace.app.xmlui.aspect.administrative.FlowItemUtils.AUETD_EMBARGO_LENGTH_FIELD_NAME_REQUIRED_ERROR)) {
                        embargoLengthField.addError(AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED);
                    } else if (errors.contains(org.dspace.app.xmlui.aspect.administrative.FlowItemUtils.AUETD_EMBARGO_LENGTH_FIELD_NAME_OUT0FDATE_ERROR)) {
                        embargoLengthField.addError(AUETD_STATUS_ERROR_EMBARGO_LENGTH_OUTOFDATE);
                        if (embargoLength >= 1 && embargoLength <= 5) {
                            embargoLengthField.setOptionSelected(String.valueOf(embargoLength));
                        }
                    }
                } else {
                    if (embargoLength >= 1 && embargoLength <= 5) {
                        embargoLengthField.setOptionSelected(String.valueOf(embargoLength));
                    }
                }
            }
        }

        /*if (embargoType >= 1 && embargoType <= 3) {
            embargoTypeRadio.setOptionSelected(String.valueOf(embargoType));

            if (embargoType == 2 || embargoType == 3) {
                embargoLengthFieldDisplayInput.setValue(1);
            }
        }

        if (embargoLength >= 1 && embargoLength <= 5) {
            embargoLengthField.setOptionSelected(String.valueOf(embargoLength));
        } else {
            embargoLengthFieldDisplayInput.setValue(1);
            embargoLengthField.addError(AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED);
        }*/
    }

    private int getSelectedEmbargoType(DSpaceObject dso)
        throws AuthorizeException, IOException, SQLException
    {
        Item item = null;
        Bitstream bitstream;
        int embargoType = 0;

        if (dso instanceof Bitstream) {
            bitstream = bitstreamService.find(context, dso.getID());
            DSpaceObject parent = bitstreamService.getParentObject(context, bitstream);

            if (parent != null) {
                item = itemService.find(context, parent.getID());
            }
        }

        String embargoRights = null;
        String embargoStatus = null;
        if (item != null) {
            java.util.List<MetadataValue> embargoRightsList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "rights", null, Item.ANY);
            java.util.List<MetadataValue> embargoStatusList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "status", Item.ANY);

            if (embargoRightsList != null && embargoRightsList.size() > 0) {
                embargoRights = embargoRightsList.get(0).getValue();
            }

            if (embargoStatusList != null & embargoStatusList.size() > 0) {
                embargoStatus = embargoStatusList.get(0).getValue();
            }

            if (StringUtils.isNotBlank(embargoStatus)) {
                switch (embargoStatus) {
                    case Constants.EMBARGOED:
                        if (StringUtils.isNotBlank(embargoRights)) {
                            switch (embargoRights) {
                                case Constants.EMBARGO_NOT_AUBURN_STR:
                                    embargoType = 2;
                                    break;
                                case Constants.EMBARGO_GLOBAL_STR:
                                    embargoType = 3;
                                    break;
                            }
                        }
                        break;
                    case Constants.NOT_EMBARGOED:
                        embargoType = 1;
                        break;
                }
            }
        }

        return embargoType;
    }

    private int getEmbargoLengthInYears(DSpaceObject dso)
        throws AuthorizeException, IOException, SQLException
    {
        Item item = null;
        Bitstream bitstream;
        int embargoLength = 0;

        if (dso instanceof Bitstream) {
            bitstream = bitstreamService.find(context, dso.getID());
            DSpaceObject parent = bitstreamService.getParentObject(context, bitstream);

            if (parent != null) {
                item = itemService.find(context, parent.getID());
            }
        }

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
