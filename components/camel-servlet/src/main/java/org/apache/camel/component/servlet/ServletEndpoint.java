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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

@UriEndpoint(scheme = "servlet", extendsScheme = "http", title = "Servlet",
        syntax = "servlet:contextPath", consumerOnly = true, consumerClass = ServletConsumer.class, label = "http")
public class ServletEndpoint extends HttpCommonEndpoint {

    @UriPath(label = "consumer") @Metadata(required = "true")
    private String contextPath;

    @UriParam(label = "consumer", defaultValue = "CamelServlet")
    private String servletName;

    public ServletEndpoint() {
    }

    public ServletEndpoint(String endPointURI, ServletComponent component, URI httpUri) throws URISyntaxException {
        super(endPointURI, component, httpUri);
        this.contextPath = httpUri.getPath();
    }

    public String getContextPath() {
        return contextPath;
    }

    /**
     * The context-path to use
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * Name of the servlet to use
     */
    public void setServletName(String name) {
        servletName = name;
    }

    public String getServletName() {
        return servletName;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("You cannot create producer with servlet endpoint, please consider to use http or http4 endpoint.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ServletConsumer answer = new ServletConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public boolean isLenientProperties() {
        // in contrast to the HttpEndpoint, the ServletEndpoint knows about all it's options on the passed URI
        return false;
    }

}
