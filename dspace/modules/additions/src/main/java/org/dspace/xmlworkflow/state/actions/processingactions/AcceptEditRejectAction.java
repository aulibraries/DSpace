/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xmlworkflow.state.actions.processingaction;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCDate;
import org.dspace.content.MetadataSchema;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.services.ConfigurationService;
import org.dspace.xmlworkflow.factory.XmlWorkflowServiceFactory;
import org.dspace.xmlworkflow.state.Step;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;
import org.dspace.xmlworkflow.state.actions.ActionResult;

import javax.servlet.http.HttpServletRequest;
import java.nio.file.*;
import java.io.InputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Processing class of an action that allows users to
 * edit/accept/reject a workflow item
 *
 * @author Bram De Schouwer (bram.deschouwer at dot com)
 * @author Kevin Van de Velde (kevin at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 */
public class AcceptEditRejectAction extends ProcessingAction {

    public static final int MAIN_PAGE = 0;
    public static final int REJECT_PAGE = 1;

    private static final Logger log = Logger.getLogger(AcceptEditRejectAction.class);

    protected ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
    protected ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    
    protected static final String AUETD_REJECTED_FILE_FIELD_NAME = "rejected-file";

    //TODO: rename to AcceptAndEditMetadataAction

    @Override
    public void activate(Context c, XmlWorkflowItem wf) throws SQLException {

    }

    @Override
    public ActionResult execute(Context c, XmlWorkflowItem wfi, Step step, HttpServletRequest request) throws SQLException, AuthorizeException, IOException {
        int page = Util.getIntParameter(request, "page");

        switch (page){
            case MAIN_PAGE:
                return processMainPage(c, wfi, step, request);
            case REJECT_PAGE:
                return processRejectPage(c, wfi, step, request);

        }

        return new ActionResult(ActionResult.TYPE.TYPE_CANCEL);
    }
    
    public ActionResult processMainPage(Context c, XmlWorkflowItem wfi, Step step, HttpServletRequest request) throws SQLException, AuthorizeException {
        if(request.getParameter("submit_approve") != null){
            //Delete the tasks
            addApprovedProvenance(c, wfi);

            return new ActionResult(ActionResult.TYPE.TYPE_OUTCOME, ActionResult.OUTCOME_COMPLETE);
        } else if(request.getParameter("submit_reject") != null){
            // Make sure we indicate which page we want to process
            request.setAttribute("page", REJECT_PAGE);
            // We have pressed reject item, so take the user to a page where he can reject
            return new ActionResult(ActionResult.TYPE.TYPE_PAGE);
        } else {
            //We pressed the leave button so return to our submissions page
            return new ActionResult(ActionResult.TYPE.TYPE_SUBMISSION_PAGE);
        }
    }

    public ActionResult processRejectPage(Context c, XmlWorkflowItem wfi, Step step, HttpServletRequest request) throws SQLException, AuthorizeException, IOException {
        if(request.getParameter("submit_reject") != null){
            String reason = request.getParameter("reason");
            if(reason == null || 0 == reason.trim().length()){
                addErrorField(request, "reason");
                request.setAttribute("page", REJECT_PAGE);
                return new ActionResult(ActionResult.TYPE.TYPE_ERROR);
            }

            String rejectedFilePath = createRejectionTempFile(c, request);
        
            // If content was returned and the content equals 'rejection
            if (StringUtils.isNotBlank(rejectedFilePath)) {
                log.info(LogManager.getHeader(c, "rejected_file_upload", " Rejected file path = "+rejectedFilePath));
                if (rejectedFilePath.equals("invalid-reject-file")) {
                    log.error(LogManager.getHeader(c, "rejected_file_upload ", " The rejection file field returned an error."));
                    addErrorField(request, AUETD_REJECTED_FILE_FIELD_NAME);
                    log.error(LogManager.getHeader(c, "process_reject_page ", " Value of action type error = "+String.valueOf(ActionResult.TYPE.TYPE_ERROR)));
                    request.setAttribute("page", REJECT_PAGE);
                    return new ActionResult(ActionResult.TYPE.TYPE_ERROR);
                } else {
                    XmlWorkflowServiceFactory.getInstance().getXmlWorkflowService().sendWorkflowItemBackSubmissionWithRejectionFile(c, wfi, c.getCurrentUser(), this.getProvenanceStartId(), reason, rejectedFilePath);
                }
            } else {
                XmlWorkflowServiceFactory.getInstance().getXmlWorkflowService().sendWorkflowItemBackSubmission(c, wfi, c.getCurrentUser(), this.getProvenanceStartId(), reason);
            }

            return new ActionResult(ActionResult.TYPE.TYPE_SUBMISSION_PAGE);
        }else{
            //Cancel, go back to the main task page
            request.setAttribute("page", MAIN_PAGE);

            return new ActionResult(ActionResult.TYPE.TYPE_PAGE);
        }
    }

    private void addApprovedProvenance(Context c, XmlWorkflowItem wfi) throws SQLException, AuthorizeException {
        //Add the provenance for the accept
        String now = DCDate.getCurrent().toString();

        // Get user's name + email address
        String usersName = XmlWorkflowServiceFactory.getInstance().getXmlWorkflowService().getEPersonName(c.getCurrentUser());

        String provDescription = getProvenanceStartId() + " Approved for entry into archive by "
                + usersName + " on " + now + " (GMT) ";

        // Add to item as a DC field
        itemService.addMetadata(c, wfi.getItem(), MetadataSchema.DC_SCHEMA, "description", "provenance", "en", provDescription);
        itemService.update(c, wfi.getItem());
    }

    private String createRejectionTempFile(Context context, HttpServletRequest request)
        throws IOException
    {
        Path filePath = null;
        Path newFilePath = null;
        @SuppressWarnings("unchecked")
        Enumeration<String> attNames = request.getAttributeNames();
        ArrayList<String> attNamesList = Collections.list(attNames);

        //loop through our request attributes
        for(String attName : attNamesList) {
            //log.info(LogManager.getHeader(context, "reject_file_upload_request", " Attribute Name "+attName+" = "+String.valueOf(request.getAttribute(attName))));

            if (attName.endsWith("-path")) {
                // Strip off the -path portion of the attribute's name
                // to get the actual name of the uploaded file.
                String param = attName.replace("-path", "");

                log.info(LogManager.getHeader(context, "reject_file_upload_request", " Parameter Name = "+param));

                if (StringUtils.isNotBlank((String) request.getAttribute(param + "-path"))) {
                    // Load the file's path and input stream and description
                    filePath = Paths.get((String) request.getAttribute(param + "-path"));
                }

                log.info(LogManager.getHeader(context, "reject_file_upload_request", " Is File Path null "+String.valueOf(filePath == null)));

                if (StringUtils.isNotBlank(filePath.toString())) {
                    log.info(LogManager.getHeader(context, "reject_file_upload_request", " File Path = "+filePath.toString()));
                    
                    if (!isAuthorizedFile(context, filePath.getFileName().toString())) {
                        return "invalid-reject-file";
                    }
                    
                    newFilePath = Paths.get(configurationService.getProperty("upload.temp.dir")+"/"+filePath.getFileName().toString());
                    log.info(LogManager.getHeader(context, "reject_file_upload_request", " New File Path = "+newFilePath.toString()));

                    Files.copy((InputStream) request.getAttribute(param + "-inputstream"), newFilePath, StandardCopyOption.REPLACE_EXISTING);

                    //log.debug(LogManager.getHeader(context, "reject_file_upload_request", " New file exists = "+String.valueOf(Files.exists(newFilePath, LinkOption.NOFOLLOW_LINKS))));

                    return newFilePath.toString();
                }
            }     
        }
        return null;
    }

    private boolean isAuthorizedFile(Context context, String fileName) throws IOException
    {
        ArrayList<String> acceptableExtensionsList = new ArrayList<>();

        acceptableExtensionsList.add("pdf");
        acceptableExtensionsList.add("doc");
        acceptableExtensionsList.add("docx");
        acceptableExtensionsList.add("txt");

        log.info(LogManager.getHeader(context, "reject_file_upload_request", " File Name = "+String.valueOf(fileName)));
        
        if (StringUtils.isNotBlank(fileName)) {
            String newFilename = null;
            String[] fna = fileName.split("\\s");
            newFilename = fna[0];

            for(int i=1; i < fna.length; i++) {
                String s = fna[i];

                if(s.length() > 0) {
                    newFilename = newFilename+"-"+s;
                }
            }

            int lastDot = newFilename.lastIndexOf(".");
            String extension = null;

            log.info(LogManager.getHeader(context, "reject_file_upload_request", " Index of last dot = "+String.valueOf(lastDot)));
            
            if (lastDot != -1) {
                extension = newFilename.substring(lastDot + 1);
            }

            log.info(LogManager.getHeader(context, "reject_file_upload_request", " File Extension = "+String.valueOf(extension)));

            if(StringUtils.isNotBlank(extension)) {                
                if(!acceptableExtensionsList.contains(extension)) {
                    return false;
                }
            }
        }
        return true;
    }
}
