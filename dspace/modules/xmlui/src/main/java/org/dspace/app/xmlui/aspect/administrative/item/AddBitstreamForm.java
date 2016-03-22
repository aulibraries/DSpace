/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.xmlui.aspect.administrative.item;

// Java Class imports
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Button;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.File;
import org.dspace.app.xmlui.wing.element.Hidden;
import org.dspace.app.xmlui.wing.element.Item;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.app.xmlui.wing.element.PageMeta;
import org.dspace.app.xmlui.wing.element.Radio;
import org.dspace.app.xmlui.wing.element.Select;
import org.dspace.app.xmlui.wing.element.Text;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.Bundle;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;

import org.xml.sax.SAXException;

/**
 *
 * Show a form that allows the user to upload a new bitstream. The
 * user can select the new bitstream's bundle (which is unchangable
 * after upload) and a description for the file.
 *
 * @author Scott Phillips
 */
public class AddBitstreamForm extends AbstractDSpaceTransformer
{
	/** Language strings */
	private static final Message T_dspace_home = message("xmlui.general.dspace_home");
	private static final Message T_submit_cancel = message("xmlui.general.cancel");
	private static final Message T_item_trail = message("xmlui.administrative.item.general.item_trail");

	private static final Message T_title = message("xmlui.administrative.item.AddBitstreamForm.title");
	private static final Message T_trail = message("xmlui.administrative.item.AddBitstreamForm.trail");
	private static final Message T_head1 = message("xmlui.administrative.item.AddBitstreamForm.head1");
	private static final Message T_bundle_label = message("xmlui.administrative.item.AddBitstreamForm.bundle_label");
	private static final Message T_file_label = message("xmlui.administrative.item.AddBitstreamForm.file_label");
	private static final Message T_file_help = message("xmlui.administrative.item.AddBitstreamForm.file_help");
	private static final Message T_description_label = message("xmlui.administrative.item.AddBitstreamForm.description_label");
	private static final Message T_description_help = message("xmlui.administrative.item.AddBitstreamForm.description_help");
	private static final Message T_submit_upload = message("xmlui.administrative.item.AddBitstreamForm.submit_upload");

	private static final Message T_no_bundles = message("xmlui.administrative.item.AddBitstreamForm.no_bundles");

	private static final String DEFAULT_BUNDLE_LIST = "ORIGINAL, METADATA, THUMBNAIL, LICENSE, CC-LICENSE";

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

    public static final int STATUS_ERROR_DATE_IN_PAST = 35;
    public static final int STATUS_ERROR_EMBARGO_CREATION_REQUIRED = 36;
    public static final int STATUS_ERROR_DATE_IS_CURRENT = 37;

    // End Custom Constant Section

    /**
     *
     * @param pageMeta
     * @throws WingException
     */
    @Override
	public void addPageMeta(PageMeta pageMeta) throws WingException
	{
        pageMeta.addMetadata("title").addContent(T_title);

        pageMeta.addTrailLink(contextPath + "/", T_dspace_home);
        pageMeta.addTrailLink(contextPath + "/admin/item", T_item_trail);
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
        throws SAXException, WingException, UIException,
        SQLException, IOException, AuthorizeException
	{
        isAdvancedFormEnabled=ConfigurationManager.getBooleanProperty("webui.submission.restrictstep.enableAdvancedForm", false);

        int itemID = parameters.getParameterAsInteger("itemID", -1);
        org.dspace.content.Item item = org.dspace.content.Item.find(context, itemID);

        // DIVISION: main div
        Division div = body.addInteractiveDivision("add-bitstream", contextPath + "/admin/item", Division.METHOD_MULTIPART, "primary administrative item");

        // LIST: upload form
        List upload = div.addList("submit-upload-new", List.TYPE_FORM);
        upload.setHead(T_head1);

        int bundleCount = 0; // record how many bundles we are able to upload too.
        Select select = upload.addItem().addSelect("bundle");
        select.setLabel(T_bundle_label);

        // Get the list of bundles to allow the user to upload too. Either use the default
        // or one supplied from the dspace.cfg.
        String bundleString = ConfigurationManager.getProperty("xmlui.bundle.upload");
        if (bundleString == null || bundleString.length() == 0)
        {
            bundleString = DEFAULT_BUNDLE_LIST;
        }
        String[] parts = bundleString.split(",");
        for (String part : parts)
        {
            if (addBundleOption(item, select, part.trim()))
            {
                bundleCount++;
            }
        }
        select.setOptionSelected("ORIGINAL");

        if (bundleCount == 0) {
            select.setDisabled();
        }

        File file = upload.addItem().addFile("file");
        file.setLabel(T_file_label);
        file.setHelp(T_file_help);
        file.setRequired();

        if (bundleCount == 0)
        {
            file.setDisabled();
        }

        if (bundleCount == 0)
        {
            upload.addItem().addContent(T_no_bundles);
        }

        // EMBARGO FIELD
        addEmbargoFieldSection(upload);

        // ITEM: actions
        Item actions = upload.addItem();
        Button button = actions.addButton("submit_upload");
        button.setValue(T_submit_upload);
        if (bundleCount == 0) {
            button.setDisabled();
        }

        actions.addButton("submit_cancel").setValue(T_submit_cancel);

        div.addHidden("administrative-continue").setValue(knot.getId());
    }

    /**
     * Add the bundleName to the list of bundles available to submit to.
     * Performs an authorization check that the current user has privileges
     *
     * @param item DSO item being evaluated
     * @param select DRI wing select box that is being added to
     * @param bundleName
     * @return boolean indicating whether user can upload to bundle
     * @throws java.sql.SQLException
     * @throws org.dspace.app.xmlui.wing.WingException
     */
    public boolean addBundleOption(org.dspace.content.Item item, Select select, String bundleName)
        throws SQLException, WingException
	{
        Bundle[] bundles = item.getBundles(bundleName);
        if (bundles == null || bundles.length == 0)
        {
            // No bundle, so the user has to be authorized to add to item.
            if(!AuthorizeManager.authorizeActionBoolean(context, item, Constants.ADD))
            {
                return false;
            }
        }
        else
        {
            // At least one bundle exists, does the user have privileges to upload to it?
            Bundle bundle = bundles[0];
            if (!AuthorizeManager.authorizeActionBoolean(context, bundle, Constants.ADD))
            {
                return false; // you can't upload to this bundle.
            }

            // You also need the write privlege on the bundle.
            if (!AuthorizeManager.authorizeActionBoolean(context, bundle, Constants.WRITE))
            {
                return false;  // you can't upload
            }
        }

        // It's okay to upload.
        select.addOption(bundleName, message("xmlui.administrative.item.AddBitstreamForm.bundle." + bundleName));
        return true;
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
     */
    private void addEmbargoFieldSection(List form)
            throws SQLException, WingException
    {
        String errorString = parameters.getParameter("errors",null);
        ArrayList<String> errors = new ArrayList<>();
        if (errorString != null)
        {
            errors.addAll(Arrays.asList(errorString.split(",")));
        }

        Hidden datefieldDisplayInput = form.addItem().addHidden("datefieldDisplay");
        datefieldDisplayInput.setValue(0);

        //Embargo Question
        Radio embargoTypeRadio = form.addItem().addRadio(ETD_CREATE_QUESTION_FIELD_NAME);
        embargoTypeRadio.setLabel(ETD_CREATE_QUESTION_LABEL);
        embargoTypeRadio.setRequired();
        embargoTypeRadio.addOption("1", EMBARGO_RADIO_BUTTON1);
        embargoTypeRadio.addOption("2", EMBARGO_RADIO_BUTTON2);
        embargoTypeRadio.addOption("3", EMBARGO_RADIO_BUTTON3);

        if(errors.contains(EMBARGO_CREATION_REQUIRED_ERROR_NAME))
        {
            embargoTypeRadio.addError(STATUS_ERROR_EMBARGO_CREATION_REQUIRED_ERROR);
        }

        // Date
        Text embargoDateField = form.addItem().addText(ETD_DATE_FIELD_NAME);
        embargoDateField.setLabel(ETD_DATE_LABEL);
        embargoDateField.setHelp(ETD_DATE_HELP);

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
    }

}
