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
package org.apache.camel.component.jetty12;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachment;
import org.apache.camel.component.jetty.MultiPartFilter;
import org.apache.camel.http.common.DefaultHttpBinding;
import org.apache.camel.http.common.HttpHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To handle attachments with Jetty 12.
 * <p/>
 * This implementation is needed to deal with attachments when using Jetty 12.
 */
final class AttachmentHttpBinding extends DefaultHttpBinding {
    private static final Logger LOG = LoggerFactory.getLogger(AttachmentHttpBinding.class);

    AttachmentHttpBinding() {
    }

    @Override
    protected void populateAttachments(HttpServletRequest request, Message message) {
        Boolean object = (Boolean) request.getAttribute(MultiPartFilter.MULTIPART);
        if (object != null && object) {
            Collection<Part> parts;
            try {
                parts = request.getParts();
                for (Part part : parts) {
                    DataSource ds = new PartDataSource(part);
                    Attachment attachment = new DefaultAttachment(ds);
                    for (String headerName : part.getHeaderNames()) {
                        for (String headerValue : part.getHeaders(headerName)) {
                            attachment.addHeader(headerName, headerValue);
                        }
                    }
                    AttachmentMessage am = message.getExchange().getMessage(AttachmentMessage.class);
                    am.addAttachmentObject(part.getName(), attachment);
                    String name = part.getSubmittedFileName();
                    Object value = am.getAttachment(name);
                    Map<String, Object> headers = message.getHeaders();
                    if (getHeaderFilterStrategy() != null
                            && !getHeaderFilterStrategy().applyFilterToExternalHeaders(name, value, message.getExchange())
                            && name != null) {
                        HttpHelper.appendHeader(headers, name, value);
                    }

                }
            } catch (Exception e) {
                throw new RuntimeCamelException("Cannot populate attachments", e);
            }
        }
    }

    @Override
    protected void populateRequestParameters(HttpServletRequest request, Message message) {
        // we populate the http request parameters without checking the request
        // method
        Map<String, Object> headers = message.getHeaders();
        // remove Content-Encoding from request
        // TODO in Jetty 12, HttpFields cannot be removed
        //        if (request instanceof ServletApiRequest) {
        //            Iterator<HttpField> httpFieldIterator = ((ServletApiRequest) request).getRequest().getHeaders().iterator();
        //            while (httpFieldIterator.hasNext()) {
        //                HttpField httpField = httpFieldIterator.next();
        //                if (httpField.is(Exchange.CONTENT_ENCODING)) {
        //                    httpFieldIterator.remove();
        //                }
        //            }
        //        }

        // attachment is optional
        AttachmentMessage am = message.getExchange().getMessage(AttachmentMessage.class);

        Enumeration<?> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            if (am != null && am.getAttachment(name) != null) {
                DataHandler dh = am.getAttachment(name);
                Object value = dh;
                if (dh.getContentType() == null || dh.getContentType().startsWith("text/plain")) {
                    value = request.getParameter(name);
                }
                if (getHeaderFilterStrategy() != null
                        && !getHeaderFilterStrategy().applyFilterToExternalHeaders(name, value, message.getExchange())) {
                    HttpHelper.appendHeader(headers, name, value);
                }
                continue;
            }

            // there may be multiple values for the same name
            String[] values = request.getParameterValues(name);
            LOG.trace("HTTP parameter {} = {}", name, values);

            if (values != null) {
                for (String value : values) {
                    if (getHeaderFilterStrategy() != null
                            && !getHeaderFilterStrategy().applyFilterToExternalHeaders(name, value, message.getExchange())) {
                        HttpHelper.appendHeader(headers, name, value);
                    }
                }
            }
        }
    }

    static final class PartDataSource implements DataSource {
        private final Part part;

        PartDataSource(Part part) {
            this.part = part;
        }

        @Override
        public OutputStream getOutputStream() {
            return null;
        }

        @Override
        public String getName() {
            return part.getName();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return part.getInputStream();
        }

        @Override
        public String getContentType() {
            return part.getContentType();
        }
    }
}
