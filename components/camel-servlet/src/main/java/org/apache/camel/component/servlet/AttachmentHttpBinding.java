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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Locale;

import jakarta.activation.DataSource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachment;
import org.apache.camel.http.common.DefaultHttpBinding;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To handle attachments with Servlet.
 * <p/>
 * This implementation is needed to deal with attachments when using Servlet.
 */
public final class AttachmentHttpBinding extends DefaultHttpBinding {

    private static final Logger LOG = LoggerFactory.getLogger(AttachmentHttpBinding.class);

    AttachmentHttpBinding() {
    }

    @Override
    protected void populateAttachments(HttpServletRequest request, Message message) {
        try {
            Collection<Part> parts = request.getParts();
            for (Part part : parts) {
                String fileName = part.getName();
                // is the file name accepted
                boolean accepted = true;
                if (getFileNameExtWhitelist() != null) {
                    String ext = FileUtil.onlyExt(fileName);
                    if (ext != null) {
                        ext = ext.toLowerCase(Locale.US);
                        String whiteList = getFileNameExtWhitelist().toLowerCase(Locale.US);
                        if (!whiteList.equals("*") && !whiteList.contains(ext)) {
                            accepted = false;
                        }
                    }
                }

                if (accepted) {
                    DataSource ds = new PartDataSource(part);
                    Attachment attachment = new DefaultAttachment(ds);
                    for (String headerName : part.getHeaderNames()) {
                        for (String headerValue : part.getHeaders(headerName)) {
                            attachment.addHeader(headerName, headerValue);
                        }
                    }
                    AttachmentMessage am = message.getExchange().getMessage(AttachmentMessage.class);
                    am.addAttachmentObject(part.getName(), attachment);
                } else {
                    LOG.debug(
                            "Cannot add file as attachment: {} because the file is not accepted according to fileNameExtWhitelist: {}",
                            fileName, getFileNameExtWhitelist());
                }
            }
        } catch (Exception e) {
            throw new RuntimeCamelException("Cannot populate attachments", e);
        }
    }

    public final class PartDataSource implements DataSource {
        private final Part part;

        PartDataSource(Part part) {
            this.part = part;
        }

        public String getSubmittedFileName() {
            return part.getSubmittedFileName();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
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
