/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.xmlworkflow.actions.processingaction;

import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.xmlui.aspect.xmlworkflow.AbstractXMLUIAction;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.xmlworkflow.state.actions.Action;
import org.dspace.xmlworkflow.state.actions.processingaction.ReviewAction;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.sql.SQLException;

/**
 * User interface for an action that allows users to
 * edit/accept/reject a workflow item
 * 
 * @author Bram De Schouwer (bram.deschouwer at dot com)
 * @author Kevin Van de Velde (kevin at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 */
public class AcceptEditRejectAction extends AbstractXMLUIAction {

    protected static final Message T_info1=
        message("xmlui.XMLWorkflow.workflow.EditMetadataAction.info1");

    protected static final Message T_info2=
        message("xmlui.Submission.workflow.RejectTaskStep.info1");
    

    private static final Message T_HEAD = message("xmlui.XMLWorkflow.workflow.EditMetadataAction.head");

    protected static final Message T_approve_help =
        message("xmlui.XMLWorkflow.workflow.EditMetadataAction.approve_help");
    protected static final Message T_approve_submit =
        message("xmlui.XMLWorkflow.workflow.EditMetadataAction.approve_submit");
    protected static final Message T_reject_help =
        message("xmlui.XMLWorkflow.workflow.EditMetadataAction.reject_help");
    protected static final Message T_reject_submit =
        message("xmlui.XMLWorkflow.workflow.EditMetadataAction.reject_submit");

    protected static final Message T_edit_help =
        message("xmlui.XMLWorkflow.workflow.EditMetadataAction.edit_help");
    protected static final Message T_edit_submit =
        message("xmlui.XMLWorkflow.workflow.EditMetadataAction.edit_submit");

    /** Reject page messages **/
    protected static final Message T_reason =
        message("xmlui.Submission.workflow.RejectTaskStep.reason");
    protected static final Message T_submit_reject =
        message("xmlui.Submission.workflow.RejectTaskStep.submit_reject");
    protected static final Message T_reason_required =
        message("xmlui.Submission.workflow.RejectTaskStep.reason_required");

    protected static final Message T_submit_cancel =
        message("xmlui.general.cancel");

    protected static final Message T_workflow_head =
        message("xmlui.Submission.general.workflow.head");
    protected static final Message T_cancel_submit =
        message("xmlui.general.cancel");

    //Custom constants
    protected static final Message AUETD_REJECTED_FILE_FIELD_LABEL =
        message("xmlui.Submission.workflow.RejectTaskStep.AUETD_reject_file_upload_field_label");
    protected static final Message AUETD_REJECTED_FILE_FIELD_HELP =
        message("xmlui.Submission.workflow.RejectTaskStep.AUETD_reject_file_upload_field_help");
    protected static final Message AUETD_REJECTED_FILE_FIELD_ERROR =
        message("xmlui.Submission.workflow.RejectTaskStep.AUETD_reject_file_upload_field_error");
    protected static final String AUETD_REJECTED_FILE_FIELD_NAME = "rejected-file";


    @Override
    public void addBody(Body body) throws SAXException, WingException, SQLException, IOException, AuthorizeException {
        Item item = workflowItem.getItem();
        Collection collection = workflowItem.getCollection();
        Request request = ObjectModelHelper.getRequest(objectModel);

        String actionURL = contextPath + "/handle/"+collection.getHandle() + "/xmlworkflow";

        //Retrieve our pagenumber
        int page = ReviewAction.MAIN_PAGE;
        if(request.getAttribute("page") != null){
            page = Integer.parseInt(request.getAttribute("page").toString());
        }

        // Generate a from asking the user two questions: multiple
        // titles & published before.
    	Division div = body.addInteractiveDivision("perform-task", actionURL, Division.METHOD_MULTIPART, "primary workflow");
        div.setHead(T_HEAD);

        addWorkflowItemInformation(div, item, request);

        switch (page){
            case org.dspace.xmlworkflow.state.actions.processingaction.AcceptEditRejectAction.MAIN_PAGE:
                renderMainPage(div);
                break;
            case ReviewAction.REJECT_PAGE:
                renderRejectPage(div);
                break;
        }

        div.addHidden("submission-continue").setValue(knot.getId());
    }

    private void renderMainPage(Division div) throws WingException {
        Table table = div.addTable("workflow-actions", 1, 1);
        table.setHead(T_info1);

        // Approve task
        Row row = table.addRow();
        row.addCellContent(T_approve_help);
        row.addCell().addButton("submit_approve").setValue(T_approve_submit);

        // Reject item
        row = table.addRow();
        row.addCellContent(T_reject_help);
        row.addCell().addButton("submit_reject").setValue(T_reject_submit);


        // Edit metadata
        row = table.addRow();
        row.addCellContent(T_edit_help);
        row.addCell().addButton("submit_edit").setValue(T_edit_submit);


        // Everyone can just cancel
        row = table.addRow();
        row.addCell(0, 2).addButton("submit_leave").setValue(T_cancel_submit);

        div.addHidden("page").setValue(ReviewAction.MAIN_PAGE);
    }

    private void renderRejectPage(Division div) throws WingException {
        Request request = ObjectModelHelper.getRequest(objectModel);

        List form = div.addList("reject-workflow",List.TYPE_FORM);

        form.addItem(T_info2);

        TextArea reason = form.addItem().addTextArea("reason");
        reason.setLabel(T_reason);
        reason.setRequired();
        reason.setSize(15, 50);

        if (Action.getErrorFields(request).contains("reason")) {
            reason.addError(T_reason_required);
        }

        File rejectedFileField = form.addItem().addFile(AUETD_REJECTED_FILE_FIELD_NAME);
        rejectedFileField.setLabel(AUETD_REJECTED_FILE_FIELD_LABEL);
        rejectedFileField.setHelp(AUETD_REJECTED_FILE_FIELD_HELP);

        if (Action.getErrorFields(request).contains("invalid-reject-file")) {
            if (StringUtils.isNotBlank(request.getParameter("reason"))) {
                reason.setValue(request.getParameter("reason"));
            }
            rejectedFileField.addError(AUETD_REJECTED_FILE_FIELD_ERROR);
        }

        div.addHidden("page").setValue(ReviewAction.REJECT_PAGE);

        org.dspace.app.xmlui.wing.element.Item actions = form.addItem();
        actions.addButton("submit_reject").setValue(T_submit_reject);
        actions.addButton("submit_cancel").setValue(T_submit_cancel);
    }
}
