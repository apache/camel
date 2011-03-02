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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.apache.camel.component.http.CamelServlet;
import org.apache.camel.component.http.HttpConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camel HTTP servlet which can be used in Camel routes to route servlet invocations in routes.
 */
public class CamelHttpTransportServlet extends CamelServlet {
    private static final transient Logger LOG = LoggerFactory.getLogger(CamelHttpTransportServlet.class);
    private static final Map<String, CamelServletService> CAMEL_SERVLET_MAP = new ConcurrentHashMap<String, CamelServletService>();
    private String servletName;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        this.servletName = config.getServletName();
        // do we already know this servlet?
        CamelServletService service = CAMEL_SERVLET_MAP.get(servletName);

        // we cannot control the startup ordering, sometimes the Camel routes start first
        // other times the servlet, so we need to cater for both situations

        if (service == null) {
            // no we don't so create a new early service with this servlet
            service = new DefaultCamelServletService(servletName, this);
            CAMEL_SERVLET_MAP.put(servletName, service);
        } else {
            // use this servlet
            service.setCamelServlet(this);
            // and start the existing consumers we already have registered
            for (HttpConsumer consumer : service.getConsumers()) {
                connect(consumer);
            }
        }
        LOG.info("Initialized CamelHttpTransportServlet[" + servletName + "]");
    }
    
    public void destroy() {
        CAMEL_SERVLET_MAP.remove(servletName);
        LOG.info("Destroyed CamelHttpTransportServlet[" + servletName + "]");
    }
    
    public static synchronized CamelServletService getCamelServletService(String servletName, HttpConsumer consumer) {
        // we cannot control the startup ordering, sometimes the Camel routes start first
        // other times the servlet, so we need to cater for both situations

        CamelServletService answer = CAMEL_SERVLET_MAP.get(servletName);
        if (answer == null) {
            answer = new DefaultCamelServletService(servletName, consumer);
            CAMEL_SERVLET_MAP.put(servletName, answer);
        } else {
            answer.addConsumer(consumer);
        }
        return answer;
    }

    @Override
    public String getServletName() {
        return servletName;
    }

}

