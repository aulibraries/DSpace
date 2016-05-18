/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.xmlui.aspect.administrative.item;

// Java class imports
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.log4j.Logger;
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
import org.dspace.app.xmlui.wing.element.Text;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.LogManager;
import org.dspace.embargo.ETDEmbargoSetter;
import org.dspace.embargo.EmbargoManager;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.xml.sax.SAXException;

/**
 *
 * Show a form allowing the user to edit a bitstream's metadata, the description & format.
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
	private static final Message T_primary_label = message("xmlui.administrative.item.EditBitstreamForm.primary_label");
	private static final Message T_primary_option_yes = message("xmlui.administrative.item.EditBitstreamForm.primary_option_yes");
	private static final Message T_primary_option_no = message("xmlui.administrative.item.EditBitstreamForm.primary_option_no");
	private static final Message T_description_label = message("xmlui.administrative.item.EditBitstreamForm.description_label");
	private static final Message T_description_help = message("xmlui.administrative.item.EditBitstreamForm.description_help");
	private static final Message T_para1 = message("xmlui.administrative.item.EditBitstreamForm.para1");
	private static final Message T_format_label = message("xmlui.administrative.item.EditBitstreamForm.format_label");
	private static final Message T_format_default = message("xmlui.administrative.item.EditBitstreamForm.format_default");
	private static final Message T_para2 = message("xmlui.administrative.item.EditBitstreamForm.para2");
	private static final Message T_user_label = message("xmlui.administrative.item.EditBitstreamForm.user_label");
	private static final Message T_user_help = message("xmlui.administrative.item.EditBitstreamForm.user_help");
    private static final Message T_filename_label = message("xmlui.administrative.item.EditBitstreamForm.name_label");
    private static final Message T_filename_help = message("xmlui.administrative.item.EditBitstreamForm.name_help");

    private boolean isAdvancedFormEnabled=true;

    // Custom Constant Section

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

    // Field names
    protected static final String ETD_CREATE_QUESTION_FIELD_NAME = "create_embargo_radio";
    protected static final String ETD_DATE_FIELD_NAME = "embargo_until_date";

    private static final String ETD_DATE_REQUIRED_ERROR_NAME = "date_required_error";
    private static final String ETD_DATE_IN_PAST_ERROR_NAME = "date_in_past_error";
    private static final String ETD_DATE_IS_CURRENT_ERROR_NAME = "date_is_current_error";
    private static final String EMBARGO_CREATION_REQUIRED_ERROR_NAME = "create_required_error";

    // End Custom Constant Section


    /** log4j logger */
    private static final Logger log = Logger.getLogger(EditBitstreamForm.class);

    /**
     *
     * @param pageMeta
     * @throws org.dspace.app.xmlui.wing.WingException
     */
    @Override
	public void addPageMeta(PageMeta pageMeta) throws WingException
	{
		pageMeta.addMetadata("title").addContent(T_title);

		pageMeta.addTrailLink(contextPath + "/", T_dspace_home);
		pageMeta.addTrailLink(contextPath + "/admin/item",T_item_trail);
		pageMeta.addTrail().addContent(T_trail);
        pageMeta.addMetadata("javascript", "static").addContent("static/js/editItemUtil.js");
	}

    /**
     *
     * @param body
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

        //isAdvancedFormEnabled= ConfigurationManager.getBooleanProperty("webui.submission.restrictstep.enableAdvancedForm", false);

		// Get our parameters
		int bitstreamID = parameters.getParameterAsInteger("bitstreamID",-1);

		// Get the bitstream and all the various formats
        // Administrator is allowed to see internal formats too.
		Bitstream bitstream = Bitstream.find(context, bitstreamID);

		// File name & url
		String fileUrl = contextPath + "/bitstream/id/" +bitstream.getID() + "/" + bitstream.getName();
		String fileName = bitstream.getName();

		// DIVISION: main
		Division div = body.addInteractiveDivision("edit-bitstream", contextPath+"/admin/item", Division.METHOD_MULTIPART, "primary administrative item");
		div.setHead(T_head1);

		// LIST: edit form
        List edit = div.addList("edit-bitstream-list", List.TYPE_FORM);
        edit.addLabel(T_file_label);
        edit.addItem().addXref(fileUrl, fileName);

        Text bitstreamName = edit.addItem().addText("bitstreamName");
        bitstreamName.setLabel(T_filename_label);
        bitstreamName.setHelp(T_filename_help);
        bitstreamName.setValue(fileName);

        // EMBARGO FIELD
        addEmbargoFieldSection(bitstream, edit, bitstreamID);

        // ITEM: form actions
        org.dspace.app.xmlui.wing.element.Item actions = edit.addItem();
        actions.addButton("submit_save").setValue(T_submit_save);
        actions.addButton("submit_cancel").setValue(T_submit_cancel);

        div.addHidden("administrative-continue").setValue(knot.getId());
        div.addHidden("bitstreamID").setValue(bitstreamID);
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
     * @throws java.IOException.IOException
     * @throws org.dspace.authorize.AuthorizeException
     */
    private void addEmbargoFieldSection(DSpaceObject dso, List form, int errorFlag)
        throws SQLException, WingException, IOException, AuthorizeException
    {
        String embargoStatus = null;
        String embargoType = null;
        Item item = null;
        DateTime rpEndDate = null;
        DateTimeFormatter dft = DateTimeFormat.forPattern("yyyy-MM-dd");

        String errorString = parameters.getParameter("errors",null);
        ArrayList<String> errors = new ArrayList<String>();

        if (errorString != null)
        {
            errors.addAll(Arrays.asList(errorString.split(",")));
        }

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

        if(dso!=null)
        {
            if(dso.getType() == Constants.BITSTREAM)
            {
                Bitstream tempBS = (Bitstream) dso;
                Bundle[] bndlList = tempBS.getBundles();

                if(bndlList != null)
                {
                    for(Bundle bndl : tempBS.getBundles())
                    {
                        Item[] bndlItemList = bndl.getItems();
                        if(bndlItemList != null)
                        {
                            for(Item tempItem : bndl.getItems())
                            {
                                item = tempItem;
                            }
                        }
                    }
                }
            }
        }

        if(item != null)
        {
            embargoType = EmbargoManager.getEmbargoRightsMDV(context, item);
            embargoStatus = EmbargoManager.getEmbargoStatusMDV(context, item);

            if(embargoStatus != null && (dso.getName() == null ? Constants.LICENSE_BITSTREAM_NAME != null : !dso.getName().
                       equals(Constants.LICENSE_BITSTREAM_NAME)))
            {
                switch (embargoStatus)
                {
                    case ETDEmbargoSetter.EMBARGOED:
                        if(null != embargoType)
                        {
                            switch (embargoType)
                            {
                                case ETDEmbargoSetter.EMBARGO_NOT_AUBURN_STR:
                                    embargoTypeRadio.setOptionSelected("2");
                                    break;
                                case ETDEmbargoSetter.EMBARGO_GLOBAL_STR:
                                    embargoTypeRadio.setOptionSelected("3");
                                    break;
                            }

                            java.util.List<ResourcePolicy> dsoRPList = AuthorizeManager.getPolicies(context, dso);

                            log.debug(LogManager.getHeader(context, "Viewing dso RP List", " Size = "+String.valueOf(dsoRPList.size())));

                            if(dsoRPList.size() > 0)
                            {
                                for(ResourcePolicy dsoRP : dsoRPList)
                                {
                                    if((dsoRP.getRpType() == null && dsoRP.getRpName() == null && dsoRP.getEndDate() != null) ||
                                      (dsoRP.getRpType() == null && dsoRP.getRpName().equals("Public_Read")))
                                    {
                                        rpEndDate = new DateTime(dsoRP.getEndDate());
                                    }
                                }
                            }
                        }

                        if(rpEndDate != null)
                        {
                            log.debug(LogManager.getHeader(context, "Viewing dso RP List", " List Item end Date = "+dft.print(rpEndDate)));
                            if(errors.contains(ETD_DATE_REQUIRED_ERROR_NAME))
                            {
                                embargoDateField.addError(ETD_DATE_REQUIRED_ERROR);
                                datefieldDisplayInput.setValue(1);
                            }
                            if(errors.contains(ETD_DATE_IN_PAST_ERROR_NAME))
                            {
                                embargoDateField.addError(ETD_DATE_IN_PAST_ERROR);
                                datefieldDisplayInput.setValue(1);
                            }

                            if(errors.contains(ETD_DATE_IS_CURRENT_ERROR_NAME))
                            {
                                embargoDateField.addError(ETD_DATE_IS_CURRENT_ERROR);
                                datefieldDisplayInput.setValue(1);
                            }
                            embargoDateField.setValue(dft.print(rpEndDate));
                        }
                        else
                        {
                            log.debug(LogManager.getHeader(context, "Viewing dso RP List", " First List Item end Date = NULL"));

                            if(errors.contains(ETD_DATE_REQUIRED_ERROR_NAME))
                            {
                                embargoDateField.addError(ETD_DATE_REQUIRED_ERROR);
                                datefieldDisplayInput.setValue(1);
                            }
                        }
                        break;
                    case ETDEmbargoSetter.NOT_EMBARGOED:
                        embargoTypeRadio.setOptionSelected("1");
                        break;
                }
            }
        }
    }
}
