package org.apache.camel.component.servlet.osgi;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.Lifecycle;

/**
 * Register the given (CamelHttpTransport) Servlet with the OSGI 
 * <a href="http://www.osgi.org/javadoc/r4v42/org/osgi/service/http/HttpService.html">
 * HttpService</a>
 */
public class OsgiServletRegisterer implements Lifecycle, InitializingBean {
    /**
     * The alias is the name in the URI namespace of the Http Service at which the registration will be mapped
     * An alias must begin with slash ('/') and must not end with slash ('/'), with the exception that an alias 
     * of the form "/" is used to denote the root alias.
     */
    private String alias;

    /**
     * Servlet to be registered
     */
    private HttpServlet servlet;
    
    /**
     * HttpService to register with. Get this with osgi:reference in the spring
     * context
     */
    private HttpService httpService;
    
    private HttpContext httpContext;
    
    private boolean alreadyRegistered;
    
    public void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }
    public void setAlias(String alias) {
        this.alias = alias;
    }

    public void setServlet(HttpServlet servlet) {
        this.servlet = servlet;
    }
    
    public void setHttpContext(HttpContext httpContext) {
        this.httpContext = httpContext;
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        HttpContext actualHttpContext = (httpContext == null) 
            ? httpService.createDefaultHttpContext()
            : httpContext;
        final Dictionary<String, String> initParams = new Hashtable<String, String>();
        // The servlet will always have to match on uri prefix as some endpoints may do so
        initParams.put("matchOnUriPrefix", "true");
        
        try {
            String servletName = servlet.getServletName();
            initParams.put("servlet-name", servletName);
        } catch (Exception e) {
            // If getServletName is not implemented the default is to throw an exception
            // In this case we simply do not set a servlet name
        }
        
        httpService.registerServlet(alias, servlet, initParams, actualHttpContext);
        alreadyRegistered = true;
    }
    
    @Override
    public void start() {
    }
    
    @Override
    public void stop() {
        if (alreadyRegistered) {
            httpService.unregister(alias);
            alreadyRegistered = false;
        }
    }
    
    @Override
    public boolean isRunning() {
        return alreadyRegistered;
    }
    
}
