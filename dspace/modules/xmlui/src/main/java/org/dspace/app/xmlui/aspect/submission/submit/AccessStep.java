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
import org.apache.log4j.Logger;
import org.dspace.app.xmlui.aspect.submission.AbstractSubmissionStep;
import org.dspace.app.xmlui.aspect.submission.submit.AccessStepUtil;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.LogManager;
import org.dspace.submit.AbstractProcessingStep;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public class AccessStep extends AbstractSubmissionStep
{
    private static final Logger log = Logger.getLogger(LicenseStep.class);

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
    
    /** Custom Class Properties */
    // Custom Field names and Messages
    public static final String AURORA_EMBARGO_LENGTH_FIELD_NAME = "embargo_length_radio";
    public static final Message AURORA_EMBARGO_LENGTH_FIELD_LABEL = message("xmlui.Submission.submit.AccessStep.embargo_length_field_label");
    public static final Message AURORA_EMBARGO_LENGTH_FIELD_HELP = message("xmlui.Submission.submit.AccessStep.embargo_length_field_help");
    public static final Message AURORA_EMBARGO_LENGTH_RADIO1 = message("xmlui.Submission.submit.AccessStep.embargo_length_radio_button1_text");
    public static final Message AURORA_EMBARGO_LENGTH_RADIO2 = message("xmlui.Submission.submit.AccessStep.embargo_length_radio_button2_text");
    public static final Message AURORA_EMBARGO_LENGTH_RADIO3 = message("xmlui.Submission.submit.AccessStep.embargo_length_radio_button3_text");
    public static final Message AURORA_EDIT_POLICIES_BUTTON_NAME = message("xmlui.Submission.submit.AccessStep.aurora_edit_policies_button");
    public static final Message AURORA_REMOVE_POLICIES_BUTTON_NAME = message("xmlui.Submission.submit.AccessStep.aurora_remove_policies_button");
    public static final Message AURORA_ADD_POLICY_BUTTON_NAME = message("xmlui.Submission.submit.AccessStep.aurora_add_policy_button");
    public static final String AURORA_EMBARGO_LENGTH_RADIO1_VALUE = "1";
    public static final String AURORA_EMBARGO_LENGTH_RADIO2_VALUE = "2";
    public static final String AURORA_EMBARGO_LENGTH_RADIO3_VALUE = "3";

    // Internal request info attribute name
    public static final String AURORA_SHOW_EMBARGO_SUMMARY = "print_embargo_summary";

    // Embargo Summary Info
    // Table Columns
    public static final Message EMBARGO_INFO_TABLE_COLUMN_STATUS = message("xmlui.Submission.submit.AccessStep.item_access_status_column");
    public static final Message EMBARGO_INFO_TABLE_COLUMN_LENGTH = message("xmlui.Submission.submit.AccessStep.embargo_length_column");

    // Status Messages
    public static final Message EMBARGO_STATUS_EMBARGOED = message("xmlui.Submission.submit.AccessStep.embargo_status_embargoed");
    public static final Message EMBARGO_STATUS_NOT_EMBARGOED = message("xmlui.Submission.submit.AccessStep.embargo_status_not_embargoed");

    /** End Custom Class Properties */

    
    /**
     * Establish our required parameters, abstractStep will enforce these.
     */
    public AccessStep()
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
        if(this.errorFlag==org.dspace.submit.step.AccessStep.STATUS_EDIT_POLICY || 
            this.errorFlag==org.dspace.submit.step.AccessStep.EDIT_POLICY_STATUS_DUPLICATED_POLICY)
        {
            this.editPolicy = new EditPolicyStep();
            this.editPolicy.setup(resolver, objectModel, src, parameters);
        }
    }

    public void addPageMeta(PageMeta pageMeta) 
        throws WingException, SAXException, SQLException, 
            AuthorizeException, IOException 
    {
        super.addPageMeta(pageMeta);
        pageMeta.addMetadata("javascript", "static").addContent("static/js/accessFormUtil.js");
    }


    public void addBody(Body body) 
        throws SAXException, WingException, UIException, 
                SQLException, IOException, AuthorizeException
    {
        // If we are actually editing information of an uploaded file,
        // then display that body instead!
        if(this.editPolicy!=null)
        {
            editPolicy.addBody(body);
            return;
        }

        // Get our parameters and state
        Item item = submission.getItem();
        Collection collection = submission.getCollection();
        Request request = ObjectModelHelper.getRequest(objectModel);
        Division div = addMainDivision(body, collection);
        List form = null;

        log.debug(LogManager.getHeader(context, "Submission Data Map", " Data List = "+submissionInfo.toString()));

        java.util.List<ResourcePolicy> itemCustomRPList = AuthorizeManager.findPoliciesByDSOAndType(context, item, ResourcePolicy.TYPE_CUSTOM);

        if(!itemCustomRPList.isEmpty())
        {
            form = div.addList("submit-access-settings-summary", List.TYPE_FORM);
            form.setHead(T_head);

            printEmbargoSummary(form, item);

            form = div.addList("submit-access-settings-new-part2", List.TYPE_FORM);
            
            // add standard control/paging buttons
            addControlButtons(form);
        }
        else
        {
            form = div.addList("submit-access-settings-new", List.TYPE_FORM);
            form.setHead(T_head);

            Radio embargoLengthRadio = form.addItem().addRadio(AURORA_EMBARGO_LENGTH_FIELD_NAME);
            embargoLengthRadio.setLabel(AURORA_EMBARGO_LENGTH_FIELD_LABEL);
            embargoLengthRadio.setHelp(AURORA_EMBARGO_LENGTH_FIELD_HELP);
            embargoLengthRadio.addOption(AURORA_EMBARGO_LENGTH_RADIO1_VALUE, AURORA_EMBARGO_LENGTH_RADIO1);
            embargoLengthRadio.addOption(AURORA_EMBARGO_LENGTH_RADIO2_VALUE, AURORA_EMBARGO_LENGTH_RADIO2);
            embargoLengthRadio.addOption(AURORA_EMBARGO_LENGTH_RADIO3_VALUE, AURORA_EMBARGO_LENGTH_RADIO3);

            org.dspace.app.xmlui.wing.element.Item addRestrictionButton = form.addItem();
            addRestrictionButton.addButton(org.dspace.submit.step.AccessStep.FORM_ACCESS_BUTTON_ADD).setValue(AURORA_ADD_POLICY_BUTTON_NAME);

            // add standard control/paging buttons
            addControlButtons(form);
        }
    }

    private void addPrivateCheckBox(Request request, List form, Item item) 
        throws WingException 
    {
        CheckBox privateCheckbox = form.addItem().addCheckBox("private_option");
        privateCheckbox.setLabel(T_private_settings);
        privateCheckbox.setHelp(T_private_settings_help);
        if(request.getParameter("private_option")!=null || !item.isDiscoverable())
        {
            privateCheckbox.addOption(true, CHECKBOX_PRIVATE_ITEM, T_private_label);
        }
        else
        {
            privateCheckbox.addOption(false, CHECKBOX_PRIVATE_ITEM, T_private_label);
        }
    }

    private Division addMainDivision(Body body, Collection collection) 
        throws WingException
    {
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
    public List addReviewSection(List reviewList) 
        throws SAXException, WingException, UIException, 
            SQLException, IOException, AuthorizeException
    {
        AccessStepUtil asu = new AccessStepUtil(context);
        ResourcePolicy resourcePolicy = null;
        Item item = submission.getItem();

        List accessSection = reviewList.addList("submit-review-" + this.stepAndPage, List.TYPE_FORM);
	    accessSection.setHead(T_head);

        for(ResourcePolicy customRP : AuthorizeManager.findPoliciesByDSOAndType(context, item, ResourcePolicy.TYPE_CUSTOM))
        {
            if(customRP.getStartDate() != null)
            {
                resourcePolicy = customRP;
                break;
            }
        }

        accessSection.addLabel(EMBARGO_INFO_TABLE_COLUMN_STATUS);

        if(resourcePolicy != null)
        {
            if (asu.getEmbargoLengthNYears(resourcePolicy) > 0) {
                accessSection.addItem(EMBARGO_STATUS_EMBARGOED);
                accessSection.addLabel(EMBARGO_INFO_TABLE_COLUMN_LENGTH);

                switch(String.valueOf(asu.getEmbargoLengthNYears(resourcePolicy)))
                {
                    case AURORA_EMBARGO_LENGTH_RADIO1_VALUE:
                        accessSection.addItem().addContent(AURORA_EMBARGO_LENGTH_RADIO1);
                        break;
                    case AURORA_EMBARGO_LENGTH_RADIO2_VALUE:
                        accessSection.addItem().addContent(AURORA_EMBARGO_LENGTH_RADIO2);
                        break;
                    case AURORA_EMBARGO_LENGTH_RADIO3_VALUE:
                        accessSection.addItem().addContent(AURORA_EMBARGO_LENGTH_RADIO3);
                        break;
                }
            }
            else
            {
                accessSection.addItem(EMBARGO_STATUS_NOT_EMBARGOED);
            }
        }
        else
        {
            accessSection.addItem(EMBARGO_STATUS_NOT_EMBARGOED);
        }

        return accessSection;
    }

    private void printEmbargoSummary(List list, Item item)
        throws SQLException, IOException, AuthorizeException,
                WingException
    {
        int policyID = 0;
        AccessStepUtil asu = new AccessStepUtil(context);
        ResourcePolicy resourcePolicy = null;

        for(ResourcePolicy customRP : AuthorizeManager.findPoliciesByDSOAndType(context, item, ResourcePolicy.TYPE_CUSTOM))
        {
            if(customRP.getStartDate() != null)
            {
                //liftdate = new DateTime(rp.getStartDate());
                policyID = customRP.getID();
                resourcePolicy = customRP;
                break;
            }
        }

        // Embargo Status Information
        if(asu.getEmbargoLengthNYears(resourcePolicy) > 0)
        {
            list.addLabel(EMBARGO_INFO_TABLE_COLUMN_STATUS);
            list.addItem(EMBARGO_STATUS_EMBARGOED);
            list.addLabel(EMBARGO_INFO_TABLE_COLUMN_LENGTH);

            switch(String.valueOf(asu.getEmbargoLengthNYears(resourcePolicy)))
            {
                case AURORA_EMBARGO_LENGTH_RADIO1_VALUE:
                    list.addItem().addContent(AURORA_EMBARGO_LENGTH_RADIO1);
                    break;
                case AURORA_EMBARGO_LENGTH_RADIO2_VALUE:
                    list.addItem().addContent(AURORA_EMBARGO_LENGTH_RADIO2);
                    break;
                case AURORA_EMBARGO_LENGTH_RADIO3_VALUE:
                    list.addItem().addContent(AURORA_EMBARGO_LENGTH_RADIO3);
                    break;
            }
        }

        // Only print the edit and delete buttons if policyID is given
        if(policyID > 0)
        {
            org.dspace.app.xmlui.wing.element.Item actions = list.addItem();
            actions.addButton("submit_edit_edit_policies_"+String.valueOf(policyID)).setValue(AURORA_EDIT_POLICIES_BUTTON_NAME);
            actions.addButton("submit_delete_edit_policies_"+String.valueOf(policyID)).setValue(AURORA_REMOVE_POLICIES_BUTTON_NAME);
        }
    }
}
