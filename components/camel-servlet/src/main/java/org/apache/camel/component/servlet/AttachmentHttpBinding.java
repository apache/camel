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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import javax.activation.DataSource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.apache.camel.Attachment;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.http.common.DefaultHttpBinding;
import org.apache.camel.http.common.HttpMessage;
import org.apache.camel.impl.DefaultAttachment;

/**
 * To handle attachments with Servlet.
 * <p/>
 * This implementation is needed to deal with attachments when using Servlet.
 */
public final class AttachmentHttpBinding extends DefaultHttpBinding {

    AttachmentHttpBinding() {
    }

    @Override
    protected void populateAttachments(HttpServletRequest request, HttpMessage message) {
        try {
            Collection<Part> parts = request.getParts();
            for (Part part : parts) {
                DataSource ds = new PartDataSource(part);
                Attachment attachment = new DefaultAttachment(ds);
                for (String headerName : part.getHeaderNames()) {
                    for (String headerValue : part.getHeaders(headerName)) {
                        attachment.addHeader(headerName, headerValue);
                    }
                }
                message.addAttachmentObject(part.getName(), attachment);
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
