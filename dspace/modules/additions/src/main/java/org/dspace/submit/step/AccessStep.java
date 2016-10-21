/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submit.step;

import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.dspace.app.util.SubmissionInfo;
import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.embargo.EmbargoManager;
import org.dspace.handle.HandleManager;
import org.dspace.submit.AbstractProcessingStep;
import org.joda.time.DateTime;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;


/**
 * This class manages the access step during the submission
 *
 * @author Fabio Bolognesi (fabio at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 *
 */
public class AccessStep extends AbstractProcessingStep
{
    public static final int STATUS_ERROR_FORMAT_DATE = 1;
    public static final int STATUS_ERROR_MISSING_DATE = 2;
    public static final int STATUS_ERROR_SELECT_GROUP = 3;
    public static final int STATUS_DUPLICATED_POLICY = 4;
    public static final int EDIT_POLICY_STATUS_DUPLICATED_POLICY=5;

    // edit file information
    public static final int STATUS_EDIT_POLICY = 10;

    public static final String SUB_INFO_SELECTED_RP = "SUB_INFO_SELECTED_RP";

    /** log4j logger */
    private static Logger log = Logger.getLogger(AccessStep.class);

    // OPERATIONS
    public static final String FORM_EDIT_BUTTON_CANCEL = "submit_edit_cancel";
    public static final String FORM_EDIT_BUTTON_SAVE = "submit_save";
    public static final String FORM_ACCESS_BUTTON_ADD = "submit_add_policy";

    /** Custom Class Properties */
    // Field names from custom AccessStep form
    public static final String AURORA_EMBARGO_LENGTH_FIELD_NAME = "embargo_length_radio";

    // Internal request info attribute name
    public static final String AURORA_SHOW_EMBARGO_SUMMARY = "print_embargo_summary";
    

    /**
     * Do any processing of the information input by the user, and/or perform
     * step processing (if no user interaction required)
     * <P>
     * It is this method's job to save any data to the underlying database, as
     * necessary, and return error messages (if any) which can then be processed
     * by the appropriate user interface (JSP-UI or XML-UI)
     * <P>
     * NOTE: If this step is a non-interactive step (i.e. requires no UI), then
     * it should perform *all* of its processing in this method!
     * 
     * @param context
     *            current DSpace context
     * @param request
     *            current servlet request object
     * @param response
     *            current servlet response object
     * @param subInfo
     *            submission info object
     * @return Status or error flag which will be processed by
     *         doPostProcessing() below! (if STATUS_COMPLETE or 0 is returned,
     *         no errors occurred!)
     */
    public int doProcessing(Context context, HttpServletRequest request, HttpServletResponse response, SubmissionInfo subInfo)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
        String buttonPressed = Util.getSubmitButton(request, CANCEL_BUTTON);

        // If the user simply clicks the Next button then move on to the next
        // screen.
        if(buttonPressed.equalsIgnoreCase(AbstractProcessingStep.NEXT_BUTTON))
        {
            return STATUS_COMPLETE;
        }

        // get reference to item
        Item item = subInfo.getSubmissionItem().getItem();

        if(wasRemovePolicyPressed(buttonPressed))
        {
            AuthorizeManager.removeAllPoliciesByDSOAndType(context, item, ResourcePolicy.TYPE_CUSTOM);

            if(item.hasUploadedFiles())
            {
                for(Bundle bndl : item.getBundles(Constants.CONTENT_BUNDLE_NAME))
                {
                    for(Bitstream bs : bndl.getBitstreams())
                    {
                        AuthorizeManager.removeAllPoliciesByDSOAndType(context, bs, ResourcePolicy.TYPE_CUSTOM);
                    }
                }
            }

            EmbargoManager.removeEmbargoEndDateMDV(context, item);

            if(!item.isDiscoverable())
            {
                item.setDiscoverable(true);
            }
        }

        // SELECTED OPERATION: go to EditPolicyForm
        if(wasEditPolicyPressed(context, buttonPressed, subInfo))
        {
            return STATUS_EDIT_POLICY;
        }

        if(comeFromEditPolicy(request))
        {
            return saveOrCancelEditPolicy(context, request, subInfo, buttonPressed, item, null, 0, null);
        }

        // SELECTED OPERATION: ADD Policy
        if(wasAddPolicyPressed(buttonPressed))
        {
            Date embargoEndDate = generateEmbargoUntilDate(request);

            if(embargoEndDate != null)
            {
                AuthorizeManager.generateAutomaticPolicies(context, embargoEndDate, null, item, (Collection)HandleManager.resolveToObject(context, subInfo.getCollectionHandle()));

                if(item.hasUploadedFiles())
                {
                    for(Bundle bndl : item.getBundles(Constants.CONTENT_BUNDLE_NAME))
                    {
                        for(Bitstream bs : bndl.getBitstreams())
                        {
                            AuthorizeManager.generateAutomaticPolicies(context, embargoEndDate, null, bs, (Collection)HandleManager.resolveToObject(context, subInfo.getCollectionHandle()));
                        }
                    }
                }

                // We want to attach the value of the selected embargo_length_radio button back to the
                subInfo.put(AURORA_EMBARGO_LENGTH_FIELD_NAME, request.getParameter(AURORA_EMBARGO_LENGTH_FIELD_NAME));
                subInfo.put(AURORA_SHOW_EMBARGO_SUMMARY, "1");
            }
        }

        item.update();
        context.commit();

        return STATUS_COMPLETE;
    }

    public static boolean wasEditPolicyPressed(Context context, String buttonPressed, SubmissionInfo subInfo) 
        throws SQLException 
    {
        if (buttonPressed.startsWith("submit_edit_edit_policies_") && !buttonPressed.equals(FORM_EDIT_BUTTON_CANCEL))
        {
            String idPolicy = buttonPressed.substring("submit_edit_edit_policies_".length());
            ResourcePolicy rp = ResourcePolicy.find(context, Integer.parseInt(idPolicy));
            subInfo.put(SUB_INFO_SELECTED_RP, rp);
            return true;
        }
        return false;
    }

    public static boolean wasAddPolicyPressed(String buttonPressed) 
            throws SQLException 
    {
        return (buttonPressed.equalsIgnoreCase(FORM_ACCESS_BUTTON_ADD));
    }

    public static boolean wasRemovePolicyPressed(String buttonPressed) 
        throws SQLException 
    {
        return (buttonPressed.startsWith("submit_delete_edit_policies_"));
    }

    public static boolean comeFromEditPolicy(HttpServletRequest request)
        throws SQLException 
    {
        return (request.getParameter("policy_id") != null);
    }

    public static int saveOrCancelEditPolicy(Context context, HttpServletRequest request, 
                                                SubmissionInfo subInfo, String buttonPressed, 
                                                DSpaceObject dso, String name, int groupID, 
                                                String reason) 
        throws AuthorizeException, SQLException 
    {
        if (buttonPressed.equals(FORM_EDIT_BUTTON_CANCEL))
        {
            return STATUS_COMPLETE;
        }
        else if (buttonPressed.equals(FORM_EDIT_BUTTON_SAVE))
        {
            String idPolicy = request.getParameter("policy_id");
            ResourcePolicy resourcePolicy = ResourcePolicy.find(context, Integer.parseInt(idPolicy));
            subInfo.put(SUB_INFO_SELECTED_RP, resourcePolicy);
            //Date dateStartDate = getEmbargoUntil(request);
            Date dateStartDate = generateEmbargoUntilDate(request);
            
            if((resourcePolicy=AuthorizeManager.createOrModifyPolicy(resourcePolicy, context, name, groupID, null, dateStartDate, Constants.READ, reason, dso))==null)
            {
                return EDIT_POLICY_STATUS_DUPLICATED_POLICY;
            }

            resourcePolicy.update();

            // If the current item in submission has an uploaded file associated with it
            // then update the start date property of any custom resource policies assigned
            // to the file
            if(subInfo.getSubmissionItem().getItem().hasUploadedFiles())
            {
                for(Bundle bndl : subInfo.getSubmissionItem().getItem().getBundles(Constants.CONTENT_BUNDLE_NAME))
                {
                    for(ResourcePolicy bsRP : bndl.getBitstreamPolicies())
                    {
                        if(bsRP.getRpType().equals(ResourcePolicy.TYPE_CUSTOM) && bsRP.getStartDate() != null)
                        {
                            bsRP.setStartDate(dateStartDate);
                            bsRP.update();
                        }
                    }
                }
            }

            context.commit();

            subInfo.put(AURORA_SHOW_EMBARGO_SUMMARY, "1");
        }
        return STATUS_COMPLETE;
    }

    public static void removePolicy(Context context, String buttonPressed) 
        throws SQLException 
    {
        String idPolicy = buttonPressed.substring("submit_delete_edit_policies_".length());
        ResourcePolicy rp = ResourcePolicy.find(context, Integer.parseInt(idPolicy));
        rp.delete();
    }

    public static int checkForm(HttpServletRequest request)
    {
        String selectedRadio=null;
        String dateEmbargoUntil = request.getParameter("embargo_until_date");

        // RADIO_OPEN_ACCESS_ITEM_VISIBLE=0;
        // RADIO_OPEN_ACCESS_ITEM_EMBARGOED=1;
        if((selectedRadio=request.getParameter("open_access_radios"))!=null && 
            Integer.parseInt(selectedRadio)==1 && 
            (dateEmbargoUntil==null || dateEmbargoUntil.equals("")))
        {
            return STATUS_ERROR_MISSING_DATE;
        }

        if(dateEmbargoUntil !=null && !dateEmbargoUntil.equals(""))
        {
            Date startDate = getEmbargoUntilDate(request);
            if(startDate==null)
            {
                return STATUS_ERROR_FORMAT_DATE;
            }
        }
        return 0;
    }

    public static Date getEmbargoUntil(HttpServletRequest request)
    {
        // RADIO_OPEN_ACCESS_ITEM_VISIBLE=0;
        // RADIO_OPEN_ACCESS_ITEM_EMBARGOED=1;
        String selectedRadio;
        Date dateStartDate=null;
        if((selectedRadio=request.getParameter("open_access_radios"))!=null && Integer.parseInt(selectedRadio)==1)
        {
            Date startDate = getEmbargoUntilDate(request);
            if(startDate!=null) dateStartDate=startDate;
        }
        return dateStartDate;
    }

    private static Date getEmbargoUntilDate(HttpServletRequest request)
    {
        Date startDate = null;
        try {
            startDate = DateUtils.parseDate(request.getParameter("embargo_until_date"), new String[]{"yyyy-MM-dd", "yyyy-MM", "yyyy"});
        } 
        catch (Exception e) 
        {
            //Ignore start date is already null
        }
        return startDate;
    }

    /**
     * Retrieves the number of pages that this "step" extends over. This method
     * is used to build the progress bar.
     * <P>
     * This method may just return 1 for most steps (since most steps consist of
     * a single page). But, it should return a number greater than 1 for any
     * "step" which spans across a number of HTML pages. For example, the
     * configurable "Describe" step (configured using input-forms.xml) overrides
     * this method to return the number of pages that are defined by its
     * configuration file.
     * <P>
     * Steps which are non-interactive (i.e. they do not display an interface to
     * the user) should return a value of 1, so that they are only processed
     * once!
     * 
     * @param request
     *            The HTTP Request
     * @param subInfo
     *            The current submission information object
     * 
     * @return the number of pages in this step
     */
    public int getNumberOfPages(HttpServletRequest request,
                                SubmissionInfo subInfo) 
        throws ServletException
    {
        return 1;

    }
    
    /**
     * Generate a new date for an embargo that is X number of years ahead of
     * the submission date. This date is a temporary date and is not
     *
     * @param request
     * @return
     */
    private static Date generateEmbargoUntilDate(HttpServletRequest request)
    {
        String selectedRadio;

        selectedRadio=request.getParameter(AURORA_EMBARGO_LENGTH_FIELD_NAME);

        if(selectedRadio != null)
        {
            return DateTime.now().plusYears(Integer.parseInt(selectedRadio)).toDate();
        }

        return null;
    }
}
