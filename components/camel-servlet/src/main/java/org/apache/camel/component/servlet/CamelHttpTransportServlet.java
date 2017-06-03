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

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.http.common.CamelServlet;
import org.apache.camel.http.common.HttpConsumer;
import org.apache.camel.http.common.HttpRestServletResolveConsumerStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camel HTTP servlet which can be used in Camel routes to route servlet invocations in routes.
 */
public class CamelHttpTransportServlet extends CamelServlet {
    private static final long serialVersionUID = -1797014782158930490L;
    private static final Logger LOG = LoggerFactory.getLogger(CamelHttpTransportServlet.class);

    private HttpRegistry httpRegistry;
    private boolean ignoreDuplicateServletName;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        // use rest enabled resolver in case we use rest
        this.setServletResolveConsumerStrategy(new HttpRestServletResolveConsumerStrategy());

        String ignore = config.getInitParameter("ignoreDuplicateServletName");
        Boolean bool = ObjectConverter.toBoolean(ignore);
        if (bool != null) {
            ignoreDuplicateServletName = bool;
        } else {
            // always log so people can see it easier
            String msg = "Invalid parameter value for init-parameter ignoreDuplicateServletName with value: " + ignore;
            LOG.error(msg);
            throw new ServletException(msg);
        }

        String name = config.getServletName();
        String contextPath = config.getServletContext().getContextPath();

        if (httpRegistry == null) {
            httpRegistry = DefaultHttpRegistry.getHttpRegistry(name);
            CamelServlet existing = httpRegistry.getCamelServlet(name);
            if (existing != null) {
                String msg = "Duplicate ServletName detected: " + name + ". Existing: " + existing + " This: " + this.toString()
                        + ". Its advised to use unique ServletName per Camel application.";
                // always log so people can see it easier
                if (isIgnoreDuplicateServletName()) {
                    LOG.warn(msg);
                } else {
                    LOG.error(msg);
                    throw new ServletException(msg);
                }
            }
            httpRegistry.register(this);
        }

        LOG.info("Initialized CamelHttpTransportServlet[name={}, contextPath={}]", getServletName(), contextPath);
    }
    
    @Override
    public void destroy() {
        DefaultHttpRegistry.removeHttpRegistry(getServletName());
        if (httpRegistry != null) {
            httpRegistry.unregister(this);
            httpRegistry = null;
        }
        LOG.info("Destroyed CamelHttpTransportServlet[{}]", getServletName());
    }
    
    private ServletEndpoint getServletEndpoint(HttpConsumer consumer) {
        if (!(consumer.getEndpoint() instanceof ServletEndpoint)) {
            throw new RuntimeException("Invalid consumer type. Must be ServletEndpoint but is " 
                    + consumer.getClass().getName());
        }
        return (ServletEndpoint)consumer.getEndpoint();
    }

    @Override
    public void connect(HttpConsumer consumer) {
        ServletEndpoint endpoint = getServletEndpoint(consumer);
        if (endpoint.getServletName() != null && endpoint.getServletName().equals(getServletName())) {
            super.connect(consumer);
        }
    }

    public boolean isIgnoreDuplicateServletName() {
        return ignoreDuplicateServletName;
    }

    @Override
    public String toString() {
        String name = getServletName();
        if (name != null) {
            return "CamelHttpTransportServlet[name=" + getServletName() + "]";
        } else {
            return "CamelHttpTransportServlet";
        }
    }
}

