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
import java.util.List;
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
import org.apache.camel.attachment.DefaultAttachmentMessage;
import org.apache.camel.component.jetty.MultiPartFilter;
import org.apache.camel.http.common.DefaultHttpBinding;
import org.apache.camel.http.common.HttpHelper;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;
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
                    // the whitelist accepts file name extensions, so it must be checked against the submitted
                    // file name and not against Part.getName(), which is the multipart field name
                    String fileName = part.getSubmittedFileName();
                    if (!isFileNameAccepted(fileName)) {
                        LOG.debug(
                                "Cannot add file as attachment: {} because the file is not accepted according to fileNameExtWhitelist: {}",
                                HttpHelper.sanitizeLog(fileName), getFileNameExtWhitelist());
                        continue;
                    }

                    DataSource ds = new PartDataSource(part);
                    Attachment attachment = new DefaultAttachment(ds);
                    for (String headerName : part.getHeaderNames()) {
                        for (String headerValue : part.getHeaders(headerName)) {
                            attachment.addHeader(headerName, headerValue);
                        }
                    }
                    AttachmentMessage am = new DefaultAttachmentMessage(message);
                    String name = part.getName();
                    am.addAttachmentObject(name, attachment);
                    // a file part is also exposed as a header carrying the DataHandler. The attachment is keyed on
                    // the multipart field name, so the header must be looked up and named by that same key and not
                    // by the client supplied file name. A plain form field carries no file name and is mapped by
                    // populateRequestParameters instead, so it is left alone here.
                    if (fileName != null && name != null) {
                        Object value = am.getAttachment(name);
                        Map<String, Object> headers = message.getHeaders();
                        if (getHeaderFilterStrategy() != null
                                && !getHeaderFilterStrategy().applyFilterToExternalHeaders(name, value,
                                        message.getExchange())) {
                            HttpHelper.appendHeader(headers, name, value);
                        }
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
        AttachmentMessage am = new DefaultAttachmentMessage(message);

        Enumeration<?> names = null;
        try {
            names = request.getParameterNames();
            while (names.hasMoreElements()) {
                String name = (String) names.nextElement();
                if (am.getAttachment(name) != null) {
                    DataHandler dh = am.getAttachment(name);
                    Object[] values = { dh };
                    if (dh.getContentType() == null || dh.getContentType().startsWith("text/plain")) {
                        // a text field can occur several times, so map each of its values
                        String[] texts = request.getParameterValues(name);
                        // prevent potential injections
                        values = texts != null ? HttpHelper.sanitizeLog(texts) : new Object[] { null };
                    }
                    for (Object value : values) {
                        if (getHeaderFilterStrategy() != null
                                && !getHeaderFilterStrategy().applyFilterToExternalHeaders(name, value,
                                        message.getExchange())) {
                            HttpHelper.appendHeader(headers, name, value);
                        }
                    }
                    continue;
                }

                // there may be multiple values for the same name
                String[] values = request.getParameterValues(name);
                // Avoid potential injections
                String[] sanitizedValues = HttpHelper.sanitizeLog(values);
                if (sanitizedValues != null) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("HTTP parameter {} = {}", name, sanitizedValues);
                    }
                    for (String value : sanitizedValues) {
                        // use http helper to extract parameter value as it may contain multiple values
                        Object extracted = HttpHelper.extractHttpParameterValue(value);
                        if (getHeaderFilterStrategy() != null
                                && !getHeaderFilterStrategy().applyFilterToExternalHeaders(name, extracted,
                                        message.getExchange())) {
                            HttpHelper.appendHeader(headers, name, value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // In Jetty 12.1.0+, getParameterNames() reads the query and the body parameters together and fails for the
            // whole request when it cannot read the body parameters, for example a multipart request when the
            // multipart filter is disabled (enableMultipartFilter=false), or a form larger than Jetty's
            // maxFormContentSize. The body parameters are skipped (a form body is mapped by readFormUrlEncodedBody).
            if (names == null) {
                // nothing has been mapped yet, and the query does not depend on the body, so map its parameters
                LOG.debug("Failed to read the request parameters, mapping the query parameters only: {}", e.getMessage());
                populateQueryParameters(request, message);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to populate request parameters: {}", e.getMessage());
            }
        }
    }

    private void populateQueryParameters(HttpServletRequest request, Message message) {
        // decoded as UTF-8, which is Jetty's default query encoding
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) {
            return;
        }
        MultiMap<String> parameters;
        try {
            parameters = UrlEncoded.decodeQuery(query);
        } catch (Exception e) {
            LOG.debug("Failed to populate query parameters: {}", e.getMessage());
            return;
        }
        Map<String, Object> headers = message.getHeaders();
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            String name = entry.getKey();
            // Avoid potential injections
            String[] sanitizedValues = HttpHelper.sanitizeLog(entry.getValue().toArray(new String[0]));
            for (String value : sanitizedValues) {
                // use http helper to extract parameter value as it may contain multiple values
                Object extracted = HttpHelper.extractHttpParameterValue(value);
                if (getHeaderFilterStrategy() != null
                        && !getHeaderFilterStrategy().applyFilterToExternalHeaders(name, extracted, message.getExchange())) {
                    HttpHelper.appendHeader(headers, name, value);
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
