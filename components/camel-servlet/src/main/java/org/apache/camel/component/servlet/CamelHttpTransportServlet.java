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
package org.apache.camel.component.servlet;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.apache.camel.component.http.CamelServlet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CamelHttpTransportServlet extends CamelServlet implements CamelServletService {
    private static final transient Log LOG = LogFactory.getLog(CamelHttpTransportServlet.class);
    private static final Map<String, CamelServlet> CAMEL_SERVLET_MAP = new ConcurrentHashMap<String, CamelServlet>();
    private String servletName;
    private AbstractApplicationContext applicationContext;
    
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servletName = config.getServletName();
        // parser the servlet init parameters
        CAMEL_SERVLET_MAP.put(servletName, this);
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        if (contextConfigLocation != null) {
            //Create a spring application context for it
            applicationContext = new ClassPathXmlApplicationContext(contextConfigLocation.split(","));
            LOG.info("Started the application context rightly");
        }
    }
    
    public void destroy() {        
        if (applicationContext != null) {
            applicationContext.stop();
        }
        // Need to remove the servlet from map after 
        // the ApplicationContext is removed
        CAMEL_SERVLET_MAP.remove(servletName);
    }
    
    public static CamelServlet getCamelServlet(String servletName) {
        CamelServlet answer = null;
        if (servletName != null) {
            answer = CAMEL_SERVLET_MAP.get(servletName);
        } else {
            if (CAMEL_SERVLET_MAP.size() > 0) {
                // return the first one servlet
                Iterator<CamelServlet> iterator = CAMEL_SERVLET_MAP.values().iterator();
                answer = iterator.next();
                LOG.info("Since no servlet name is specified, using the first element of camelServlet map [" + answer.getServletName() + "]");
            }
        }        
        return answer;
    }
    
}

