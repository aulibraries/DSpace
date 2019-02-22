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
import org.dspace.services.factory.DSpaceServicesFactory;
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
