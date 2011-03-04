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

import javax.annotation.Resource;
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
    private static final long serialVersionUID = -1797014782158930490L;
    private static final transient Logger LOG = LoggerFactory.getLogger(CamelHttpTransportServlet.class);
    
    @Resource
    private HttpRegistry httpRegistry;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        if (httpRegistry == null) {
            httpRegistry = DefaultHttpRegistry.getSingletonHttpRegistry();
        }
        httpRegistry.register(this);
        LOG.info("Initialized CamelHttpTransportServlet[{}]", getServletName());
    }
    
    @Override
    public void destroy() {
        httpRegistry.unregister(this);
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

}

