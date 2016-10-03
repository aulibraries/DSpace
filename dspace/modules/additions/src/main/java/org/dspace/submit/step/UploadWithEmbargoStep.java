/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submit.step;

// Java class imports
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.dspace.app.util.SubmissionInfo;
import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.FormatIdentifier;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.curate.Curator;
import org.dspace.embargo.ETDEmbargoSetter;
import org.dspace.embargo.EmbargoManager;
import org.dspace.handle.HandleManager;
import static org.dspace.submit.AbstractProcessingStep.NEXT_BUTTON;
import static org.dspace.submit.AbstractProcessingStep.STATUS_COMPLETE;
import static org.dspace.submit.step.AccessStep.STATUS_ERROR_MISSING_DATE;
import static org.dspace.submit.step.UploadStep.CANCEL_EDIT_BUTTON;
import static org.dspace.submit.step.UploadStep.SUBMIT_UPLOAD_BUTTON;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Upload step with the advanced embargo system for DSpace. Processes the actual
 * upload of files for an item being submitted into DSpace.
 * <P>
 * This class performs all the behind-the-scenes processing that
 * this particular step requires.  This class's methods are utilized
 * by both the JSP-UI and the Manakin XML-UI
 *
 * @see org.dspace.app.util.SubmissionConfig
 * @see org.dspace.app.util.SubmissionStepConfig
 * @see org.dspace.submit.step.UploadStep
 * @see org.dspace.submit.AbstractProcessingStep
 *
 * @author Tim Donohue
 * @author Keiji Suzuki
 * @version $Revision$
 */
public class UploadWithEmbargoStep extends UploadStep
{
    public static final int STATUS_EDIT_POLICIES = 30;

    public static final int STATUS_EDIT_POLICIES_ERROR_SELECT_GROUP = 31;
    public static final int STATUS_EDIT_POLICIES_DUPLICATED_POLICY = 32;

    public static final int STATUS_EDIT_POLICY_ERROR_SELECT_GROUP = 33;
    public static final int STATUS_EDIT_POLICY_DUPLICATED_POLICY = 34;

    /** log4j logger */
    private static final Logger log = Logger.getLogger(UploadWithEmbargoStep.class);

    /** is the upload required? */
    private final boolean fileRequired = ConfigurationManager.getBooleanProperty("webui.submit.upload.required", true);


    /**
     * Custom constants
     */
    public static final String SUBMIT_REMOVE_SELECTED = "submit_remove_selected";
    public static final String SUBMIT_EDIT_PREFIX = "submit_edit_";
    public static final String SUBMIT_REMOVE_PREFIX = "submit_remove_";
    private static final String ETD_CREATE_QUESTION_FIELD_NAME = "create_embargo_radio";
    protected static final String ETD_DATE_FIELD_NAME = "embargo_until_date";

    public static final int STATUS_UNACCEPTABLE_FORMAT = 11;
    public static final int STATUS_ERROR_DATE_IN_PAST = 35;
    public static final int STATUS_ERROR_EMBARGO_CREATION_REQUIRED = 36;
    public static final int STATUS_ERROR_DATE_IS_CURRENT = 37;
    public static final int STATUS_ERROR = 38;

        /**
     *
     * @param context
     *          current DSpace context
     * @param request
     *          current servlet request object
     * @param response
     *          current servlet response object
     * @param subInfo
     *          submission info object
     * @return Status or error flag which will be processed by
     *         UI-related code! (if STATUS_COMPLETE or 0 is returned,
     *         no errors occurred!)
     *
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     */
    @Override
    @SuppressWarnings({"unchecked", "unchecked", "unchecked"})
    public int doProcessing(Context context, HttpServletRequest request, 
                            HttpServletResponse response, SubmissionInfo subInfo) 
        throws ServletException, IOException,
        SQLException, AuthorizeException
    {
        // get button user pressed
        String buttonPressed = Util.getSubmitButton(request, NEXT_BUTTON);

        // get reference to item
        Item item = subInfo.getSubmissionItem().getItem();

        // -----------------------------------
        // Step #0: Upload new files (if any)
        // -----------------------------------
        String contentType = request.getContentType();

        String embargoCreationAnswerString = null;
        int embargoCreationAnswer = 0;
        DateTimeFormatter dft = DateTimeFormat.forPattern("yyyy-MM-dd");

        if(buttonPressed.equals(SUBMIT_UPLOAD_BUTTON) || buttonPressed.equals("submit_save"))
        {
            /**
             * Custom Code Section
             */
            if(buttonPressed.equals(SUBMIT_UPLOAD_BUTTON))
            {
                String filePath = null;
                Enumeration paramNames = request.getParameterNames();
                Enumeration attrNames = request.getAttributeNames();

                ArrayList<String> attrNamesList = Collections.list(attrNames);
                ArrayList<String> paramNamesList = Collections.list(paramNames);

                for(String paramName : paramNamesList)
                {
                    log.debug(LogManager.getHeader(context, "Request Parameter List", " Name = "+paramName));
                }

                for(String attrName : attrNamesList)
                {
                    log.debug(LogManager.getHeader(context, "Request Attribute List", " Name = "+attrName));
                }

                if(paramNamesList.contains("file"))
                {
                    filePath = request.getParameter("file");
                }

                if(filePath == null && fileRequired && !item.hasUploadedFiles())
                {
                    log.error(LogManager.getHeader(context, "Upload Request Param", " File = NULL"));

                    log.error(LogManager.getHeader(context, "Submission Error Thrown", " Error Flag = "+String.valueOf(STATUS_NO_FILES_ERROR)));

                    subInfo.put("file_ERROR", STATUS_NO_FILES_ERROR);
                }

                log.debug(LogManager.getHeader(context, "Upload Request Param", " request.getParameter(\"file\") = "+String.valueOf(request.getParameter("file"))));
            }

            /**
             * Get the value of the first select field
             */
            log.debug(LogManager.getHeader(context, "Upload Request Param", " request.getParameter(\""+ETD_CREATE_QUESTION_FIELD_NAME+"\") = "+String.valueOf(request.getParameter(ETD_CREATE_QUESTION_FIELD_NAME))));

            if(request.getParameter(ETD_CREATE_QUESTION_FIELD_NAME) != null)
            {
                embargoCreationAnswer = Integer.parseInt(request.getParameter(ETD_CREATE_QUESTION_FIELD_NAME));
            }
            else
            {
                log.error(LogManager.getHeader(context, "Submission Error Thrown", " Error Flag = "+String.valueOf(STATUS_ERROR_EMBARGO_CREATION_REQUIRED)));

                subInfo.put(ETD_CREATE_QUESTION_FIELD_NAME+"_ERROR", STATUS_ERROR_EMBARGO_CREATION_REQUIRED);
            }

            /**
             * If the user has chosen to create an embargo, but forgot
             * to set a lift date then return an error code. Adding this
             * check here will prevent the file the user has selected from
             * being uploaded until they've filled out an embargo date.
             */
            if(embargoCreationAnswer == 2 || embargoCreationAnswer == 3)
            {
                log.debug(LogManager.getHeader(context, "Upload Request Param", " request.getParameter(\""+ETD_DATE_FIELD_NAME+"\") = "+String.valueOf(request.getParameter(ETD_DATE_FIELD_NAME))));

                DateTime _submittedDate = null;

                // if the requested parameter is empty then throw an error
                if(request.getParameter(ETD_DATE_FIELD_NAME).isEmpty())
                {
                    log.error(LogManager.getHeader(context, "Submission Error Thrown", " Error Flag = "+String.valueOf(STATUS_ERROR_MISSING_DATE)));

                    subInfo.put(ETD_CREATE_QUESTION_FIELD_NAME, request.getParameter(ETD_CREATE_QUESTION_FIELD_NAME));
                    subInfo.put(ETD_DATE_FIELD_NAME+"_ERROR", STATUS_ERROR_MISSING_DATE);
                }
                else
                {
                    _submittedDate = new DateTime(request.getParameter(ETD_DATE_FIELD_NAME));

                    // is the submitted date in the past
                    if(_submittedDate.isBeforeNow())
                    {
                        log.error(LogManager.getHeader(context, "Submission Error Thrown", " Error Flag = "+String.valueOf(STATUS_ERROR_DATE_IN_PAST)));

                        subInfo.put(ETD_DATE_FIELD_NAME+"_ERROR", STATUS_ERROR_DATE_IN_PAST);
                        subInfo.put(ETD_CREATE_QUESTION_FIELD_NAME, request.getParameter(ETD_CREATE_QUESTION_FIELD_NAME));
                        subInfo.put(ETD_DATE_FIELD_NAME, request.getParameter(ETD_DATE_FIELD_NAME));

                        log.error(LogManager.getHeader(context, "Submission Error Thrown", " Error Flag = "+String.valueOf(STATUS_ERROR_DATE_IN_PAST)));
                    }

                    // do the submitted date and current date equal each other
                    if(_submittedDate.isEqual(new DateTime(dft.print(DateTime.now()))))
                    {
                        log.error(LogManager.getHeader(context, "Submission Error Thrown", " Error Flag = "+String.valueOf(STATUS_ERROR_DATE_IN_PAST)));

                        subInfo.put(ETD_DATE_FIELD_NAME+"_ERROR", STATUS_ERROR_DATE_IS_CURRENT);
                        subInfo.put(ETD_CREATE_QUESTION_FIELD_NAME, request.getParameter(ETD_CREATE_QUESTION_FIELD_NAME));
                        subInfo.put(ETD_DATE_FIELD_NAME, request.getParameter(ETD_DATE_FIELD_NAME));
                    }
                }
            }
            else if(embargoCreationAnswer == 1)
            {
                subInfo.put(ETD_CREATE_QUESTION_FIELD_NAME, request.getParameter(ETD_CREATE_QUESTION_FIELD_NAME));
            }

            if(buttonPressed.equals(SUBMIT_UPLOAD_BUTTON) && !item.hasUploadedFiles())
            {
                // if multipart form, then we are uploading a file
                if ((contentType != null) && (contentType.contains("multipart/form-data")))
                {
                    // This is a multipart request, so it's a file upload
                    // (return any status messages or errors reported)
                    int status = 0;

                    status = processUploadFile(context, request, response, subInfo);

                    // if error occurred, return immediately
                    if (status != STATUS_COMPLETE)
                    {
                        log.error(LogManager.getHeader(context, "Submission Error Thrown", " Error Flag = "+String.valueOf(status)));

                        subInfo.put("file_ERROR", status);
                    }
                }
            }

            // if there were errors then stop execution here
            @SuppressWarnings("unchecked")
            List<String> subInfoKeyList = new ArrayList<String>(subInfo.keySet());
            for(String key : subInfoKeyList)
            {
                if(key.contains("ERROR"))
                {
                    return STATUS_ERROR;
                }
            }

            if(buttonPressed.equals("submit_save"))
            {
                // Remove any residual bitstream info from the submission object
                subInfo.setBitstream(null);

                if (request.getParameter("bitstream_id") != null)
                {
                    // load info for bitstream we are editing
                    Bitstream b = Bitstream.find(context, Integer.parseInt(request.getParameter("bitstream_id")));

                    // save bitstream to submission info
                    subInfo.setBitstream(b);
                }
            }

            /**
             * If the user has chosen to create an embargo then call the
             * method processAccessFields otherwise call the ETDEmbargoSetter
             * method setEmbargoStatusMDV. The processAccessFields method
             * is designed to generate the correct resource policies and
             * add specific values to an item's metadata field list. If the
             * user has chosen not to create an embargo then we will create
             * and populate the item's embargo status metadata field.
             */
            if(embargoCreationAnswer == 2 || embargoCreationAnswer == 3)
            {
                processAccessFields(context, request, subInfo, embargoCreationAnswer, request.getParameter(ETD_DATE_FIELD_NAME));
            }
            else if(embargoCreationAnswer == 1 || embargoCreationAnswer == 0)
            {
                ETDEmbargoSetter.setEmbargoStatusMDV(context, item, 0, false);
            }

            /**
             * End Custom Code Section
             */
        }

        // ---------------------------------------------
        // Step #1: Check if this was just a request to
        // edit file information.
        // (or canceled editing information)
        // ---------------------------------------------
        // check if we're already editing a specific bitstream
        if (request.getParameter("bitstream_id") != null)
        {
            if (buttonPressed.equals(CANCEL_EDIT_BUTTON))
            {
                // canceled an edit bitstream request
                subInfo.setBitstream(null);

                // this flag will just return us to the normal upload screen
                return STATUS_EDIT_COMPLETE;
            }
            else
            {
                // load info for bitstream we are editing
                Bitstream b = Bitstream.find(context, Integer.parseInt(request
                        .getParameter("bitstream_id")));

                // save bitstream to submission info
                subInfo.setBitstream(b);
            }
        }
        else if (buttonPressed.startsWith(SUBMIT_EDIT_PREFIX))
        {
            // get ID of bitstream that was requested for editing
            String bitstreamID = buttonPressed.substring(SUBMIT_EDIT_PREFIX.length());

            Bitstream b = Bitstream.find(context, Integer.parseInt(bitstreamID));

            // save bitstream to submission info
            subInfo.setBitstream(b);

            if(EmbargoManager.getEmbargoStatusMDV(context, item) != null)
            {
                String createQuestionFieldValue = null;

                switch(EmbargoManager.getEmbargoStatusMDV(context, item))
                {
                    case ETDEmbargoSetter.EMBARGOED:
                        if(EmbargoManager.getEmbargoRightsMDV(context, item) != null)
                        {
                            switch(EmbargoManager.getEmbargoRightsMDV(context, item))
                            {
                                case ETDEmbargoSetter.EMBARGO_NOT_AUBURN_STR:
                                    createQuestionFieldValue = "2";
                                    break;
                                case ETDEmbargoSetter.EMBARGO_GLOBAL_STR:
                                    createQuestionFieldValue = "3";
                                    break;
                            }
                        }
                        break;
                    case ETDEmbargoSetter.NOT_EMBARGOED:
                        createQuestionFieldValue = "1";
                        break;
                }

                if(createQuestionFieldValue != null)
                {
                    subInfo.put(ETD_CREATE_QUESTION_FIELD_NAME, createQuestionFieldValue);
                }
            }

            if(EmbargoManager.getEmbargoEndDateMDV(context, item) != null)
            {
                subInfo.put(ETD_DATE_FIELD_NAME, EmbargoManager.getEmbargoEndDateMDV(context, item));
            }

            // return appropriate status flag to say we are now editing the
            // bitstream
            return STATUS_EDIT_BITSTREAM;
        }

        // ---------------------------------------------
        // Step #2: Process any remove file request(s)
        // ---------------------------------------------
        // Remove-selected requests come from Manakin
        if (buttonPressed.startsWith(SUBMIT_REMOVE_PREFIX))
        {
            // A single file "remove" button must have been pressed

            int id = 0;
            int status = 0;

            if (request.getParameter("remove") != null)
            {
                id = Integer.parseInt(request.getParameter("remove"));
            }

            if(id > 0)
            {
                status = processRemoveFile(context, item, id);
            }

            // if error occurred, return immediately
            if (status != STATUS_COMPLETE)
            {
                return status;
            }/**/

            // remove current bitstream from Submission Info
            subInfo.setBitstream(null);

            ETDEmbargoSetter.setEmbargoStatusMDV(context, item, 0, false);
            EmbargoManager.removeETDEmbargoPolicies(context, item);
            EmbargoManager.removeEmbargoEndDateMDV(context, item);
            EmbargoManager.removeEmbargoLengthMDV(context, item);
            EmbargoManager.removeEmbargoRightsMDV(context, item);

            // return the value of status and stop execution here
            return status;
        }

        return STATUS_COMPLETE;
    }

    /**
     *
     * @param context
     *          current DSpace context
     * @param request
     *          current servlet request object
     * @param response
     *          current servlet response object
     * @param subInfo
     *          submission info object
     * @return Status or error flag which will be processed by
     *         UI-related code! (if STATUS_COMPLETE or 0 is returned,
     *         no errors occurred!)
     *
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     */
    @Override
    public int processUploadFile(Context context, HttpServletRequest request,
                                HttpServletResponse response, SubmissionInfo subInfo)
        throws ServletException, IOException, SQLException,
        AuthorizeException
    {
        boolean formatKnown = true;
        boolean fileOK = false;
        BitstreamFormat bf = null;
        Bitstream b = null;

        //NOTE: File should already be uploaded.
        //Manakin does this automatically via Cocoon.
        //For JSP-UI, the SubmissionController.uploadFiles() does the actual upload

        Enumeration attNames = request.getAttributeNames();

        //loop through our request attributes
        while(attNames.hasMoreElements())
        {
            String attr = (String) attNames.nextElement();

            //log.debug(LogManager.getHeader(context, "Submission File Upload Request", " Attribute Name = "+attr));

            //if this ends with "-path", this attribute
            //represents a newly uploaded file
            if(attr.endsWith("-path"))
            {
                //strip off the -path to get the actual parameter
                //that the file was uploaded as
                String param = attr.replace("-path", "");

                log.debug(LogManager.getHeader(context, "Submission File Upload Request", " Parameter Name = "+param));

                // Load the file's path and input stream and description
                String filePath = (String) request.getAttribute(param + "-path");
                log.info(LogManager.getHeader(context, "Submission File Upload Request", " Param - File Path = "+filePath));

                InputStream fileInputStream = (InputStream) request.getAttribute(param + "-inputstream");

                // if information wasn't passed by User Interface, we had a problem
                // with the upload
                /*if(filePath == null || fileInputStream == null)
                {
                    log.error(LogManager.getHeader(context, "", "File Upload Error"));
                    log.debug(LogManager.getHeader(context, "File Upload Error Thrown", " Error Flag = "+String.valueOf(STATUS_UPLOAD_ERROR)));
                    return STATUS_UPLOAD_ERROR;
                }*/

                if(subInfo == null)
                {
                    // In any event, if we don't have the submission info, the request
                    // was malformed
                    //log.error(LogManager.getHeader(context, "", "Upload Request Error"));
                    log.error(LogManager.getHeader(context, "Upload Request Error Thrown", " Error Flag = "+String.valueOf(STATUS_INTEGRITY_ERROR)));
                    return STATUS_INTEGRITY_ERROR;
                }

                // Create the bitstream
                Item item = subInfo.getSubmissionItem().getItem();

                // do we already have a bundle?
                Bundle[] bundles = item.getBundles("ORIGINAL");

                if(bundles.length < 1)
                {
                    // set bundle's name to ORIGINAL
                    b = item.createSingleBitstream(fileInputStream, "ORIGINAL");
                }
                else
                {
                    // we have a bundle already, just add bitstream
                    b = bundles[0].createBitstream(fileInputStream);
                }

                // Strip all but the last filename. It would be nice
                // to know which OS the file came from.
                String noPath = filePath;

                while (noPath.indexOf('/') > -1)
                {
                    noPath = noPath.substring(noPath.indexOf('/') + 1);
                }

                while (noPath.indexOf('\\') > -1)
                {
                    noPath = noPath.substring(noPath.indexOf('\\') + 1);
                }

                b.setName(noPath);
                b.setSource(filePath);
                //b.setDescription(fileDescription);
                b.setDescription(null);

                // Identify the format
                bf = FormatIdentifier.guessFormat(context, b);

                //if format was not identified
                if (bf == null)
                {
                    backoutBitstream(subInfo, b, item);
                    return STATUS_UNKNOWN_FORMAT;
                }

                /**
                 * Limit the type of file that can be uploaded to PDFs.
                 * Note: Probably not the best method of
                 * limiting the type of file that can be accepted,
                 * but it's the only way that doesn't require
                 * a lot of customization to other parts of DSpace's
                 * native source code.
                 */
                // Only PDF type files are acceptable.
                if(!(bf.getMIMEType().equals("application/pdf")))
                {
                    log.error(LogManager.getHeader(context, "File Upload", "ERROR - Attempting to upload file with an unknown file format. File "+noPath));
                    backoutBitstream(subInfo, b, item);
                    log.error(LogManager.getHeader(context, "Submission Error Thrown", " Error Flag = "+String.valueOf(STATUS_UNACCEPTABLE_FORMAT)));
                    return STATUS_UNACCEPTABLE_FORMAT;
                }

                b.setFormat(bf);

                // Update to DB
                b.update();
                item.update();

                // commit all changes to database
                context.commit();

                if ((bf.isInternal()))
                {
                    log.warn("Attempt to upload file format marked as internal system use only");
                    backoutBitstream(subInfo, b, item);
                    log.error(LogManager.getHeader(context, "Submission Error Thrown", " Error Flag = "+String.valueOf(STATUS_UPLOAD_ERROR)));
                    return STATUS_UPLOAD_ERROR;
                }

                // Check for virus
                if (ConfigurationManager.getBooleanProperty("submission-curation", "virus-scan"))
                {
                    Curator curator = new Curator();
                    curator.addTask("vscan").curate(item);
                    int status = curator.getStatus("vscan");
                    if (status == Curator.CURATE_ERROR)
                    {
                        backoutBitstream(subInfo, b, item);
                        log.error(LogManager.getHeader(context, "Submission Error Thrown", " Error Flag = "+String.valueOf(STATUS_VIRUS_CHECKER_UNAVAILABLE)));
                        return STATUS_VIRUS_CHECKER_UNAVAILABLE;
                    }
                    else if (status == Curator.CURATE_FAIL)
                    {
                        backoutBitstream(subInfo, b, item);
                        log.error(LogManager.getHeader(context, "Submission Error Thrown", " Error Flag = "+String.valueOf(STATUS_CONTAINS_VIRUS)));
                        return STATUS_CONTAINS_VIRUS;
                    }
                }

                // If we got this far then everything is more or less ok.

                // Comment - not sure if this is the right place for a commit here
                // but I'm not brave enough to remove it - Robin.
                context.commit();

                // save this bitstream to the submission info, as the
                // bitstream we're currently working with
                subInfo.setBitstream(b);

            }//end if attribute ends with "-path"
        }//end while

        return STATUS_COMPLETE;
    }

    /**
     *
     * @param context
     *          current DSpace context
     * @param request
     *          current servlet request object
     * @param subInfo
     *          submission info object
     * @param b
     *          bitstream object
     *
     * @throws javax.sql.SQLException
     * @throws java.io.IOException
     * @throws java.text.ParseException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws org.dspace.content.NonUniqueMetadataException
     */
    private void processAccessFields(Context context, HttpServletRequest request, SubmissionInfo subInfo, int type, String liftDate)
        throws AuthorizeException, SQLException, IOException
    {
        DateTimeFormatter dft = DateTimeFormat.forPattern("yyyy-MM-dd");
        Item item = null;
        Collection owningCollection = null;

        item = subInfo.getSubmissionItem().getItem();

        //log.debug(LogManager.getHeader(context, "Lift Date", " request.getParameter(\""+ETD_DATE_FIELD_NAME+"\") = "+String.valueOf(request.getParameter(ETD_DATE_FIELD_NAME))));

        // Get the bitstream's parent collection
        owningCollection = (Collection) HandleManager.resolveToObject(context, subInfo.getCollectionHandle());

        /**
         * For documentation purposes we also need to set values of the
         * item's dc.rights and dc.embargo.status metadata fields.
         */
        if(item != null)
        {
            log.debug(LogManager.getHeader(context, "Setting dc.rights MDV", " Item ID = "+String.valueOf(item.getID())));
            log.debug(LogManager.getHeader(context, "Setting dc.rights MDV", " Type ID = "+String.valueOf(type)));
            log.debug(LogManager.getHeader(context, "Setting dc.rights MDV", " Verbose = true"));/**/

            ETDEmbargoSetter.setEmbargoRightsMDV(context, item, type, false);

            log.debug(LogManager.getHeader(context, "Setting dc.embargo.status MDV", " Item ID = "+String.valueOf(item.getID())));
            log.debug(LogManager.getHeader(context, "Setting dc.embargo.status MDV", " State = 1"));
            log.debug(LogManager.getHeader(context, "Setting dc.embargo.status MDV", " Verbose = true"));/**/

            ETDEmbargoSetter.setEmbargoStatusMDV(context, item, 1,  false);

            log.debug(LogManager.getHeader(context, "Generating ETD Embargo Policies", " Bitstream ID = "+String.valueOf(subInfo.getBitstream().getID())));
            log.debug(LogManager.getHeader(context, "Generating ETD Embargo Policies", " Lift Date = "+liftDate));
            log.debug(LogManager.getHeader(context, "Generating ETD Embargo Policies", " Embargo Type = "+String.valueOf(type)));
            log.debug(LogManager.getHeader(context, "Generating ETD Embargo Policies", " Owning Collection Handle = "+owningCollection.getHandle()));/**/

            // Now that we've collected all the necessary information let's generate
            // ETD specific embargo policies.
            ETDEmbargoSetter.generateETDEmbargoPolicies(context, DateTime.parse(liftDate).toDate(), subInfo.getBitstream(), type, owningCollection);
            ETDEmbargoSetter.setEmbargoEndDateMDV(context, item, DateTime.parse(liftDate), false);
        }
    }
}
