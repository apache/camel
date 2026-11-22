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
package org.apache.camel.component.cxf.platform.http;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import io.vertx.core.Context;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;

/**
 * The servlet response that CXF writes, over a Vert.x response. Only what CXF uses is supported: status, headers,
 * content type and the body.
 */
final class VertxServletResponse implements HttpServletResponse {

    private static final DateTimeFormatter HTTP_DATE
            = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).withLocale(Locale.US);

    private final HttpServerResponse response;
    private final VertxServletOutputStream out;
    private PrintWriter writer;
    private String contentType;
    private String characterEncoding;
    private Locale locale = Locale.getDefault();

    VertxServletResponse(Context context, HttpServerResponse response) {
        this.response = response;
        this.out = new VertxServletOutputStream(context, response);
    }

    /**
     * Ends the response after CXF has written it.
     */
    void close() throws IOException {
        if (writer != null) {
            writer.flush();
        }
        out.close();
    }

    /**
     * Ends the response after a failure (on the event loop).
     */
    void fail() {
        if (response.ended() || response.closed()) {
            return;
        }
        if (response.headWritten()) {
            // the status is sent already: the client can only see that the body is cut off
            response.reset();
        } else {
            response.setStatusCode(500).end();
        }
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding != null ? characterEncoding : StandardCharsets.ISO_8859_1.name();
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return out;
    }

    @Override
    public PrintWriter getWriter() {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(out, Charset.forName(getCharacterEncoding())));
        }
        return writer;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        if (isCommitted() || writer != null) {
            return;
        }
        this.characterEncoding = charset;
        if (contentType != null) {
            setContentType(contentType);
        }
    }

    @Override
    public void setContentLength(int len) {
        setContentLengthLong(len);
    }

    @Override
    public void setContentLengthLong(long len) {
        if (!isCommitted()) {
            response.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(len));
        }
    }

    @Override
    public void setContentType(String type) {
        if (isCommitted() || type == null) {
            return;
        }
        String answer = type;
        int pos = type.toLowerCase(Locale.ROOT).indexOf("charset=");
        if (pos >= 0) {
            String charset = type.substring(pos + 8).trim();
            int end = charset.indexOf(';');
            if (end >= 0) {
                charset = charset.substring(0, end).trim();
            }
            if (writer == null) {
                characterEncoding = charset.replace("\"", "");
            }
        } else if (characterEncoding != null) {
            answer = type + ";charset=" + characterEncoding;
        }
        contentType = answer;
        response.putHeader(HttpHeaders.CONTENT_TYPE, answer);
    }

    @Override
    public void setBufferSize(int size) {
        out.setBufferSize(size);
    }

    @Override
    public int getBufferSize() {
        return out.getBufferSize();
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        }
        out.flush();
    }

    @Override
    public void resetBuffer() {
        out.resetBuffer();
    }

    @Override
    public boolean isCommitted() {
        return out.isCommitted();
    }

    @Override
    public void reset() {
        resetBuffer();
        response.headers().clear();
        response.setStatusCode(200);
        contentType = null;
        characterEncoding = null;
        writer = null;
    }

    @Override
    public void setLocale(Locale loc) {
        if (loc != null && !isCommitted()) {
            this.locale = loc;
        }
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    @Override
    public void addCookie(Cookie cookie) {
        // CXF does not set cookies on a SOAP response
        throw new UnsupportedOperationException("Cookies are not supported");
    }

    @Override
    public boolean containsHeader(String name) {
        return response.headers().contains(name);
    }

    @Override
    public String encodeURL(String url) {
        return url;
    }

    @Override
    public String encodeRedirectURL(String url) {
        return url;
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        if (isCommitted()) {
            throw new IllegalStateException("The response has been committed");
        }
        resetBuffer();
        response.setStatusCode(sc);
        if (msg != null) {
            response.putHeader(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-8");
            out.write(msg.getBytes(StandardCharsets.UTF_8));
        }
        out.close();
    }

    @Override
    public void sendError(int sc) throws IOException {
        sendError(sc, null);
    }

    @Override
    public void sendRedirect(String location, int sc, boolean clearBuffer) throws IOException {
        if (isCommitted()) {
            throw new IllegalStateException("The response has been committed");
        }
        if (clearBuffer) {
            resetBuffer();
        }
        response.setStatusCode(sc);
        response.putHeader(HttpHeaders.LOCATION, location);
        out.close();
    }

    @Override
    public void setDateHeader(String name, long date) {
        setHeader(name, HTTP_DATE.format(Instant.ofEpochMilli(date)));
    }

    @Override
    public void addDateHeader(String name, long date) {
        addHeader(name, HTTP_DATE.format(Instant.ofEpochMilli(date)));
    }

    @Override
    public void setHeader(String name, String value) {
        if (isCommitted() || name == null) {
            return;
        }
        if (HttpHeaders.CONTENT_TYPE.toString().equalsIgnoreCase(name)) {
            setContentType(value);
        } else if (value == null) {
            response.headers().remove(name);
        } else {
            response.putHeader(name, value);
        }
    }

    @Override
    public void addHeader(String name, String value) {
        if (isCommitted() || name == null || value == null) {
            return;
        }
        if (HttpHeaders.CONTENT_TYPE.toString().equalsIgnoreCase(name)) {
            setContentType(value);
        } else {
            response.headers().add(name, value);
        }
    }

    @Override
    public void setIntHeader(String name, int value) {
        setHeader(name, String.valueOf(value));
    }

    @Override
    public void addIntHeader(String name, int value) {
        addHeader(name, String.valueOf(value));
    }

    @Override
    public void setStatus(int sc) {
        if (!isCommitted()) {
            response.setStatusCode(sc);
        }
    }

    @Override
    public int getStatus() {
        return response.getStatusCode();
    }

    @Override
    public String getHeader(String name) {
        return response.headers().get(name);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        return new ArrayList<>(response.headers().getAll(name));
    }

    @Override
    public Collection<String> getHeaderNames() {
        return new LinkedHashSet<>(response.headers().names());
    }
}
