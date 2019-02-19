/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.eperson;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.UUID;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;

import org.apache.cocoon.caching.CacheableProcessingComponent;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.excalibur.source.SourceValidity;
import org.apache.excalibur.source.impl.validity.NOPValidity;
import org.apache.jena.atlas.io.IO;
import org.apache.log4j.Logger;
import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.AuthenticationUtil;
import org.dspace.app.xmlui.utils.HandleUtil;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.Item;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.app.xmlui.wing.element.PageMeta;
import org.dspace.app.xmlui.wing.element.Password;
import org.dspace.app.xmlui.wing.element.Text;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.xml.sax.SAXException;

import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.content.*;
import org.dspace.content.service.*;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.core.Constants;
import org.dspace.embargo.AUETDEmbargoSetter;
import org.dspace.embargo.factory.EmbargoServiceFactory;
import org.dspace.embargo.service.EmbargoService;

/**
 * Query the user for their authentication credentials.
 * 
 * The parameter "return-url" may be passed to give a location where to redirect
 * the user to after successfully authenticating.
 * 
 * @author Jay Paz
 */
public class LDAPLogin extends AbstractDSpaceTransformer implements
		CacheableProcessingComponent {
	/** language strings */
	public static final Message T_title = message("xmlui.EPerson.LDAPLogin.title");

	public static final Message T_dspace_home = message("xmlui.general.dspace_home");

	public static final Message T_trail = message("xmlui.EPerson.LDAPLogin.trail");

	public static final Message T_head1 = message("xmlui.EPerson.LDAPLogin.head1");

	public static final Message T_userName = message("xmlui.EPerson.LDAPLogin.username");

	public static final Message T_error_bad_login = message("xmlui.EPerson.LDAPLogin.error_bad_login");

	public static final Message T_password = message("xmlui.EPerson.LDAPLogin.password");

	public static final Message T_submit = message("xmlui.EPerson.LDAPLogin.submit");

	private static final Logger log = Logger.getLogger(LDAPLogin.class);

    protected BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    protected AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();

    protected EmbargoService embargoService = EmbargoServiceFactory.getInstance().getEmbargoService();

    protected ItemService itemService = ContentServiceFactory.getInstance().getItemService();

	/**
	 * Generate the unique caching key. This key must be unique inside the space
	 * of this component.
	 */
	public Serializable getKey() {
		Request request = ObjectModelHelper.getRequest(objectModel);
		String previous_username = request.getParameter("username");

		// Get any message parameters
		HttpSession session = request.getSession();
		String header = (String) session
				.getAttribute(AuthenticationUtil.REQUEST_INTERRUPTED_HEADER);
		String message = (String) session
				.getAttribute(AuthenticationUtil.REQUEST_INTERRUPTED_MESSAGE);
		String characters = (String) session
				.getAttribute(AuthenticationUtil.REQUEST_INTERRUPTED_CHARACTERS);

		// If there is a message or previous email attempt then the page is not
		// cachable
		if (header == null && message == null && characters == null
				&& previous_username == null)
        {
            // cacheable
            return "1";
        }
		else
        {
            // Uncachable
            return "0";
        }
	}

	/**
	 * Generate the cache validity object.
	 */
	public SourceValidity getValidity() {
		Request request = ObjectModelHelper.getRequest(objectModel);
		String previous_username = request.getParameter("username");

		// Get any message parameters
		HttpSession session = request.getSession();
		String header = (String) session
				.getAttribute(AuthenticationUtil.REQUEST_INTERRUPTED_HEADER);
		String message = (String) session
				.getAttribute(AuthenticationUtil.REQUEST_INTERRUPTED_MESSAGE);
		String characters = (String) session
				.getAttribute(AuthenticationUtil.REQUEST_INTERRUPTED_CHARACTERS);

		// If there is a message or previous email attempt then the page is not
		// cachable
		if (header == null && message == null && characters == null
				&& previous_username == null)
        {
            // Always valid
            return NOPValidity.SHARED_INSTANCE;
        }
		else
        {
            // invalid
            return null;
        }
	}

	/**
	 * Set the page title and trail.
	 */
	public void addPageMeta(PageMeta pageMeta) throws AuthorizeException, IOException, SQLException, WingException {

        Request request = ObjectModelHelper.getRequest(objectModel);
		String bitstreamIDStr = getBitstreamId(request);

		pageMeta.addMetadata("title").addContent(T_title);

        if(StringUtils.isNotBlank(bitstreamIDStr)) {
			Bitstream bitstream = bitstreamService.find(context, UUID.fromString(bitstreamIDStr));
        
            if(authorizeService.isAccessRestrictedToAllNonAdminUsers(context, bitstream))
            {
                pageMeta.addMetadata("view-account-nav").addContent("false");
            }
            else
            {
                pageMeta.addMetadata("view-account-nav").addContent("true");
            }
        } else {
            pageMeta.addMetadata("view-account-nav").addContent("true");
        }

		pageMeta.addTrailLink(contextPath + "/", T_dspace_home);
		pageMeta.addTrail().addContent(T_trail);
	}

	/**
	 * Display the login form.
	 */
	public void addBody(Body body) throws AuthorizeException, IOException,
		SQLException, SAXException, WingException 
	{
		// Check if the user has previously attempted to login.
		Request request = ObjectModelHelper.getRequest(objectModel);
		HttpSession session = request.getSession();
		String previousUserName = request.getParameter("username");

		// Get any message parameters
		String header = (String) session
				.getAttribute(AuthenticationUtil.REQUEST_INTERRUPTED_HEADER);
		String message = (String) session
				.getAttribute(AuthenticationUtil.REQUEST_INTERRUPTED_MESSAGE);
		String characters = (String) session
				.getAttribute(AuthenticationUtil.REQUEST_INTERRUPTED_CHARACTERS);

		// Custom code
		String embargoRights = null;
		org.dspace.content.Item item = null;
		String bitstreamIDStr = getBitstreamId(request);

		if(StringUtils.isNotBlank(bitstreamIDStr))
        {
            Bitstream bitstream = bitstreamService.find(context, UUID.fromString(bitstreamIDStr));
			DSpaceObject parent = bitstreamService.getParentObject(context, bitstream);

            if(parent instanceof org.dspace.content.Item)
            {
				item = itemService.find(context, parent.getID());

                embargoRights = embargoService.getEmbargoMetadataValue(context, item, "rights", null);
            }
        }

		if (header != null || message != null || characters != null) {
			Division reason = body.addDivision("login-reason");

			if (header != null)
            {
                reason.setHead(message(header));
            }
			else
            {
                // Always have a head.
                reason.setHead("Authentication Required");
            }

			if (message != null)
            {
                reason.addPara(message(message));
            }

			if (characters != null)
            {
                reason.addPara(characters);
            }
		}

		// Display the login form when an item's bitstream is not restricted or is restricted to authenticated Auburn users.
		if (StringUtils.isBlank(embargoRights) || authorizeService.isAccessRestrictedToNonAdminAuburnUsers(context, item))
		{
			Division login = body.addInteractiveDivision("login", contextPath
					+ "/ldap-login", Division.METHOD_POST, "primary");
			login.setHead(T_head1);

			List list = login.addList("ldap-login", List.TYPE_FORM);

			Text email = list.addItem().addText("username");
			email.setRequired();
			email.setAutofocus("autofocus");
			email.setLabel(T_userName);
			if (previousUserName != null) {
				email.setValue(previousUserName);
				email.addError(T_error_bad_login);
			}

			Item displayItem = list.addItem();
			Password password = displayItem.addPassword("ldap_password");
			password.setRequired();
			password.setLabel(T_password);

			list.addLabel();
			Item submit = list.addItem("login-in", null);
			submit.addButton("submit").setValue(T_submit);
		}
	}

    private String getBitstreamId(Request request)
    {
        String qStr = request.getQueryString();
		String bitstreamIDStr = null;
		
		if(StringUtils.isNotBlank(qStr))
		{
            if (qStr.contains("=")) {
                String[] qStrArray = qStr.split("=");

                if(qStrArray.length > 1)
                {
                    if((StringUtils.isNotBlank(qStrArray[0]) && qStrArray[0].equals("bitstreamId")) && StringUtils.isNotBlank(qStrArray[1]))
                    {
                        bitstreamIDStr = qStrArray[1];
                    }
                }
            }
		}
        return bitstreamIDStr;        
    }
}
