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
import org.apache.commons.lang.time.DateFormatUtils;
import org.dspace.app.xmlui.aspect.submission.AbstractStep;
import static org.dspace.app.xmlui.aspect.submission.submit.AccessStep.AURORA_EMBARGO_LENGTH_FIELD_HELP;
import static org.dspace.app.xmlui.aspect.submission.submit.AccessStep.AURORA_EMBARGO_LENGTH_FIELD_LABEL;
import static org.dspace.app.xmlui.aspect.submission.submit.AccessStep.AURORA_EMBARGO_LENGTH_FIELD_NAME;
import static org.dspace.app.xmlui.aspect.submission.submit.AccessStep.AURORA_EMBARGO_LENGTH_RADIO1;
import static org.dspace.app.xmlui.aspect.submission.submit.AccessStep.AURORA_EMBARGO_LENGTH_RADIO1_VALUE;
import static org.dspace.app.xmlui.aspect.submission.submit.AccessStep.AURORA_EMBARGO_LENGTH_RADIO2;
import static org.dspace.app.xmlui.aspect.submission.submit.AccessStep.AURORA_EMBARGO_LENGTH_RADIO2_VALUE;
import static org.dspace.app.xmlui.aspect.submission.submit.AccessStep.AURORA_EMBARGO_LENGTH_RADIO3;
import static org.dspace.app.xmlui.aspect.submission.submit.AccessStep.AURORA_EMBARGO_LENGTH_RADIO3_VALUE;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.*;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;


public class EditPolicyStep extends AbstractStep
{
    /** Language Strings **/
    protected static final Message T_head =message("xmlui.Submission.submit.EditPolicyStep.head");
    protected static final Message T_submit_save = message("xmlui.general.save");
    protected static final Message T_submit_cancel =message("xmlui.general.cancel");

    private ResourcePolicy resourcePolicy;
    private Bitstream bitstream;


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
        this.resourcePolicy = (ResourcePolicy) submissionInfo.get(org.dspace.submit.step.AccessStep.SUB_INFO_SELECTED_RP);
        //this.bitstream=submissionInfo.getBitstream();
    }

  
    public void addBody(Body body) 
        throws SAXException, WingException, UIException, 
                SQLException, IOException, AuthorizeException
    {
        Collection collection = submission.getCollection();
		String actionURL = contextPath + "/handle/"+collection.getHandle() + "/submit/" + knot.getId() + ".continue";
        Request request = ObjectModelHelper.getRequest(objectModel);

        Division div = body.addInteractiveDivision("submit-edit-policy", actionURL, Division.METHOD_POST, "primary submission");
        div.setHead(T_submission_head);
        addSubmissionProgressList(div);

        List edit = div.addList("submit-edit-file", List.TYPE_FORM);
        edit.setHead(T_head);

        div.addHidden("policy_id").setValue(resourcePolicy.getID());

        AccessStepUtil asu = new AccessStepUtil(context);

        // Builds a group of three radio button input fields.
        Radio embargoLengthRadio = edit.addItem().addRadio(AURORA_EMBARGO_LENGTH_FIELD_NAME);
        embargoLengthRadio.setLabel(AURORA_EMBARGO_LENGTH_FIELD_LABEL);
        embargoLengthRadio.setHelp(AURORA_EMBARGO_LENGTH_FIELD_HELP);
        embargoLengthRadio.addOption(AURORA_EMBARGO_LENGTH_RADIO1_VALUE, AURORA_EMBARGO_LENGTH_RADIO1);
        embargoLengthRadio.addOption(AURORA_EMBARGO_LENGTH_RADIO2_VALUE, AURORA_EMBARGO_LENGTH_RADIO2);
        embargoLengthRadio.addOption(AURORA_EMBARGO_LENGTH_RADIO3_VALUE, AURORA_EMBARGO_LENGTH_RADIO3);

        /**
         * If the submissionInfo object contains a key/value pair called AURORA_EMBARGO_LENGTH_FIELD_NAME
         * and its value is not NULL then set the 'selected' property of the radio input field whose value
         * property corresponds to the value of the key.
         */
        if(asu.getEmbargoLengthNYears(resourcePolicy) > 0)
        {
            embargoLengthRadio.setOptionSelected(String.valueOf(asu.getEmbargoLengthNYears(resourcePolicy)));
        }

        // Note, not standard control actions, this page just goes back to the upload step.
        org.dspace.app.xmlui.wing.element.Item actions = edit.addItem();
        actions.addButton(org.dspace.submit.step.AccessStep.FORM_EDIT_BUTTON_SAVE).setValue(T_submit_save);
        actions.addButton(org.dspace.submit.step.AccessStep.FORM_EDIT_BUTTON_CANCEL).setValue(T_submit_cancel);
    }
}
