/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dspace.embargo;

import java.sql.SQLException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.*;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.*;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.core.Context;
import org.dspace.core.Constants;
import org.dspace.embargo.factory.EmbargoServiceFactory;
import org.dspace.embargo.service.EmbargoService;
import org.dspace.eperson.Group;
import org.dspace.license.CreativeCommonsServiceImpl;

/**
 *
 * @author stonema
 */
public class AUETDEmbargoSetter extends DefaultEmbargoSetter
{
    private static final Logger log = Logger.getLogger(AUETDEmbargoSetter.class);

    protected AuthorizeService authorizeService;
    protected ResourcePolicyService resourcePolicyService;
    protected EmbargoService embargoService;
    protected ContentServiceFactory serviceFactory;

    // Custom Constants Section
    public static final String EMBARGOED = "EMBARGOED";
    public static final String NOT_EMBARGOED = "NOT_EMBARGOED";
    public static final String EMBARGO_NOT_AUBURN_STR = "EMBARGO_NOT_AUBURN";
    public static final String EMBARGO_GLOBAL_STR = "EMBARGO_GLOBAL";

    public AUETDEmbargoSetter()
    {
        super();
    }

    @Override
    public DCDate parseTerms(Context context, Item item, String terms)
        throws AuthorizeException, SQLException
	{
        return null;
	}

    @Override
    public void setEmbargo(Context context, Item item)
        throws AuthorizeException, SQLException
	{
        // TODO: Add more code here
	}

    @Override
    public void checkEmbargo(Context context, Item item)
        throws AuthorizeException, IOException, SQLException
    {
        for (Bundle bn : item.getBundles())
        {
            // Skip the LICENSE and METADATA bundles, they stay world-readable
            String bnn = bn.getName();
            if (!(bnn.equals(Constants.LICENSE_BUNDLE_NAME) || bnn.equals(Constants.METADATA_BUNDLE_NAME) || bnn.equals(CreativeCommonsServiceImpl.CC_BUNDLE_NAME)))
            {
                // don't report on "TEXT" or "THUMBNAIL" bundles; those
                // can have READ as long as the bitstreams in them do not.
                if (!(bnn.equals("TEXT") || bnn.equals("THUMBNAIL")))
                {
                    // check for ANY read policies and report them:
                    for (ResourcePolicy resourcePolicy : getAuthorizeService().getPoliciesActionFilter(context, bn, Constants.READ))
                    {
                        System.out.println("CHECK WARNING: Item "+item.getHandle()+", Bundle "+bn.getName()+" allows READ by "+
                            ((resourcePolicy.getEPerson() == null) ? "Group "+resourcePolicy.getGroup().getName() :
                                                      "EPerson "+resourcePolicy.getEPerson().getFullName()));
                    }
                }
            }
        }
    }

    public void generateAUETDEmbargoPolicies(Context context, DSpaceObject dso, LocalDate embargoEndDate, int embargoType, Collection owningCollection) throws AuthorizeException, IOException, SQLException {
        List<ResourcePolicy> owningCollReadResourcePolicyList = getAuthorizeService().getPoliciesActionFilter(context, owningCollection, Constants.READ);
        List<ResourcePolicy> dsoResourcePolicyList = getAuthorizeService().getPoliciesActionFilter(context, dso, Constants.READ);

        // Remove all existing policies for this object.
        for(ResourcePolicy dsoResourcePolicy : dsoResourcePolicyList)
        {
            getResourcePolicyService().delete(context, dsoResourcePolicy);
        }

        if(embargoType == 2) {
            /**
             * If the user has chosen to hide the bitstream from the public only
             * then create a resource policy only for the Anonymous user group.
             */
            for(ResourcePolicy resourcePolicy : owningCollReadResourcePolicyList) {
                if(StringUtils.equals(resourcePolicy.getGroup().getName(), Group.ANONYMOUS)) {
                    ResourcePolicy newResourcePolicy = getResourcePolicyService().create(context);
                    newResourcePolicy.setAction(resourcePolicy.getAction());
                    newResourcePolicy.setdSpaceObject(dso);
                    newResourcePolicy.setGroup(resourcePolicy.getGroup());
                    newResourcePolicy.setEPerson(null);
                    newResourcePolicy.setRpName(resourcePolicy.getRpName());
                    newResourcePolicy.setRpType(resourcePolicy.getRpType());
                    newResourcePolicy.setStartDate(null);
                    newResourcePolicy.setEndDate(Date.from(Instant.from(embargoEndDate)));

                    getResourcePolicyService().update(context, newResourcePolicy);
                }
            }
        }
        else if (embargoType == 3) {
            /**
             * Else if the submitter has chosen to restrict access
             * from everyone then create a resource policy record
             * for each policy in the policies list.
             */
            for(ResourcePolicy resourcePolicy : owningCollReadResourcePolicyList) {
                ResourcePolicy newResourcePolicy = getResourcePolicyService().create(context);
                newResourcePolicy.setdSpaceObject(dso);
                newResourcePolicy.setAction(resourcePolicy.getAction());
                newResourcePolicy.setEPerson(null);
                newResourcePolicy.setGroup(resourcePolicy.getGroup());
                newResourcePolicy.setStartDate(null);
                newResourcePolicy.setEndDate(Date.from(Instant.from(embargoEndDate)));
                newResourcePolicy.setRpDescription(null);
                newResourcePolicy.setRpName(resourcePolicy.getRpName());
                newResourcePolicy.setRpType(resourcePolicy.getRpType());

                getResourcePolicyService().update(context, newResourcePolicy);
            }
        }
    }

    public void setEmbargoRightsMetadataValue(Context context, Item item, int type)
		throws AuthorizeException, IOException, SQLException
    {
        String rightsValue = null;

        if(type == 2)
        {
            rightsValue = EMBARGO_NOT_AUBURN_STR;
        }
        else if(type == 3)
        {
            rightsValue = EMBARGO_GLOBAL_STR;
        }

        getEmbargoService().CreateOrModifyEmbargoMetadataValue(context, item, "rights", null, rightsValue);
    }

    public void setEmbargoStatusMetadataValue(Context context, Item item, int state) throws AuthorizeException, IOException, SQLException {
        String status = NOT_EMBARGOED; // By default the item should not be embargoed

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
        getEmbargoService().CreateOrModifyEmbargoMetadataValue(context, item, "embargo", "status", status);
    }

    public void setEmbargoEndDateMetadataValue(Context context, Item item, LocalDate endDate) throws AuthorizeException, IOException, SQLException {
        getEmbargoService().CreateOrModifyEmbargoMetadataValue(context, item, "embargo", "enddate", endDate.toString());
    }

    public void setEmbargoLengthMetadataValue(Context context, Item item) throws AuthorizeException, IOException, SQLException {
        String embargoLength = generateEmbargoLength(context, item);

        if(!StringUtils.isNotEmpty(embargoLength)) {
            getEmbargoService().CreateOrModifyEmbargoMetadataValue(context, item, "embargo", "length", embargoLength);
        }
    }

    private String generateEmbargoLength(Context context, Item item) throws AuthorizeException, IOException, SQLException {
        Date resourcePolicyEndDate;
        long days = 0;
        long months = 0;
        String lengthStr = null;

        resourcePolicyEndDate = getEmbargoEndDate(context, item);

        if(resourcePolicyEndDate != null) {
            LocalDate embargoStartDate = LocalDate.parse(getEmbargoService().getEmbargoMetadataValue(context, item, "date", "accessioned"));
            Instant inst = resourcePolicyEndDate.toInstant();
            LocalDate embargoEndDate = LocalDate.from(inst);

            days = ChronoUnit.DAYS.between(embargoStartDate, embargoEndDate);
            months = ChronoUnit.MONTHS.between(embargoStartDate, embargoEndDate);
        }

        if(months > 0) {
            lengthStr = "MONTHS_WITHHELD:"+months;
        }
        else {
            lengthStr = "DAYS_WITHHELD:"+days;
        }

        return lengthStr;
    }
    
    private Date getEmbargoEndDate(Context context, Item item) throws AuthorizeException, SQLException {

        for(Bundle bndl : serviceFactory.getItemService().getBundles(item, Constants.DEFAULT_BUNDLE_NAME)) {
            for(Bitstream bs : bndl.getBitstreams()) {
                for(ResourcePolicy resourcePolicy : bs.getResourcePolicies()) {
                    if(resourcePolicy.getEndDate() != null) {
                        return resourcePolicy.getEndDate();
                    }
                }
            }
        }
        return null;
    }

    private AuthorizeService getAuthorizeService() {
        if(authorizeService == null)
        {
            authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
        }
        return authorizeService;
    }

    private ResourcePolicyService getResourcePolicyService() {
        if(resourcePolicyService == null)
        {
            resourcePolicyService = AuthorizeServiceFactory.getInstance().getResourcePolicyService();
        }
        return resourcePolicyService;
    }

    private EmbargoService getEmbargoService() {
        if(embargoService == null)
        {
            embargoService = EmbargoServiceFactory.getInstance().getEmbargoService();
        }
        return embargoService;
    }
}
