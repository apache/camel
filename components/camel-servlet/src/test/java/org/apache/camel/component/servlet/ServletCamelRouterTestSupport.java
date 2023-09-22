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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class ServletCamelRouterTestSupport extends CamelTestSupport {

    public static final String CONTEXT = "/mycontext";
    protected String contextUrl;
    protected boolean startCamelContext = true;
    protected int port;
    protected DeploymentManager manager;
    protected Undertow server;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        DeploymentInfo servletBuilder = getDeploymentInfo();
        manager = Servlets.newContainer().addDeployment(servletBuilder);
        manager.deploy();
        PathHandler path = Handlers.path(Handlers.redirect(CONTEXT))
                .addPrefixPath(CONTEXT, manager.start());
        server = Undertow.builder().addHttpListener(port, "localhost")
                .setHandler(path).build();
        server.start();
        contextUrl = "http://localhost:" + port + CONTEXT;
        if (startCamelContext) {
            super.setUp();
        }
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        if (startCamelContext) {
            super.tearDown();
        }
        server.stop();
        manager.stop();
        manager.undeploy();
    }

    protected DeploymentInfo getDeploymentInfo() {
        return Servlets.deployment()
                .setClassLoader(getClass().getClassLoader())
                .setContextPath(CONTEXT)
                .setDeploymentName(getClass().getName())
                .addServlet(Servlets.servlet("CamelServlet", CamelHttpTransportServlet.class)
                        .addMapping("/services/*"));
    }

    protected WebResponse query(WebRequest req) throws IOException {
        return query(req, true);
    }

    protected WebResponse query(WebRequest req, boolean exceptionsThrownOnErrorStatus) throws IOException {
        String params = req.params.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        String urlStr = params.isEmpty() ? req.url : req.url + "?" + params;
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setUseCaches(false);
        req.headers.forEach(con::addRequestProperty);
        con.setRequestMethod(req.getMethod());
        if (req instanceof PostMethodWebRequest) {
            con.setDoOutput(true);
            String contentType = con.getRequestProperty("Content-Type");
            boolean isMultipart = ObjectHelper.isNotEmpty(contentType) && contentType.startsWith("multipart/form-data");
            if (isMultipart) {
                StringBuilder builder = new StringBuilder();
                builder.append("------Boundary\r\n");
                builder.append("Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n");
                builder.append("Content-Type: application/octet-stream\r\n\r\n");
                con.getOutputStream().write(builder.toString().getBytes(StandardCharsets.UTF_8));
            }

            InputStream is = ((PostMethodWebRequest) req).content;
            if (is != null) {
                IOHelper.copy(is, con.getOutputStream());
            }

            if (isMultipart) {
                con.getOutputStream().write("\r\n------Boundary--\r\n".getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = con.getResponseCode();
        if (exceptionsThrownOnErrorStatus && code >= HttpURLConnection.HTTP_BAD_REQUEST) {
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new HttpNotFoundException(code, con.getResponseMessage(), url);
            }
            throw new HttpException(code, con.getResponseMessage(), url);
        }
        return new WebResponse(con);
    }

    protected abstract static class WebRequest {
        protected String url;
        protected Map<String, String> headers = new HashMap<>();
        protected Map<String, String> params = new HashMap<>();

        public WebRequest(String url) {
            this.url = url;
        }

        public abstract String getMethod();

        public void setParameter(String key, String val) {
            params.put(key, val);
        }

        public void setHeaderField(String key, String val) {
            headers.put(key, val);
        }
    }

    protected static class GetMethodWebRequest extends WebRequest {
        public GetMethodWebRequest(String url) {
            super(url);
            headers.put("Content-Length", "0");
        }

        public String getMethod() {
            return "GET";
        }
    }

    protected static class PostMethodWebRequest extends WebRequest {
        protected InputStream content;

        public PostMethodWebRequest(String url) {
            super(url);
        }

        public PostMethodWebRequest(String url, InputStream content, String contentType) {
            super(url);
            this.content = content;
            headers.put("Content-Type", contentType);
        }

        public String getMethod() {
            return "POST";
        }
    }

    protected static class PutMethodWebRequest extends PostMethodWebRequest {
        public PutMethodWebRequest(String url) {
            super(url);
        }

        public PutMethodWebRequest(String url, InputStream content, String contentType) {
            super(url, content, contentType);
        }

        public String getMethod() {
            return "PUT";
        }
    }

    protected static class OptionsMethodWebRequest extends WebRequest {
        public OptionsMethodWebRequest(String url) {
            super(url);
        }

        public String getMethod() {
            return "OPTIONS";
        }
    }

    protected abstract static class HeaderOnlyWebRequest extends WebRequest {
        public HeaderOnlyWebRequest(String url) {
            super(url);
        }
    }

    protected static class WebResponse {

        HttpURLConnection con;
        String text;

        public WebResponse(HttpURLConnection con) {
            this.con = con;
        }

        public int getResponseCode() throws IOException {
            return con.getResponseCode();
        }

        public String getText(Charset charset) throws IOException {
            if (text == null) {
                if (con.getContentLength() != 0) {
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        IOHelper.copy(con.getInputStream(), baos);
                        text = baos.toString(charset.name());
                    } catch (IOException e) {
                        text = e.getMessage();
                    }
                } else {
                    text = "";
                }
            }
            return text;
        }

        public String getText() throws IOException {
            return getText(Charset.defaultCharset());
        }

        public String getContentType() {
            String content = con.getContentType();
            return content != null && content.contains(";")
                    ? content.substring(0, content.indexOf(";"))
                    : content;
        }

        public InputStream getInputStream() throws IOException {
            try {
                return con.getInputStream();
            } catch (IOException e) {
                try {
                    Field f = con.getClass().getDeclaredField("inputStream");
                    f.setAccessible(true);
                    return (InputStream) f.get(con);
                } catch (Throwable t) {
                    e.addSuppressed(t);
                    throw e;
                }
            }
        }

        public String getResponseMessage() throws IOException {
            return con.getResponseMessage();
        }

        public String getCharacterSet() {
            String content = con.getContentType();
            return content != null && content.contains(";charset=")
                    ? content.substring(content.lastIndexOf(";charset=") + ";charset=".length())
                    : con.getContentEncoding();
        }

        public String getHeaderField(String key) {
            return con.getHeaderField(key);
        }
    }

    protected static class HttpException extends RuntimeException {
        private final int code;
        private final String message;
        private final URL url;

        public HttpException(int code, String message, URL url) {
            this.code = code;
            this.message = message;
            this.url = url;
        }

        public int getResponseCode() {
            return code;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder().append("Error on HTTP request: ");
            sb.append(code);
            if (message != null) {
                sb.append(" ");
                sb.append(message);
            }
            if (url != null) {
                sb.append(" [");
                sb.append(url.toExternalForm());
                sb.append("]");
            }
            return sb.toString();
        }
    }

    protected static class HttpNotFoundException extends HttpException {
        public HttpNotFoundException(int code, String message, URL url) {
            super(code, message, url);
        }
    }

}
