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

import org.apache.commons.lang3.StringUtils;

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
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bundle;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.xml.sax.SAXException;

/**
 * 
 * Show a form that allows the user to upload a new bitstream. The 
 * user can select the new bitstream's bundle (which is unchangeable
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
	private static final String[] DEFAULT_BUNDLE_LIST = new String[]{"ORIGINAL", "METADATA", "THUMBNAIL", "LICENSE", "CC-LICENSE"};

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

    protected AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    protected ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    @Override
	public void addPageMeta(PageMeta pageMeta) throws WingException
	{
        pageMeta.addMetadata("title").addContent(T_title);

        pageMeta.addTrailLink(contextPath + "/", T_dspace_home);
        pageMeta.addTrailLink(contextPath + "/admin/item", T_item_trail);
        pageMeta.addTrail().addContent(T_trail);
        pageMeta.addMetadata("javascript", "static").addContent("static/js/editItemUtil.js");
    }

    @Override
	public void addBody(Body body) throws SAXException, WingException, UIException, SQLException, IOException, AuthorizeException
	{
        // DIVISION: main div
        Division div = body.addInteractiveDivision("add-bitstream", contextPath + "/admin/item", Division.METHOD_MULTIPART, "primary administrative item");

        // LIST: upload form
        List upload = div.addList("submit-upload-new", List.TYPE_FORM);
        upload.setHead(T_head1);

        File file = upload.addItem().addFile("file");
        file.setLabel(T_file_label);
        file.setHelp(T_file_help);
        file.setRequired();

        // EMBARGO FIELDS
        addEmbargoFieldSection(upload);

        // ITEM: actions
        Item actions = upload.addItem();
        Button button = actions.addButton("submit_upload");
        button.setValue(T_submit_upload);

        actions.addButton("submit_cancel").setValue(T_submit_cancel);

        div.addHidden("administrative-continue").setValue(knot.getId());
    }

	/**
     * Add the bundleName to the list of bundles available to submit to. 
     * Performs an authorization check that the current user has privileges 
     * @param item DSO item being evaluated
     * @param select DRI wing select box that is being added to
     * @param bundleName the new bundle name.
     * @return boolean indicating whether user can upload to bundle
     * @throws SQLException passed through.
     * @throws WingException passed through.
     */
    public boolean addBundleOption(org.dspace.content.Item item, Select select, String bundleName)
        throws SQLException, WingException
	{
        java.util.List<Bundle> bundles = itemService.getBundles(item, bundleName);
        if (bundles == null || bundles.size() == 0) {
            // No bundle, so the user has to be authorized to add to item.
            if(!authorizeService.authorizeActionBoolean(context, item, Constants.ADD)) {
                return false;
            }
        } else {
            // At least one bundle exists, does the user have privileges to upload to it?
            Bundle bundle = bundles.get(0);
            if (!authorizeService.authorizeActionBoolean(context, bundle, Constants.ADD)) {
                return false; // you can't upload to this bundle.
            }

            // You also need the write privlege on the bundle.
            if (!authorizeService.authorizeActionBoolean(context, bundle, Constants.WRITE)) {
                return false;  // you can't upload
            }
        }

        // It's okay to upload.
        select.addOption(bundleName, message("xmlui.administrative.item.AddBitstreamForm.bundle." + bundleName));
        return true;
    }

    private void addEmbargoFieldSection(List form) throws SQLException, WingException
    {
        String errorString = parameters.getParameter("errors", null);
        ArrayList<String> errors = new ArrayList<String>();
        if (StringUtils.isNotBlank(errorString)) {
            errors.addAll(Arrays.asList(errorString.split(",")));
        }

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

        if (errors.contains(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME)) {
            embargoTypeRadio.addError(AUETD_STATUS_ERROR_EMBARGO_CREATION_REQUIRED);
        }

        if (errors.contains(org.dspace.submit.step.UploadWithEmbargoStep.AUETD_EMBARGO_LENGTH_FIELD_NAME)) {
            embargoLengthFieldDisplayInput.setValue(1);
            embargoLengthField.addError(AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED);
        }
    }
}
