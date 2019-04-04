/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.workflow;

import org.apache.commons.lang3.StringUtils;
//import org.apache.cocoon.environment.Request;
import org.apache.log4j.Logger;
import org.dspace.app.util.*;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.submit.AbstractProcessingStep;
import org.dspace.workflowbasic.BasicWorkflowItem;
import org.dspace.workflowbasic.factory.BasicWorkflowServiceFactory;
import org.dspace.workflowbasic.service.BasicWorkflowItemService;
import org.dspace.workflowbasic.service.BasicWorkflowService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import java.nio.file.*;
import java.io.InputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;

/**
 * This is a utility class to aid in the workflow flow scripts.
 * Since data validation is cumbersome inside a flow script this
 * is a collection of methods to perform processing at each step
 * of the flow, the flow script will ties these operations
 * together in a meaningful order but all actually processing
 * is done through these various processes.
 *
 * @author Bram De Schouwer (bram.deschouwer at dot com)
 * @author Kevin Van de Velde (kevin at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 */

public class FlowUtils {

    protected static final BasicWorkflowService basicWorkflowService = BasicWorkflowServiceFactory.getInstance().getBasicWorkflowService();
    protected static final BasicWorkflowItemService basicWorkflowItemService = BasicWorkflowServiceFactory.getInstance().getBasicWorkflowItemService();
    protected static final HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    protected static final WorkspaceItemService workspaceItemService = ContentServiceFactory.getInstance().getWorkspaceItemService();
    protected static final ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    private static final Logger log = Logger.getLogger(FlowUtils.class);

   	/**
	 * Update the provided workflowItem to advance to the next workflow
	 * step. If this was the last thing needed before the item is
	 * committed to the repository then return true, otherwise false.
	 *
	 * @param context The current DSpace content
	 * @param id The unique ID of the current workflow
     * @return whether the workflow is completed.
     * @throws java.sql.SQLException passed through.
     * @throws org.dspace.app.xmlui.utils.UIException passed through.
     * @throws javax.servlet.ServletException passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException passed through.
	 */
	public static boolean processApproveTask(Context context, String id)
            throws SQLException, UIException, ServletException, AuthorizeException, IOException
	{
		BasicWorkflowItem workflowItem = findWorkflow(context, id);
		Item item = workflowItem.getItem();

		// Advance the item along the workflow
        basicWorkflowService.advance(context, workflowItem, context.getCurrentUser());

        // FIXME: This should be a return value from advance()
        // See if that gave the item a Handle. If it did,
        // the item made it into the archive, so we
        // should display a suitable page.
        String handle = handleService.findHandle(context, item);

        if (handle != null)
        {
            return true;
        }
        else
        {
            return false;
        }
	}

	/**
	 * Return the given task to the pool of unclaimed tasks for another user
	 * to select and perform.
	 *
	 * @param context The current DSpace content
	 * @param id The unique ID of the current workflow
     * @throws java.sql.SQLException passed through.
     * @throws org.dspace.app.xmlui.utils.UIException passed through.
     * @throws javax.servlet.ServletException passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException passed through.
	 */
	public static void processUnclaimTask(Context context, String id)
            throws SQLException, UIException, ServletException, AuthorizeException, IOException
	{
		BasicWorkflowItem workflowItem = findWorkflow(context, id);

        // Return task to pool
        basicWorkflowService.unclaim(context, workflowItem, context.getCurrentUser());

        // Log this unclaim action
        log.info(LogManager.getHeader(context, "unclaim_workflow",
                "workflow_item_id=" + workflowItem.getID() + ",item_id="
                        + workflowItem.getItem().getID() + ",collection_id="
                        + workflowItem.getCollection().getID()
                        + ",new_state=" + workflowItem.getState()));
	}

	/**
	 * Claim this task from the pool of unclaimed task so that this user may
	 * perform the task by either approving or rejecting it.
	 *
	 * @param context The current DSpace content
	 * @param id The unique ID of the current workflow
     * @throws java.sql.SQLException passed through.
     * @throws org.dspace.app.xmlui.utils.UIException passed through.
     * @throws javax.servlet.ServletException passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException passed through.
	 */
	public static void processClaimTask(Context context, String id)
            throws SQLException, UIException, ServletException, AuthorizeException, IOException
	{
		BasicWorkflowItem workflowItem = findWorkflow(context, id);
        if(workflowItem.getState() != BasicWorkflowService.WFSTATE_STEP1POOL &&
                workflowItem.getState() != BasicWorkflowService.WFSTATE_STEP2POOL &&
                workflowItem.getState() != BasicWorkflowService.WFSTATE_STEP3POOL){
            // Only allow tasks in the pool to be claimed !
            throw new AuthorizeException("Error while claiming task: this task has already been claimed !");
        }

       // Claim the task
       basicWorkflowService.claim(context, workflowItem, context.getCurrentUser());

       // log this claim information
       log.info(LogManager.getHeader(context, "claim_task", "workflow_item_id="
                   + workflowItem.getID() + "item_id=" + workflowItem.getItem().getID()
                   + "collection_id=" + workflowItem.getCollection().getID()
                   + "newowner_id=" + workflowItem.getOwner().getID()
                   + "new_state=" + workflowItem.getState()));
	}

    /**
     * Verifies if the currently logged in user has proper rights to perform the workflow task on the item
     * @param context the current dspace context
     * @param workflowItemId the identifier of the workflow item
     * @throws org.dspace.authorize.AuthorizeException thrown if the user doesn't have sufficient rights to perform the task at hand
     * @throws java.sql.SQLException is thrown when something is wrong with the database
     */
    public static void authorizeWorkflowItem(Context context, String workflowItemId) throws AuthorizeException, SQLException 
    {
        BasicWorkflowItem workflowItem = basicWorkflowItemService.find(context, Integer.parseInt(workflowItemId.substring(1)));
        if ((workflowItem.getState() == BasicWorkflowService.WFSTATE_STEP1 ||
                workflowItem.getState() == BasicWorkflowService.WFSTATE_STEP2 ||
                workflowItem.getState() == BasicWorkflowService.WFSTATE_STEP3) && workflowItem.getOwner().getID() != context.getCurrentUser().getID()) {
            throw new AuthorizeException("You are not allowed to perform this task.");
        } else
        if ((workflowItem.getState() == BasicWorkflowService.WFSTATE_STEP1POOL ||
                workflowItem.getState() == BasicWorkflowService.WFSTATE_STEP2POOL ||
                workflowItem.getState() == BasicWorkflowService.WFSTATE_STEP3POOL)) {
            // Verify if the current user has the current workflowItem among his pooled tasks
            boolean hasPooledTask = false;
            List<BasicWorkflowItem> pooledTasks = basicWorkflowService.getPooledTasks(context, context.getCurrentUser());
            for (BasicWorkflowItem pooledItem : pooledTasks) {
                if (pooledItem.getID() == workflowItem.getID()) {
                    hasPooledTask = true;
                }
            }
            if (!hasPooledTask) {
                throw new AuthorizeException("You are not allowed to perform this task.");
            }

        }
    }

	/**
	 * Reject the given task for the given reason. If the user did not provide
	 * a reason then an error is generated placing that field in error.
	 *
	 * @param context The current DSpace content
	 * @param id The unique ID of the current workflow
     * @param request The current request object
     * @return error if any.
     * @throws java.sql.SQLException passed through.
     * @throws org.dspace.app.xmlui.utils.UIException passed through.
     * @throws javax.servlet.ServletException passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException passed through.
	 */
	public static String processRejectTask(Context context, String id, HttpServletRequest request)
        throws SQLException, UIException, ServletException, AuthorizeException, IOException
	{
		BasicWorkflowItem workflowItem = findWorkflow(context, id);
		String reason = request.getParameter("reason");

        //Custom code
        String rejectedFilePath = null;

		if (reason != null && reason.length() > 1) {
            rejectedFilePath = createRejectionTempFile(context, request);
        
            // If content was returned and the content equals 'rejection
            if (StringUtils.isNotBlank(rejectedFilePath) && !rejectedFilePath.equals("bad-file")) {
                log.debug(LogManager.getHeader(context, "rejected_file_upload", " Rejected file path = "+rejectedFilePath));
                
                return rejectedFilePath;
            }

            WorkspaceItem wsi = basicWorkflowService.sendWorkflowItemBackSubmission(context, workflowItem, context.getCurrentUser(), null, reason);

            // Load the Submission Process for the collection this WSI is associated with
            Collection c = wsi.getCollection();
            SubmissionConfigReader subConfigReader = new SubmissionConfigReader();
            SubmissionConfig subConfig = subConfigReader.getSubmissionConfig(c.getHandle(), false);

            // Set the "stage_reached" column on the workspace item
            // to the LAST page of the LAST step in the submission process
            // (i.e. the page just before "Complete", which is at NumSteps-1)
            int lastStep = subConfig.getNumberOfSteps()-2;
            wsi.setStageReached(lastStep);
            wsi.setPageReached(AbstractProcessingStep.LAST_PAGE_REACHED);
            workspaceItemService.update(context, wsi);

            // Submission rejected.  Log this information
            log.info(LogManager.getHeader(context, "reject_workflow", "workflow_item_id="
                    + wsi.getID() + "item_id=" + wsi.getItem().getID()
                    + "collection_id=" + wsi.getCollection().getID()
                    + "eperson_id=" + context.getCurrentUser().getID()));

			// Return no errors.
			return null;
		} else {
			// If the user did not supply a reason then
			// place the reason field in error.
			return "reason";
		}
	}

    /**
     * Return the workflow identified by the given id, the id should be
     * prepended with the character S to signify that it is a workflow
     * instead of a workspace.
     *
     * @param context session context.
     * @param inProgressSubmissionID internal identifier of the submission.
     * @return The found workflow item or null if none found.
     * @throws java.sql.SQLException passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException passed through.
     */
    public static BasicWorkflowItem findWorkflow(Context context, String inProgressSubmissionID)
        throws SQLException, AuthorizeException, IOException
    {
        int id = Integer.valueOf(inProgressSubmissionID.substring(1));
        return basicWorkflowItemService.find(context, id);
    }

    private static String createRejectionTempFile(Context context, HttpServletRequest request)
        throws IOException, ServletException
    {
        Path filePath = null;
        Path newFilePath = null;
        Enumeration<String> attNames = request.getAttributeNames();
        ArrayList<String> attNamesList = Collections.list(attNames);

        //loop through our request attributes
        for(String attName : attNamesList) {
            log.debug(LogManager.getHeader(context, "reject_file_upload_request", " Attribute Name "+attName+" = "+request.getAttribute(attName)));

            if (attName.endsWith("-path")) {
                // Strip off the -path portion of the attribute's name
                // to get the actual name of the uploaded file.
                String param = attName.replace("-path", "");

                log.debug(LogManager.getHeader(context, "reject_file_upload_request", " Parameter Name = "+param));

                if (StringUtils.isNotBlank((String) request.getAttribute(param + "-path"))) {
                    // Load the file's path and input stream and description
                    filePath = Paths.get((String) request.getAttribute(param + "-path"));
                }

                log.debug(LogManager.getHeader(context, "reject_file_upload_request", " Is File Path null "+String.valueOf(filePath == null)));

                if (StringUtils.isNotBlank(filePath.toString())) {
                    log.debug(LogManager.getHeader(context, "reject_file_upload_request", " File Path = "+filePath.toString()));
                    
                    if (!isAuthorizedFile(context, filePath.getFileName().toString())) {
                        return "invalid-reject-file";
                    }
                    
                    newFilePath = Paths.get(configurationService.getProperty("upload.temp.dir")+"/"+filePath.getFileName().toString());
                    log.debug(LogManager.getHeader(context, "reject_file_upload_request", " New File Path = "+newFilePath.toString()));

                    Files.copy((InputStream) request.getAttribute(param + "-inputstream"), newFilePath, StandardCopyOption.REPLACE_EXISTING);

                    //log.debug(LogManager.getHeader(context, "reject_file_upload_request", " New file exists = "+String.valueOf(Files.exists(newFilePath, LinkOption.NOFOLLOW_LINKS))));

                    return newFilePath.toString();
                }
            }     
        }
        return "invalid-reject-file";
    }

    private static boolean isAuthorizedFile(Context context, String fileName) throws IOException
    {
        ArrayList<String> acceptableExtensionsList = new ArrayList<>();

        acceptableExtensionsList.add("pdf");
        acceptableExtensionsList.add("doc");
        acceptableExtensionsList.add("docx");
        acceptableExtensionsList.add("txt");

        log.debug(LogManager.getHeader(context, "reject_file_upload_request", " File Name = "+String.valueOf(fileName)));
        
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

            log.debug(LogManager.getHeader(context, "reject_file_upload_request", " Index of last dot = "+String.valueOf(lastDot)));
            
            if (lastDot != -1) {
                extension = newFilename.substring(lastDot + 1);
            }

            log.debug(LogManager.getHeader(context, "reject_file_upload_request", " File Extension = "+String.valueOf(extension)));

            if(StringUtils.isNotBlank(extension)) {                
                if(!acceptableExtensionsList.contains(extension)) {
                    return false;
                }
            }
        }
        return true;
    }
}
