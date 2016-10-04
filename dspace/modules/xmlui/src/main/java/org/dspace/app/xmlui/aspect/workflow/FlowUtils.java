/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.xmlui.aspect.workflow;

// Java class imports
import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import javax.servlet.http.HttpServletRequest;

// Apache class imports
import org.apache.cocoon.environment.Request;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

// DSpace class imports
import org.dspace.app.util.*;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.core.Utils;
import org.dspace.handle.HandleManager;
import org.dspace.submit.AbstractProcessingStep;
import org.dspace.workflow.WorkflowItem;
import org.dspace.workflow.WorkflowManager;



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

    private static final Logger log = Logger.getLogger(FlowUtils.class);

    /**
     * Update the provided workflowItem to advance to the next workflow
     * step. If this was the last thing needed before the item is
     * committed to the repository then return true, otherwise false.
     *
     * @param context
     *      The current DSpace content
     * @param id
     *      The unique ID of the current workflow
     * @return
     *
     * @throws java.sql.SQLException
     * @throws org.dspace.app.xmlui.utils.UIException
     * @throws javax.servlet.ServletException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     */
    public static boolean processApproveTask(Context context, String id)
        throws SQLException, UIException, ServletException, AuthorizeException, IOException
    {
        WorkflowItem workflowItem = findWorkflow(context, id);
        Item item = workflowItem.getItem();

        // Advance the item along the workflow
        WorkflowManager.advance(context, workflowItem, context.getCurrentUser());

        // FIXME: This should be a return value from advance()
        // See if that gave the item a Handle. If it did,
        // the item made it into the archive, so we
        // should display a suitable page.
        String handle = HandleManager.findHandle(context, item);

        context.commit();

        return handle != null;
    }



    /**
     * Return the given task back to the pool of unclaimed tasks for another user
     * to select and perform.
     *
     * @param context
     *      The current DSpace content
     * @param id
     *      The unique ID of the current workflow
     *
     * @throws java.sql.SQLException
     * @throws org.dspace.app.xmlui.utils.UIException
     * @throws javax.servlet.ServletException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     */
    public static void processUnclaimTask(Context context, String id)
    throws SQLException, UIException, ServletException, AuthorizeException, IOException
    {
        WorkflowItem workflowItem = findWorkflow(context, id);

        // Return task to pool
        WorkflowManager.unclaim(context, workflowItem, context.getCurrentUser());

        context.commit();

        // Log this unclaim action
        log.info(LogManager.getHeader(context, "unclaim_workflow", MessageFormat.format("workflow_item_id = {0}, item_id = {1}, collection_id = {2}, new_state = {3}", workflowItem.getID(), workflowItem.getItem().getID(), workflowItem.getCollection().getID(), workflowItem.getState())));
    }

    /**
     * Claim this task from the pool of unclaimed task so that this user may
     * perform the task by either approving or rejecting it.
     *
     * @param context
     *      The current DSpace content
     * @param id
     *      The unique ID of the current workflow
     *
     * @throws java.sql.SQLException
     * @throws org.dspace.app.xmlui.utils.UIException
     * @throws javax.servlet.ServletException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     */
    public static void processClaimTask(Context context, String id)
        throws SQLException, UIException, ServletException, AuthorizeException, IOException
    {
        WorkflowItem workflowItem = findWorkflow(context, id);
        if(workflowItem.getState() != WorkflowManager.WFSTATE_STEP1POOL &&
                workflowItem.getState() != WorkflowManager.WFSTATE_STEP2POOL &&
                workflowItem.getState() != WorkflowManager.WFSTATE_STEP3POOL)
        {
            // Only allow tasks in the pool to be claimed !
            throw new AuthorizeException("Error while claiming task: this task has already been claimed !");
        }

       // Claim the task
       WorkflowManager.claim(context, workflowItem, context.getCurrentUser());

       context.commit();

       // log this claim information
       log.info(LogManager.getHeader(context, "claim_task", "workflow_item_id="
                   + workflowItem.getID() + "item_id=" + workflowItem.getItem().getID()
                   + "collection_id=" + workflowItem.getCollection().getID()
                   + "newowner_id=" + workflowItem.getOwner().getID()
                   + "new_state=" + workflowItem.getState()));
    }

    /**
     * Verifies if the currently logged in user has proper rights to perform the workflow task on the item
     *
     * @param context
     *      the current dspace context
     * @param workflowItemId
     *      the identifier of the workflow item
     *
     * @throws org.dspace.authorize.AuthorizeException
     *      thrown if the user doesn't have sufficient rights to perform the task at hand
     * @throws java.sql.SQLException
     *      is thrown when something is wrong with the database
     */
    public static void authorizeWorkflowItem(Context context, String workflowItemId)
        throws AuthorizeException, SQLException
    {
        WorkflowItem workflowItem = WorkflowItem.find(context, Integer.parseInt(workflowItemId.substring(1)));
        if((workflowItem.getState() == WorkflowManager.WFSTATE_STEP1 ||
            workflowItem.getState() == WorkflowManager.WFSTATE_STEP2 ||
            workflowItem.getState() == WorkflowManager.WFSTATE_STEP3) &&
            workflowItem.getOwner().getID() != context.getCurrentUser().getID())
        {
            throw new AuthorizeException("You are not allowed to perform this task.");
        }
        else if((workflowItem.getState() == WorkflowManager.WFSTATE_STEP1POOL ||
                workflowItem.getState() == WorkflowManager.WFSTATE_STEP2POOL ||
                workflowItem.getState() == WorkflowManager.WFSTATE_STEP3POOL))
        {
            // Verify if the current user has the current workflowItem among his pooled tasks
            boolean hasPooledTask = false;
            List<WorkflowItem> pooledTasks = WorkflowManager.getPooledTasks(context, context.getCurrentUser());

            /**
             * Only perform the check if the user has pooled tasks. This additional check
             * corrects an issue caused by having only one task item in the DB table.
             */
            if(!pooledTasks.isEmpty() || pooledTasks.size() > 0)
            {
                for (WorkflowItem pooledItem : pooledTasks)
                {
                    if(pooledItem.getID() == workflowItem.getID())
                    {
                        hasPooledTask = true;
                    }
                }
                if(!hasPooledTask)
                {
                    throw new AuthorizeException("You are not allowed to perform this task.");
                }
            }
        }
    }

    /**
     * Reject the given task for the given reason. If the user did not provide
     * a reason then an error is generated placing that field in error.
     *
     * @param context
     *      The current DSpace content
     * @param id
     *      The unique ID of the current workflow
     * @param request
     *      The current request object
     *
     * @return
     *
     * @throws java.sql.SQLException
     * @throws org.dspace.app.xmlui.utils.UIException
     * @throws javax.servlet.ServletException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     *
     */
    public static String processRejectTask(Context context, String id, Request request)
        throws SQLException, UIException, ServletException, AuthorizeException, IOException
    {
        WorkflowItem workflowItem = findWorkflow(context, id);
        WorkspaceItem wsi = null;
        String reason = request.getParameter("reason");
        String rejectedFilePath = null;

        // uncomment for debugging information
        /*Enumeration requestParamNames = request.getParameterNames();
        Enumeration requestAttrNames = request.getAttributeNames();

        ArrayList<String> requestParamNamesList = Collections.list(requestParamNames);
        ArrayList<String> requestAttrNamesList = Collections.list(requestAttrNames);


        for(String requestParamName : requestParamNamesList)
        {
            log.debug(LogManager.getHeader(context, "Rejection Request Info", " Param Name = "+requestParamName));
        }

        for(String requestAttrName : requestAttrNamesList)
        {
            log.debug(LogManager.getHeader(context, "Rejection Request Info", " Attribute Name = "+requestAttrName));
        }

        log.debug(LogManager.getHeader(context, "Rejection Request Info", " Query String = "+request.getParameter("error_fields")));
        log.debug(LogManager.getHeader(context, "Rejection Request Info", " Reason Field Info "+reason));*/

        if(reason.isEmpty() && reason.length() > 1)
        {
            // If the user did not supply a reason then
            // place the reason field in error.
            return "reason";
        }

        rejectedFilePath = createRejectionTempFile(context, request);

        if(rejectedFilePath != null)
        {
            log.debug(LogManager.getHeader(context, "Rejected File Upload", " "+rejectedFilePath));

            if(rejectedFilePath.equals("rejection-upload-file-bad-file"))
            {
                return rejectedFilePath;
            }
        }

        wsi = WorkflowManager.reject(context, workflowItem,context.getCurrentUser(), reason, rejectedFilePath);

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
        wsi.update();

        context.commit();

        // Submission rejected.  Log this information
        log.info(LogManager.getHeader(context, "reject_workflow", MessageFormat.format("workflow_item_id = {0}, item_id = {1}, collection_id = {2}, eperson_id = {3}", wsi.getID(), wsi.getItem().getID(), wsi.getCollection().getID(), context.getCurrentUser().getID())));

        log.debug(LogManager.getHeader(context, "reject_workflow", MessageFormat.format("workflow_item_id = {0}, item_id = {1}, collection_id = {2}, eperson_id = {3}", wsi.getID(), wsi.getItem().getID(), wsi.getCollection().getID(), context.getCurrentUser().getID())));

        // Return no errors.
        return null;
    }

    /**
     * Return the workflow identified by the given id, the id should be
     * prepended with the character S to signify that it is a workflow
     * instead of a workspace.
     *
     * @param context
     *      The current dspace context
     * @param inProgressSubmissionID
     *      The identifier of the submissin currently in progress
     * @return
     *      The found workflowitem or null if none found.
     *
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     */
    public static WorkflowItem findWorkflow(Context context, String inProgressSubmissionID)
        throws SQLException, AuthorizeException, IOException
    {
        int id = Integer.valueOf(inProgressSubmissionID.substring(1));
        return WorkflowItem.find(context, id);
    }

    /**
     * Builds a temporary file that will be attached to
     * to a rejection notification email.
     *
     * @param context
     *      Current DSpace context
     * @param request
     *      Current Cocoon request
     * @return
     *      The file path of the temporary file
     *
     * @throws java.io.IOException
     */
    private static String createRejectionTempFile(Context context, HttpServletRequest request)
        throws IOException, ServletException
    {
        String filePath = null;
        File tempFile;
        String newFilePath = null;
        InputStream fileInputStream;

        ArrayList<String> acceptableExtensionsList = new ArrayList<>();

        acceptableExtensionsList.add("pdf");
        acceptableExtensionsList.add("doc");
        acceptableExtensionsList.add("docx");
        acceptableExtensionsList.add("txt");

        Enumeration attNames = request.getAttributeNames();

        ArrayList<String> attNamesList = Collections.list(attNames);

        //loop through our request attributes
        for(String attName : attNamesList)
        {
            log.debug(LogManager.getHeader(context, "Rejection Upload File Request", " Attribute Name = "+attName));
            log.debug(LogManager.getHeader(context, "Rejection Upload File Request", attName+" = "+request.getAttribute(attName)));

            if(attName.endsWith("-path"))
            {
                //strip off the -path to get the actual parameter
                //that the file was uploaded as
                String param = attName.replace("-path", "");

                log.debug(LogManager.getHeader(context, "Rejection Upload File Request", " Parameter Name = "+param));

                // Load the file's path and input stream and description
                filePath = (String) request.getAttribute(param + "-path");
                log.debug(LogManager.getHeader(context, "Rejection Upload File Request Param", " File Path = "+filePath));
                    
                // No file was provided so don't bother going any further
                if(StringUtils.isBlank(filePath))
                {                    
                    return null;
                }
                
                fileInputStream = (InputStream) request.getAttribute(param + "-inputstream");

                // Strip all but the last filename. It would be nice
                // to know which OS the file came from.
                String noPath = filePath;

                //log.debug(LogManager.getHeader(context, "", "Number of bytes available in fileInputStream is"+String.valueOf(fileInputStream.available())));
                /*System.out.printf("Number of bytes available in fileInputStream is:  %d\n\r", fileInputStream.available());*/

                while (noPath.indexOf('/') > -1)
                {
                    noPath = noPath.substring(noPath.indexOf('/') + 1);
                }

                while (noPath.indexOf('\\') > -1)
                {
                    noPath = noPath.substring(noPath.indexOf('\\') + 1);
                }

                // Replace any spaces in the file name with dashes
                String newFilename = null;
                String[] fna = noPath.split("\\s");
                newFilename = fna[0];

                for(int i=1; i < fna.length; i++)
                {
                    String s = fna[i];

                    if(!s.isEmpty())
                    {
                        newFilename = newFilename+"-"+s;
                    }
                }

                int lastDot = newFilename.lastIndexOf(".");
                String extension = null;
                if (lastDot != -1)
                {
                    extension = newFilename.substring(lastDot + 1);
                }

                if(extension != null)
                {
                    log.debug(LogManager.getHeader(context, "Rejection Upload File Info", " File Extension = "+extension));
                    if(!acceptableExtensionsList.contains(extension))
                    {
                        return "rejection-upload-file-bad-file";
                    }
                }

                newFilePath = ConfigurationManager.getProperty("upload.temp.dir")+"/"+newFilename;

                log.debug(LogManager.getHeader(context, "Rejection Upload File Info", "New File Path = "+newFilePath));
                /*System.out.printf("Build Reject Temp File %s\n\r", newFilePath);*/

                // Delete an existing version of the uploaded file.
                new File(newFilePath).delete();

                tempFile = new File(newFilePath);
                FileOutputStream fos = new FileOutputStream(tempFile);

                try
                {
                    Utils.bufferedCopy(fileInputStream, fos);
                    fos.close();
                    fileInputStream.close();
                }
                catch(IOException ioexp)
                {
                    log.error(LogManager.getHeader(context, "IO Exception ", ioexp.getMessage()));
                    //System.out.printf("IO Exception %s", ioexp.getMessage());
                }

                return newFilePath;
            }
        }

        return null;
    }
}
