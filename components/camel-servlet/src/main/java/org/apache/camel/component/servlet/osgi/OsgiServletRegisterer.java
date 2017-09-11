/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.servlet.osgi;

import java.util.Dictionary;
import java.util.Hashtable;
import javax.servlet.http.HttpServlet;

import org.apache.camel.util.ObjectHelper;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;

/**
 * Register the given (CamelHttpTransport) Servlet with the OSGI 
 * <a href="http://www.osgi.org/javadoc/r4v42/org/osgi/service/http/HttpService.html">
 * HttpService</a>
 * 
 * See src/test/resources/osgiservletregisterer.xml
 */
public class OsgiServletRegisterer {

    /**
     * The alias is the name in the URI namespace of the Http Service at which the registration will be mapped
     * An alias must begin with slash ('/') and must not end with slash ('/'), with the exception that an alias 
     * of the form "/" is used to denote the root alias.
     */
    private String alias;

    /**
     * The servlet name.
     */
    private String servletName = "CamelServlet";

    /**
     * Servlet to be registered
     */
    private HttpServlet servlet;
    
    /**
     * HttpService to register with. Get this with osgi:reference in the blueprint file
     */
    private HttpService httpService;
    
    private HttpContext httpContext;
    
    private boolean alreadyRegistered;

    // The servlet will default have to match on uri prefix as some endpoints may do so
    private volatile boolean matchOnUriPrefix = true;
    
    public void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public void setServletName(String servletName) {
        this.servletName = servletName;
    }

    public void setServlet(HttpServlet servlet) {
        this.servlet = servlet;
    }
    
    public void setHttpContext(HttpContext httpContext) {
        this.httpContext = httpContext;
    }

    public void setMatchOnUriPrefix(boolean matchOnUriPrefix) {
        this.matchOnUriPrefix = matchOnUriPrefix;
    }

    public void register() throws Exception {
        ObjectHelper.notEmpty(alias, "alias", this);
        ObjectHelper.notEmpty(servletName, "servletName", this);

        HttpContext actualHttpContext = (httpContext == null)
            ? httpService.createDefaultHttpContext()
            : httpContext;
        final Dictionary<String, String> initParams = new Hashtable<String, String>();
        initParams.put("matchOnUriPrefix", matchOnUriPrefix ? "true" : "false");
        initParams.put("servlet-name", servletName);
        httpService.registerServlet(alias, servlet, initParams, actualHttpContext);
        alreadyRegistered = true;
    }
 
    public void unregister() {
        if (alreadyRegistered) {
            httpService.unregister(alias);
            alreadyRegistered = false;
        }
    }

}
