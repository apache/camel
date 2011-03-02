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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.component.http.CamelServlet;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.util.ObjectHelper;

/**
 * Default {@link CamelServletService}
 */
public class DefaultCamelServletService implements CamelServletService {

    private final String servletName;
    private final Set<HttpConsumer> consumers = new HashSet<HttpConsumer>();
    private CamelServlet camelServlet;

    public DefaultCamelServletService(String servletName, HttpConsumer consumer) {
        ObjectHelper.notEmpty(servletName, "ServletName");
        ObjectHelper.notNull(consumer, "HttpConsumer");
        this.servletName = servletName;
        addConsumer(consumer);
    }

    public DefaultCamelServletService(String servletName, CamelServlet camelServlet) {
        ObjectHelper.notEmpty(servletName, "ServletName");
        ObjectHelper.notNull(camelServlet, "CamelServlet");
        this.servletName = servletName;
        this.camelServlet = camelServlet;
    }

    public String getServletName() {
        return servletName;
    }

    public void addConsumer(HttpConsumer consumer) {
        consumers.add(consumer);
    }

    public void setCamelServlet(CamelServlet camelServlet) {
        this.camelServlet = camelServlet;
    }

    public void connect(HttpConsumer consumer) {
        if (camelServlet != null) {
            camelServlet.connect(consumer);
        }
    }

    public void disconnect(HttpConsumer consumer) {
        if (camelServlet != null) {
            camelServlet.disconnect(consumer);
        }
    }

    public Set<HttpConsumer> getConsumers() {
        return Collections.unmodifiableSet(consumers);
    }
}
