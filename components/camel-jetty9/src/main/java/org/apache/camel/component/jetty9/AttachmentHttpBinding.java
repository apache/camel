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
package org.apache.camel.component.jetty9;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.http.common.DefaultHttpBinding;
import org.apache.camel.http.common.HttpMessage;
import org.eclipse.jetty.util.MultiPartInputStreamParser;

/**
 * To handle attachments with Jetty 9.
 * <p/>
 * This implementation is needed to deal with attachments when using Jetty 9.
 */
final class AttachmentHttpBinding extends DefaultHttpBinding {

    AttachmentHttpBinding() {
    }

    @Override
    protected void populateAttachments(HttpServletRequest request, HttpMessage message) {
        Object object = request.getAttribute("org.eclipse.jetty.servlet.MultiPartFile.multiPartInputStream");
        if (object instanceof MultiPartInputStreamParser) {
            MultiPartInputStreamParser parser = (MultiPartInputStreamParser)object;
            Collection<Part> parts;
            try {
                parts = parser.getParts();
                for (Part part : parts) {
                    String contentType = part.getContentType();
                    if (!contentType.startsWith("application/octet-stream")) {
                        continue;
                    }

                    DataSource ds = new PartDataSource(part);
                    message.addAttachment(part.getName(), new DataHandler(ds));
                }
            } catch (Exception e) {
                throw new RuntimeCamelException("Cannot populate attachments", e);
            }
        }
    }
    
    final class PartDataSource implements DataSource {
        private final Part part;

        PartDataSource(Part part) {
            this.part = part;
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