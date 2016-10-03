/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

 package org.dspace.app.xmlui.aspect.eperson;

 // Java class imports
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// Apache class imports
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.acting.AbstractAction;
import org.apache.cocoon.environment.Redirector;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.environment.http.HttpEnvironment;
import org.apache.log4j.Logger;

// DSpace class imports
import org.apache.cocoon.environment.http.HttpEnvironment;
import org.dspace.app.xmlui.utils.ContextUtil;
import org.dspace.authenticate.AuthenticationManager;
import org.dspace.authenticate.AuthenticationMethod;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.LogManager;

/**
 * When only one login method is defined in the dspace.cfg file this class will
 * redirect to the URL provided by that AuthenticationMethod class
 * 
 * @author Jay Paz
 * @author Scott Phillips
 * 
 */
public class LoginRedirect extends AbstractAction 
{
    private static final Logger log = Logger.getLogger(LoginRedirect.class);
    
    /**
     * 
     * @param redirector
     * @param resolver
     * @param objectModel
     * @param source
     * @param parameters
     * @return
     * @throws Exception 
     */
    @Override
    public Map act(Redirector redirector, SourceResolver resolver, Map objectModel, String source, Parameters parameters)
        throws Exception 
    {
        final HttpServletResponse httpResponse = (HttpServletResponse) objectModel.get(HttpEnvironment.HTTP_RESPONSE_OBJECT);
        final HttpServletRequest httpRequest = (HttpServletRequest) objectModel.get(HttpEnvironment.HTTP_REQUEST_OBJECT);
        final Iterator<AuthenticationMethod> authMethods = AuthenticationManager.authenticationMethodIterator();

        if (authMethods == null)
        {
            throw new IllegalStateException("No explicit authentication methods found when exactly one was expected.");
        }

        AuthenticationMethod authMethod = null;

        while (authMethods.hasNext())
        {
            AuthenticationMethod currAuthMethod = authMethods.next();
            if (currAuthMethod.loginPageURL(ContextUtil.obtainContext(objectModel), httpRequest, httpResponse) != null)
            {
                if (authMethod != null)
                {
                    throw new IllegalStateException("Multiple explicit authentication methods found when only one was expected.");
                }
                authMethod = currAuthMethod;
            }
        }

        String url = null;
       
        if(authMethod != null)
        {
            /*log.debug(LogManager.getHeader(ContextUtil.obtainContext(objectModel), "Login Auth Method", " "+authMethod));
            log.debug(LogManager.getHeader(ContextUtil.obtainContext(objectModel), "HTTP Request Info", " Request URL = "+httpRequest.getRequestURI()));
            log.debug(LogManager.getHeader(ContextUtil.obtainContext(objectModel), "HTTP Request Info", " Query String = "+httpRequest.getQueryString()));
            log.debug(LogManager.getHeader(ContextUtil.obtainContext(objectModel), "HTTP Request Info", " Method = "+httpRequest.getMethod()));*/

            url = ((AuthenticationMethod) authMethod).loginPageURL(ContextUtil.obtainContext(objectModel), httpRequest, httpResponse);
        }
       
        if(url != null)
        {
            //log.debug(LogManager.getHeader(ContextUtil.obtainContext(objectModel), "Login Redirect URL", " "+url+"?"+httpRequest.getQueryString()));

            String qStr = httpRequest.getQueryString();

            // now we want to check for the force ssl property
            if (ConfigurationManager.getBooleanProperty("xmlui.force.ssl")) 
            {
                if (!httpRequest.isSecure()) 
                {
                    StringBuilder location = new StringBuilder("https://");
                    location.append(ConfigurationManager.getProperty("dspace.hostname")).append(url);
                    location.append(qStr == null ? "" : ("?" + httpRequest.getQueryString()));

                    log.debug(LogManager.getHeader(ContextUtil.obtainContext(objectModel), "Login Redirect URL", " Original URL = "+url));
                    log.debug(LogManager.getHeader(ContextUtil.obtainContext(objectModel), "Login Redirect URL", " Modfied URL = "+location.toString()));
                    httpResponse.sendRedirect(location.toString());
                }
                else
                {
                   log.debug(LogManager.getHeader(ContextUtil.obtainContext(objectModel), "Login Redirect URL", " URL = "+url+(qStr == null ? "" : ("?" + httpRequest.getQueryString()))));
                   httpResponse.sendRedirect(url+(qStr == null ? "" : ("?" + httpRequest.getQueryString())));
                }
            }
            else
            {
                log.debug(LogManager.getHeader(ContextUtil.obtainContext(objectModel), "Login Redirect URL", " URL = "+url+(qStr == null ? "" : ("?" + httpRequest.getQueryString()))));
               httpResponse.sendRedirect(url+(qStr == null ? "" : ("?" + httpRequest.getQueryString())));
            }
        }

        return new HashMap();
    }
    
}
