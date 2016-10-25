/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.embargo;

// Java package imports
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.DCDate;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.license.CreativeCommons;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Months;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 *
 * @author STONEMA
 */
public class ETDEmbargoSetter implements EmbargoSetter
{
    private static final Logger log = Logger.getLogger(ETDEmbargoSetter.class);

    protected static String termsOpen = null;

    // Custom Constants Section
    public static final String EMBARGOED = "EMBARGOED";
    public static final String NOT_EMBARGOED = "NOT_EMBARGOED";
    public static final String EMBARGO_NOT_AUBURN_STR = "EMBARGO_NOT_AUBURN";
    public static final String EMBARGO_GLOBAL_STR = "EMBARGO_GLOBAL";

    // End Custom Constants Section

    public ETDEmbargoSetter()
    {
        super();
        termsOpen = ConfigurationManager.getProperty("embargo.terms.open");
    }

    /**
     *
     * @param context
     *      Current Dspace context
     * @param item
     *      Instance of the item object being acted upon
     * @param term
     *      String representation of a date.
     * @return
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     */
    @Override
    public DCDate parseTerms(Context context, Item item, String term)
        throws SQLException, IOException, AuthorizeException
    {
        /*DateTimeFormatter dft = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        if(term != null)
        {
            DateTime newEndDate = generateConvertedEmbargoEndDate(context, item, term);

            return new DCDate(dft.print(newEndDate));
        }*/

        return null;
    }

    /**
     *
     * @param context
     *      Current Dspace context
     * @param item
     *      Instance of the item object being acted upon
     *
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     */
    @Override
    public void setEmbargo(Context context, Item item)
        throws SQLException, IOException, AuthorizeException
    {
        try
        {
            // Set the value of the dc.embargo.length metadata field
            setEmbargoLengthMDV(context, item, false);
        }
        catch(SQLException exp)
        {
            // throw something here
        }
        catch (IOException exp)
        {
            // throw something here
        }
        catch (AuthorizeException exp)
        {
            // throw something here
        }
    }

    /**
     *
     * @param context
     *      Current context object
     * @param item
     *      Current item being worked on
     *
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     */
    @Override
    public void checkEmbargo(Context context, Item item)
        throws SQLException, IOException, AuthorizeException
    {
        for (Bundle bn : item.getBundles())
        {
            // Skip the LICENSE and METADATA bundles, they stay world-readable
            String bnn = bn.getName();
            if (!(bnn.equals(Constants.LICENSE_BUNDLE_NAME) || bnn.equals(Constants.METADATA_BUNDLE_NAME) || bnn.equals(CreativeCommons.CC_BUNDLE_NAME)))
            {
                // don't report on "TEXT" or "THUMBNAIL" bundles; those
                // can have READ as long as the bitstreams in them do not.
                if (!(bnn.equals("TEXT") || bnn.equals("THUMBNAIL")))
                {
                    // check for ANY read policies and report them:
                    for (ResourcePolicy rp : AuthorizeManager.getPoliciesActionFilter(context, bn, Constants.READ))
                    {
                        System.out.println("CHECK WARNING: Item "+item.getHandle()+", Bundle "+bn.getName()+" allows READ by "+
                          ((rp.getEPersonID() < 0) ? "Group "+rp.getGroup().getName() :
                                                      "EPerson "+rp.getEPerson().getFullName()));
                    }
                }

                for (Bitstream bs : bn.getBitstreams())
                {
                    for (ResourcePolicy rp : AuthorizeManager.getPoliciesActionFilter(context, bs, Constants.READ))
                    {
                        EmbargoManager.printRPInfo(context, rp);
                    }
                }
            }
        }
    }

    /**
     *
     * @param context
     *      Current context object
     * @param item
     *      Current item being worked on
     *
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     */
    public static void generateMissingEmbargoMDVInfo(Context context, Item item)
        throws SQLException, IOException, AuthorizeException
    {
        String status = EmbargoManager.getEmbargoStatusMDV(context, item);
        String length = EmbargoManager.getEmbargoLengthMDV(context, item);
        String enddate = EmbargoManager.getEmbargoEndDateMDV(context, item);
        String rights = EmbargoManager.getEmbargoRightsMDV(context, item);

        int type = 0;
        String ca = "CORRECTIVE ACTION: ";

        if(status == null && length == null)
        {
            if(log.isDebugEnabled())
            {
                System.out.println("-----------------------------------------------------");
                System.out.println("SETTING EMBARGO STATUS MDV INFORMATION");
                System.out.println("-----------------------------------------------------");
            }

            setEmbargoStatusMDV(context, item, 0, true);
        }
        else if(status != null && length != null)
        {
            if(NOT_EMBARGOED.equals(status) && length.contains("NO_RESTRICTION"))
            {

                if(log.isDebugEnabled())
                {
                    ca += "None";
                    System.out.println("-----------------------------------------------------");
                    System.out.println(ca);
                    System.out.println("-----------------------------------------------------");
                }
                return;
            }

            if(NOT_EMBARGOED.equals(status) && (length.contains("MONTHS") || length.contains("DAYS")))
            {
                if(log.isDebugEnabled())
                {
                    System.out.println("-----------------------------------------------------");
                    System.out.println("SETTING EMBARGO STATUS MDV INFORMATION");
                    System.out.println("-----------------------------------------------------");
                }

                setEmbargoStatusMDV(context, item, 1, true);
            }
        }

        status = null;
        status = EmbargoManager.getEmbargoStatusMDV(context, item);

        if(rights == null && EMBARGOED.equals(status))
        {
            List<ResourcePolicy> bsRPList = null;
            for(Bundle bndl : item.getBundles(Constants.CONTENT_BUNDLE_NAME))
            {
                bsRPList = bndl.getBitstreamPolicies();
            }

            if(bsRPList != null)
            {
                ArrayList<String> rpTypeList = new ArrayList<String>();
                ArrayList<String> rpNameList = new ArrayList<String>();

                for(ResourcePolicy bsRP : bsRPList)
                {
                    rpTypeList.add(bsRP.getRpType());
                    rpNameList.add(bsRP.getRpName());
                }

                if(!rpTypeList.contains(ResourcePolicy.TYPE_INHERITED) && 
                    !rpTypeList.contains(ResourcePolicy.TYPE_SUBMISSION) && 
                    !rpTypeList.contains(ResourcePolicy.TYPE_WORKFLOW))
                {
                    if(rpNameList.contains("Auburn") && rpNameList.contains("Public"))
                    {
                        type = 3;
                    }
                    else if(!rpNameList.contains("Auburn") && rpNameList.contains("Public"))
                    {
                        type = 2;
                    }
                    else if(!rpNameList.contains("Auburn") && !rpNameList.contains("Public"))
                    {
                        if(bsRPList.size() == 1)
                        {
                            type = 2;
                        }
                        else if(bsRPList.size() > 1)
                        {
                            type = 3;
                        }
                    }
                }
            }
        }

        if(type > 0)
        {
            if(log.isDebugEnabled())
            {
                System.out.println("-----------------------------------------------------");
                System.out.println("SETTING EMBARGO RIGHTS MDV INFORMATION");
                System.out.println("-----------------------------------------------------");
            }

            setEmbargoRightsMDV(context, item, type, true);
        }

        status = null;
        status = EmbargoManager.getEmbargoStatusMDV(context, item);

        if(length == null && EMBARGOED.equals(status))
        {
            if(log.isDebugEnabled())
            {
                System.out.println("-----------------------------------------------------");
                System.out.println("SETTING EMBARGO LENGTH MDV INFORMATION");
                System.out.println("-----------------------------------------------------");
            }

            // Set the value of the dc.embargo.length metadata field
            setEmbargoLengthMDV(context, item, true);
        }

        length = null;
        length = EmbargoManager.getEmbargoLengthMDV(context, item);

        if(enddate == null && length != null)
        {
            if(log.isDebugEnabled())
            {
                System.out.println("-----------------------------------------------------");
                System.out.println("SETTING EMBARGO END DATE MDV INFORMATION");
                System.out.println("-----------------------------------------------------");
            }

            // Set the value of the dc.embargo.enddate metadata field
            setEmbargoEndDateMDV(context, item, null, true);
        }

        // Since we've updated the metadata information for this item
        // we need to update its last modified date.
        item.updateLastModified();
    }

    /**
     *
     * @param context
     *          current DSpace context
     * @param liftDate
     *          lift date of the embargo
     * @param dso
     *          current DSpace object
     * @param type
     * @param owningCollection
     *          collection the bitstream is owned by
     *
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     */
    public static void generateETDEmbargoPolicies(Context context,
                                            Date liftDate,
                                            DSpaceObject dso,
                                            int type,
                                            Collection owningCollection)
        throws SQLException, AuthorizeException
    {
        List<ResourcePolicy> owningCollReadRPList = AuthorizeManager.getPoliciesActionFilter(context, owningCollection, Constants.READ);

        List<ResourcePolicy> dsoRPList = AuthorizeManager.getPoliciesActionFilter(context, dso, Constants.READ);

        // Remove any exisiting policies for this object
        // which have a value in their end date property
        for(ResourcePolicy dsoRP : dsoRPList)
        {
            if(dsoRP.getEndDate() != null)
            {
                dsoRP.delete();
            }
        }

        /**
         * If the user has chosen to hide the bitstream from the public only
         * then create a resource policy only for the Anonymous user group.
         */
        if(type == 2)
        {
            for(ResourcePolicy rp : owningCollReadRPList)
            {
                if(rp.getGroupID() == 0)
                {
                    ResourcePolicy bsRP = ResourcePolicy.create(context);
                    bsRP.setResourceID(dso.getID());
                    bsRP.setResourceType(dso.getType());
                    bsRP.setAction(rp.getAction());
                    bsRP.setEPerson(null);
                    bsRP.setStartDate(null);
                    bsRP.setEndDate(liftDate);
                    bsRP.setRpName(rp.getRpName());
                    bsRP.setRpType(rp.getRpType());
                    bsRP.setRpDescription(null);
                    bsRP.setGroup(rp.getGroup());
                    bsRP.update();

                    context.commit();

                    if(log.isDebugEnabled())
                    {
                        EmbargoManager.printRPInfo(context, bsRP);
                    }
                }
            }
        }
        else if(type == 3)
        {
            /**
             * Else if the submitter has chosen to restrict access
             * from everyone then create a resource policy record
             * for each policy in the policies list.
             */
            for(ResourcePolicy rp: owningCollReadRPList)
            {
                ResourcePolicy bsRP = ResourcePolicy.create(context);
                bsRP.setResourceID(dso.getID());
                bsRP.setResourceType(dso.getType());
                bsRP.setAction(rp.getAction());
                bsRP.setEPerson(null);
                bsRP.setStartDate(null);
                bsRP.setEndDate(liftDate);
                bsRP.setRpName(rp.getRpName());
                bsRP.setRpType(rp.getRpType());
                bsRP.setRpDescription(null);
                bsRP.setGroup(rp.getGroup());
                bsRP.update();

                context.commit();

                if(log.isDebugEnabled())
                {
                    EmbargoManager.printRPInfo(context, bsRP);
                }
            }
        }
        else
        {
            log.error(LogManager.getHeader(context, "Processing Embargo Type", " Embargo Type value is out of bounds. Type = "+type));
        }
    }

    /**
     *
     * @param context
     *      Current Dspace context
     * @param item
     *      Current item being worked on.
     * @param type
     *      Represents the type of embargo being enforced.
     * @param verbose
     *      Log debug information based on this parameter's value
     *
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    public static void setEmbargoRightsMDV(Context context, Item item, int type, boolean verbose)
        throws AuthorizeException, SQLException, IOException
    {
        String rightsValue = null;
        int RightsMDFID = EmbargoManager.getMetadataFieldID(context, "rights", null);

        /**
         * Creating a new metadata value for the item's dc.rights
         * metadata field.  The value assigned to the field is based
         * on whether the embargo applies to only authenticated
         * Auburn users or everyone (except system admins).  The
         * value could either be EMBARGO_NOT_AUBURN to represent that
         * the embargo applies to non-authenticated users or
         * EMBARGO_GLOBAL to represent that the embargo applies to
         * both authorized and non-authorized users. This restriction
         * do not apply to system administrators.  The creation of
         * this metadata field value is mostly for documentation
         * purposes only.  No other portion of the DSpace system's
         * functionality is reliant upon the value of this field.
         */

        if(type == 2)
        {
            rightsValue = EMBARGO_NOT_AUBURN_STR;
        }
        else if(type == 3)
        {
            rightsValue = EMBARGO_GLOBAL_STR;
        }

        EmbargoManager.CreateOrModifyEmbargoMetadataValue(context, item, "rights", null, rightsValue);

        if(verbose)
        {
            List<MetadataValue> rightsMDVList = MetadataValue.findByField(
                    context, RightsMDFID);
            if(!rightsMDVList.isEmpty())
            {
                for(MetadataValue rightsMDV : rightsMDVList)
                {
                    if(rightsMDV.getResourceId() == item.getID())
                    {
                        System.out.println("Embargo Rights Metadata Information");
                        System.out.println("-----------------------------------------------------");
                        EmbargoManager.printMDVInfo(context, rightsMDV);
                    }
                }
            }
        }
    }

    /**
     *
     * Sets the status of an item's embargo state.
     *
     * @param context
     *      Current Dspace context
     * @param item
     *      Information about the current submission
     * @param state
     *      State of the item's embargo status. Acceptable values: 0 or 1
     * @param verbose
     *      Log debug information based on this parameter's value
     *
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     */
    public static void setEmbargoStatusMDV(Context context, Item item, int state, boolean verbose)
        throws SQLException, IOException, AuthorizeException
    {
        String status = NOT_EMBARGOED; // By default the item should not be embargoed
        int EmbargoStatusMDFID = EmbargoManager.getMetadataFieldID(context, "embargo", "status");
        /**
         * If the value assigned to the parameter 'state' is 1 then
         * change of the value of status to EMBARGOED.
         */
        if(state == 1)
        {
            status = EMBARGOED;
        }

        /**
         * Create a new metadata value for an item's dc.embargo.status metadata
         * field. Set the value of the new metadata value entry to either Embargoed
         * if the item's bitstream is under embargo or NOT_EMBARGOED otherwise.
         * This process is solely for informational record keeping purposes and
         * has no bearing on any other portion of the DSpace system.
         */
        EmbargoManager.CreateOrModifyEmbargoMetadataValue(context, item, "embargo", "status", status);

        if(verbose)
        {
            List<MetadataValue> statusMDVList = MetadataValue.findByField(context, EmbargoStatusMDFID);
            if(!statusMDVList.isEmpty())
            {
                for(MetadataValue statusMDV : statusMDVList)
                {
                    if(statusMDV.getResourceId() == item.getID())
                    {
                        System.out.println("Embargo Status Metadata Information");
                        System.out.println("-----------------------------------------------------");
                        EmbargoManager.printMDVInfo(context, statusMDV);
                    }
                }
            }
        }
    }


    /**
     *
     * @param context
     *      Current DSpace context
     * @param item
     *      Current item being worked on.
     * @param verbose
     *      Log debug information based on this parameter's value
     *
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     */
    public static void setEmbargoLengthMDV(Context context, Item item, boolean verbose)
        throws SQLException, IOException, AuthorizeException
    {
        Date rpEndDate = null;
        int embargoLength = 0;
        String mdvStr = null;
        Months months = null;
        Days days = null;
        List<ResourcePolicy> bsRPList = null;
        int EmbargoLengthMDFID = EmbargoManager.getMetadataFieldID(context, "embargo", "length");

        for(Bundle bndl : item.getBundles(Constants.CONTENT_BUNDLE_NAME))
        {
            for(ResourcePolicy bsRP : bndl.getBitstreamPolicies())
            {
                if(bsRP.getEndDate() != null)
                {
                    rpEndDate = bsRP.getEndDate();
                }
                else
                {
                    log.error(LogManager.getHeader(context, "Getting Resource Policy End Date", " No end date was returned for policy id "+String.valueOf(bsRP.getID())));
                }
            }
        }

        if(rpEndDate != null)
        {
            DateTime sdt = new DateTime(EmbargoManager.getDateAccessionedMDV(context, item));
            DateTime edt = new DateTime(rpEndDate);

            months = Months.monthsBetween(sdt, edt);
            days = Days.daysBetween(sdt, edt);
        }

        if(months != null && days != null)
        {
            if(months.isLessThan(Months.months(1)))
            {
                embargoLength = days.getDays()+1;
                mdvStr = "DAYS_WITHHELD:"+embargoLength;
            }
            else
            {
                embargoLength = months.getMonths()+1;
                mdvStr = "MONTHS_WITHHELD:"+embargoLength;
            }
        }

        EmbargoManager.CreateOrModifyEmbargoMetadataValue(context, item, "embargo", "length", mdvStr);

        if(verbose)
        {
            List<MetadataValue> mdvList = MetadataValue.findByField(context, EmbargoLengthMDFID);
            if(!mdvList.isEmpty())
            {
                for(MetadataValue mdv : mdvList)
                {
                    if(mdv.getResourceId() == item.getID())
                    {
                        System.out.println("Embargo Length Metadata Information");
                        System.out.println("-----------------------------------------------------");
                        EmbargoManager.printMDVInfo(context, mdv);
                    }
                }
            }
        }
    }

    /**
     *
     * @param context
     *      Current context
     * @param item
     *      Current item
     * @param lDate
     * @param verbose
     *      Log debug information based on this parameter's value
     *
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     */
    public static void setEmbargoEndDateMDV(Context context, Item item, DateTime lDate, boolean verbose)
        throws SQLException, IOException, AuthorizeException
    {
        DateTimeFormatter dft = DateTimeFormat.forPattern("yyyy-MM-dd");
        int EmbargoEndDateMDFID = EmbargoManager.getMetadataFieldID(context, "embargo", "enddate");

        EmbargoManager.CreateOrModifyEmbargoMetadataValue(context, item, "embargo", "enddate", dft.print(lDate));

        if(verbose)
        {
            List<MetadataValue> mdvList = MetadataValue.findByField(context, EmbargoEndDateMDFID);
            if(!mdvList.isEmpty())
            {
                for(MetadataValue mdv : mdvList)
                {
                    if(mdv.getResourceId() == item.getID())
                    {
                        System.out.println("Embargo End Date Metadata Information");
                        System.out.println("-----------------------------------------------------");
                        log.debug(LogManager.getHeader(context, "", "Embargo End Date Metadata Information"));
                        log.debug(LogManager.getHeader(context, "", "-----------------------------------------------------"));
                        EmbargoManager.printMDVInfo(context, mdv);
                    }
                }
            }
        }
    }
    
    public static void setDateIssuedMDV(Context context, Item item, boolean verbose)
        throws SQLException, IOException, AuthorizeException
    {
        DateTimeFormatter dft = DateTimeFormat.forPattern("yyyy-MM-dd");
        int DateIssuedMDFID = EmbargoManager.getMetadataFieldID(context, "date", "issued");
        
        EmbargoManager.CreateOrModifyEmbargoMetadataValue(context, item, "date", "issued", dft.print(DateTime.now()));
        
        if(verbose)
        {
            List<MetadataValue> dateIssuedMDVList = MetadataValue.findByField(
                    context, DateIssuedMDFID);
            if(!dateIssuedMDVList.isEmpty())
            {
                for(MetadataValue dateIssuedMDV : dateIssuedMDVList)
                {
                    if(dateIssuedMDV.getResourceId() == item.getID())
                    {
                        System.out.println("Date Issued Metadata Information");
                        System.out.println("-----------------------------------------------------");
                        EmbargoManager.printMDVInfo(context, dateIssuedMDV);
                    }
                }
            }
        }
    }
    
    public static void generateMissingDateIssuedMDV(Context context, Item item, boolean verbose)
        throws SQLException, IOException, AuthorizeException
    {
        DateTimeFormatter dft = DateTimeFormat.forPattern("yyyy-MM-dd");
        int DateIssuedMDFID = EmbargoManager.getMetadataFieldID(context, "date", "issued");
        String dateAccessionedMDV = EmbargoManager.getDateAccessionedMDV(context, item);
        DateTime dateAccessioned = new DateTime(dateAccessionedMDV);
        
        if(log.isDebugEnabled())
        {
            System.out.println("-----------------------------------------------------");
            System.out.println("SETTING DATE ISSUED MDV INFORMATION");
            System.out.println("-----------------------------------------------------");
            
            System.out.println("Item's Date Accessioned MDV: "+dateAccessionedMDV);
            System.out.println("Item's New Issue Date: "+dft.print(dateAccessioned));
            System.out.println("-----------------------------------------------------");
        }
        
        EmbargoManager.CreateOrModifyEmbargoMetadataValue(context, item, "date", "issued", dft.print(dateAccessioned));
        
        // Since we've updated the metadata information for this item
        // we need to update its last modified date.
        context.turnOffAuthorisationSystem();
        item.updateLastModified();
        context.restoreAuthSystemState();
        
        if(verbose)
        {
            List<MetadataValue> dateIssuedMDVList = MetadataValue.findByField(
                    context, DateIssuedMDFID);
            if(!dateIssuedMDVList.isEmpty())
            {
                for(MetadataValue dateIssuedMDV : dateIssuedMDVList)
                {
                    if(dateIssuedMDV.getResourceId() == item.getID())
                    {
                        System.out.println("Date Issued Metadata Information");
                        System.out.println("-----------------------------------------------------");
                        EmbargoManager.printMDVInfo(context, dateIssuedMDV);
                    }
                }
            }
        }
    }
}
