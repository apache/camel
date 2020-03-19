/*
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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.http.common.DefaultHttpBinding;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * To use a HTTP Servlet as entry for Camel routes when running in a servlet container.
 */
@UriEndpoint(firstVersion = "2.0.0", scheme = "servlet", extendsScheme = "http", title = "Servlet",
        syntax = "servlet:contextPath", consumerOnly = true, label = "http")
@Metadata(excludeProperties = "httpUri")
public class ServletEndpoint extends HttpCommonEndpoint {

    private HttpBinding binding;

    @UriPath(label = "consumer") @Metadata(required = true)
    private String contextPath;
    @UriParam(label = "consumer", defaultValue = "CamelServlet")
    private String servletName;
    @UriParam(label = "consumer,advanced")
    private boolean attachmentMultipartBinding;
    @UriParam(label = "consumer,advanced")
    private String fileNameExtWhitelist;

    public ServletEndpoint() {
    }

    public ServletEndpoint(String endPointURI, ServletComponent component, URI httpUri) throws URISyntaxException {
        super(endPointURI, component, httpUri);
        this.contextPath = httpUri.getPath();
    }

    @Override
    public ServletComponent getComponent() {
        return (ServletComponent) super.getComponent();
    }

    @Override
    public HttpBinding getHttpBinding() {
        // make sure we include servlet variant of the http binding
        if (this.binding == null) {
            // is attachment binding enabled?
            if (isAttachmentMultipartBinding()) {
                this.binding = new AttachmentHttpBinding();
            } else {
                this.binding = new DefaultHttpBinding();
            }
            this.binding.setFileNameExtWhitelist(getFileNameExtWhitelist());
            this.binding.setTransferException(isTransferException());
            this.binding.setMuteException(isMuteException());
            if (getComponent() != null) {
                this.binding.setAllowJavaSerializedObject(getComponent().isAllowJavaSerializedObject());
            }
            this.binding.setHeaderFilterStrategy(getHeaderFilterStrategy());
            this.binding.setEagerCheckContentAvailable(isEagerCheckContentAvailable());
            this.binding.setMapHttpMessageBody(isMapHttpMessageBody());
            this.binding.setMapHttpMessageHeaders(isMapHttpMessageHeaders());
            this.binding.setMapHttpMessageFormUrlEncodedBody(isMapHttpMessageFormUrlEncodedBody());
        }
        return this.binding;
    }

    @Override
    public void setHttpBinding(HttpBinding binding) {
        super.setHttpBinding(binding);
        this.binding = binding;
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

    public boolean isAttachmentMultipartBinding() {
        return attachmentMultipartBinding;
    }

    /**
     * Whether to automatic bind multipart/form-data as attachments on the Camel {@link Exchange}.
     * <p/>
     * The options attachmentMultipartBinding=true and disableStreamCache=false cannot work together.
     * Remove disableStreamCache to use AttachmentMultipartBinding.
     * <p/>
     * This is turn off by default as this may require servlet specific configuration to enable this when using Servlet's.
     */
    public void setAttachmentMultipartBinding(boolean attachmentMultipartBinding) {
        this.attachmentMultipartBinding = attachmentMultipartBinding;
    }

    public String getFileNameExtWhitelist() {
        return fileNameExtWhitelist;
    }

    /**
     * Whitelist of accepted filename extensions for accepting uploaded files.
     * <p/>
     * Multiple extensions can be separated by comma, such as txt,xml.
     */
    public void setFileNameExtWhitelist(String fileNameExtWhitelist) {
        this.fileNameExtWhitelist = fileNameExtWhitelist;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("You cannot create producer with servlet endpoint, please consider to use http endpoint.");
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
