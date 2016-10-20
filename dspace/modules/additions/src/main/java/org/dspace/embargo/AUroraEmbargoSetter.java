/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.embargo;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.license.CreativeCommons;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 *
 * @author STONEMA
 */
public class AUroraEmbargoSetter implements EmbargoSetter
{
    protected String termsOpen = null;

    private static final Logger log = Logger.getLogger(AUroraEmbargoSetter.class);

    public AUroraEmbargoSetter()
    {
        super();
        termsOpen = ConfigurationManager.getProperty("embargo.terms.open");
    }

    /**
     * Parse the terms into a definite date. Terms are expected to consist of
     * either: a token (value configured in 'embargo.terms.open' property) to indicate
     * indefinite embargo, or a literal lift date formatted in ISO 8601 format (yyyy-mm-dd)
     *
     * @param context the DSpace context
     * @param item the item to embargo
     * @param terms the embargo terms
     * @return parsed date in DCDate format
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     */
    @Override
    public DCDate parseTerms(Context context, Item item, String terms)
        throws SQLException, AuthorizeException, IOException
    {
        return null;
    }

    /**
     * Set up an embargo on the specified item.
     *
     * @param context
     *      the DSpace context
     * @param item
     *      the item to embargo
     *
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     */
    @Override
    public void setEmbargo(Context context, Item item)
        throws SQLException, AuthorizeException, IOException
    {
        if(getItemRPStartDate(context, item) != null)
        {
            setEmbargoEndDateMDV(context, item, getItemRPStartDate(context, item));
            item.setDiscoverable(false);
        }
    }

    /**
     * Check that embargo is properly set on Item: no read access to bitstreams.
     *
     * @param context
     *      the DSpace context
     * @param item
     *      the item to embargo
     *
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     */
    @Override
    public void checkEmbargo(Context context, Item item)
        throws SQLException, AuthorizeException, IOException
    {
        for (Bundle bn : item.getBundles())
        {
            // Skip the LICENSE and METADATA bundles, they stay world-readable
            String bnn = bn.getName();
            if (!(bnn.equals(Constants.LICENSE_BUNDLE_NAME) || bnn.equals(Constants.METADATA_BUNDLE_NAME) || bnn.equals(CreativeCommons.CC_BUNDLE_NAME)))
            {
                // don't report on "TEXT" or "THUMBNAIL" bundles; those
                // can have READ long as the bitstreams in them do not.
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
                        System.out.println("CHECK WARNING: Item "+item.getHandle()+", Bitstream "+bs.getName()+" (in Bundle "+bn.getName()+") allows READ by "+
                          ((rp.getEPersonID() < 0) ? "Group "+rp.getGroup().getName() :
                                                      "EPerson "+rp.getEPerson().getFullName()));
                    }
                }
            }
        }
    }

    /**
     * Creates a new or modifies an existing dc.embargo.enddate metadata field value.
     *
     * @param context
     *      Current context
     * @param item
     *      Current item
     * @param eDate
     *      The date the policy will end.
     *
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorize.AuthorizeException
     */
    public static void setEmbargoEndDateMDV(Context context, Item item, DateTime eDate)
        throws SQLException, IOException, AuthorizeException
    {
        DateTime endDate = null;
        DateTimeFormatter dft = DateTimeFormat.forPattern("yyyy-MM-dd");
        int embargoEndDateMDFID = 0;

        embargoEndDateMDFID = EmbargoManager.getMetadataFieldID(context, "embargo", "enddate");

        // If the item already has its dc.embargo.enddate metadata field set then
        // make sure it's different than the value of eDate. If so then
        // update the
        if(embargoEndDateMDFID <= 0 || EmbargoManager.getEmbargoEndDateMDV(context, item) == null)
        {
            MetadataValue endDateMDV = new MetadataValue();

            endDateMDV.setFieldId(embargoEndDateMDFID);
            endDateMDV.setResourceId(item.getID());
            endDateMDV.setResourceTypeId(item.getType());
            endDateMDV.setValue(eDate.toString(dft));
            endDateMDV.setLanguage("en_US");
            endDateMDV.setPlace(1);
            endDateMDV.setAuthority(null);
            endDateMDV.setConfidence(-1);
            endDateMDV.create(context);

            context.commit();
        }
        else
        {
            if(embargoEndDateMDFID > 0 && EmbargoManager.getEmbargoEndDateMDV(context, item) != null)
            {
                List<MetadataValue> mdvList = MetadataValue.findByField(context, embargoEndDateMDFID);
                if(!mdvList.isEmpty())
                {
                    for(MetadataValue mdv : mdvList)
                    {
                        if(mdv.getResourceId() == item.getID())
                        {
                            mdv.setValue(eDate.toString(dft));
                            mdv.setResourceTypeId(item.getType());
                            mdv.update(context);

                            context.commit();
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns either the starting date listed in the specified item's
     * resource policy typed as a DateTime object instance or null.
     *
     * @param contex
     *      Current DSpace context
     * @param item
     *      Current item
     * @return
     *
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     * @throws java.sql.SQLException
     */
    private static DateTime getItemRPStartDate(Context context, Item item)
        throws AuthorizeException, IOException, SQLException
    {
        for(ResourcePolicy itemRP : AuthorizeManager.findPoliciesByDSOAndType(context, item, ResourcePolicy.TYPE_CUSTOM))
        {
            if(itemRP.getStartDate() != null)
            {
                return new DateTime(itemRP.getStartDate());
            }
        }

        return null;
    }
}
