package org.apache.camel.component.jetty9;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.apache.camel.component.http.DefaultHttpBinding;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.component.http.HttpMessage;
import org.eclipse.jetty.util.MultiPartInputStreamParser;

final class AttachmentHttpBinding extends DefaultHttpBinding {
    AttachmentHttpBinding(HttpEndpoint endpoint) {
        super(endpoint);
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
                    e.printStackTrace();
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