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
package org.apache.camel.http.common;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import javax.activation.DataHandler;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.GZIPHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binding between {@link HttpMessage} and {@link HttpServletResponse}.
 * <p/>
 * Uses by default the {@link org.apache.camel.http.common.HttpHeaderFilterStrategy}
 */
public class DefaultHttpBinding implements HttpBinding {

    /**
     * Whether Date/Locale should be converted to String types (enabled by default)
     */
    public static final String DATE_LOCALE_CONVERSION = "CamelHttpBindingDateLocaleConversion";

    /**
     * The data format used for storing java.util.Date instances as a String value.
     */
    public static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpBinding.class);
    private static final TimeZone TIME_ZONE_GMT = TimeZone.getTimeZone("GMT");

    private boolean useReaderForPayload;
    private boolean eagerCheckContentAvailable;
    private boolean transferException;
    private boolean allowJavaSerializedObject;
    private boolean mapHttpMessageBody = true;
    private boolean mapHttpMessageHeaders = true;
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();

    public DefaultHttpBinding() {
    }

    @Deprecated
    public DefaultHttpBinding(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    @Deprecated
    public DefaultHttpBinding(HttpCommonEndpoint endpoint) {
        this.headerFilterStrategy = endpoint.getHeaderFilterStrategy();
        this.transferException = endpoint.isTransferException();
        if (endpoint.getComponent() != null) {
            this.allowJavaSerializedObject = endpoint.getComponent().isAllowJavaSerializedObject();
        }
    }

    public void readRequest(HttpServletRequest request, HttpMessage message) {
        LOG.trace("readRequest {}", request);

        // must read body before headers
        if (mapHttpMessageBody) {
            readBody(request, message);
        }
        if (mapHttpMessageHeaders) {
            readHeaders(request, message);
        }

        // populate the headers from the request
        Map<String, Object> headers = message.getHeaders();

        // always store these standard headers
        // store the method and query and other info in headers as String types
        String rawPath = getRawPath(request);
        headers.put(Exchange.HTTP_METHOD, request.getMethod());
        headers.put(Exchange.HTTP_QUERY, request.getQueryString());
        headers.put(Exchange.HTTP_URL, request.getRequestURL().toString());
        headers.put(Exchange.HTTP_URI, request.getRequestURI());
        headers.put(Exchange.HTTP_PATH, rawPath);
        // only set content type if not already extracted
        if (!headers.containsKey(Exchange.CONTENT_TYPE)) {
            headers.put(Exchange.CONTENT_TYPE, request.getContentType());
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("HTTP method {}", request.getMethod());
            LOG.trace("HTTP query {}", request.getQueryString());
            LOG.trace("HTTP url {}", request.getRequestURL());
            LOG.trace("HTTP uri {}", request.getRequestURI());
            LOG.trace("HTTP path {}", rawPath);
            LOG.trace("HTTP content-type {}", headers.get(Exchange.CONTENT_TYPE));
        }
    }

    protected void readHeaders(HttpServletRequest request, HttpMessage message) {
        LOG.trace("readHeaders {}", request);

        Map<String, Object> headers = message.getHeaders();

        //apply the headerFilterStrategy
        Enumeration<?> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = (String)names.nextElement();
            String value = request.getHeader(name);
            // use http helper to extract parameter value as it may contain multiple values
            Object extracted = HttpHelper.extractHttpParameterValue(value);
            // mapping the content-type
            if (name.toLowerCase().equals("content-type")) {
                name = Exchange.CONTENT_TYPE;
            }
            if (headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToExternalHeaders(name, extracted, message.getExchange())) {
                HttpHelper.appendHeader(headers, name, extracted);
            }
        }

        if (request.getCharacterEncoding() != null) {
            headers.put(Exchange.HTTP_CHARACTER_ENCODING, request.getCharacterEncoding());
            message.getExchange().setProperty(Exchange.CHARSET_NAME, request.getCharacterEncoding());
        }

        try {
            populateRequestParameters(request, message);
        } catch (Exception e) {
            throw new RuntimeCamelException("Cannot read request parameters due " + e.getMessage(), e);
        }
    }

    protected void readBody(HttpServletRequest request, HttpMessage message) {
        LOG.trace("readBody {}", request);

        // lets parse the body
        Object body = message.getBody();
        // reset the stream cache if the body is the instance of StreamCache
        if (body instanceof StreamCache) {
            ((StreamCache) body).reset();
        }

        // if content type is serialized java object, then de-serialize it to a Java object
        if (request.getContentType() != null && HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(request.getContentType())) {
            // only deserialize java if allowed
            if (allowJavaSerializedObject || isTransferException()) {
                try {
                    InputStream is = message.getExchange().getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, body);
                    Object object = HttpHelper.deserializeJavaObjectFromStream(is, message.getExchange().getContext());
                    if (object != null) {
                        message.setBody(object);
                    }
                } catch (Exception e) {
                    throw new RuntimeCamelException("Cannot deserialize body to Java object", e);
                }
            } else {
                // set empty body
                message.setBody(null);
            }
        }

        populateAttachments(request, message);
    }

    protected void populateRequestParameters(HttpServletRequest request, HttpMessage message) throws Exception {
        //we populate the http request parameters without checking the request method
        Map<String, Object> headers = message.getHeaders();
        Enumeration<?> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = (String)names.nextElement();
            // there may be multiple values for the same name
            String[] values = request.getParameterValues(name);
            LOG.trace("HTTP parameter {} = {}", name, values);

            if (values != null) {
                for (String value : values) {
                    if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToExternalHeaders(name, value, message.getExchange())) {
                        HttpHelper.appendHeader(headers, name, value);
                    }
                }
            }
        }

        // should we extract key=value pairs from form bodies (application/x-www-form-urlencoded)
        // and map those to Camel headers
        if (mapHttpMessageBody && mapHttpMessageHeaders) {
            LOG.trace("HTTP method {} with Content-Type {}", request.getMethod(), request.getContentType());
            Boolean flag = message.getHeader(Exchange.SKIP_WWW_FORM_URLENCODED, Boolean.class);
            boolean skipWwwFormUrlEncoding = flag != null ? flag : false;
            if (request.getMethod().equals("POST") && request.getContentType() != null
                    && request.getContentType().startsWith(HttpConstants.CONTENT_TYPE_WWW_FORM_URLENCODED)
                    && !skipWwwFormUrlEncoding) {
                String charset = request.getCharacterEncoding();
                if (charset == null) {
                    charset = "UTF-8";
                }

                // lets parse the body
                Object body = message.getBody();
                // reset the stream cache if the body is the instance of StreamCache
                if (body instanceof StreamCache) {
                    ((StreamCache) body).reset();
                }

                // Push POST form params into the headers to retain compatibility with DefaultHttpBinding
                String text = message.getBody(String.class);
                if (ObjectHelper.isNotEmpty(text)) {
                    for (String param : text.split("&")) {
                        String[] pair = param.split("=", 2);
                        if (pair.length == 2) {
                            String name = URLDecoder.decode(pair[0], charset);
                            String value = URLDecoder.decode(pair[1], charset);
                            if (headerFilterStrategy != null
                                    && !headerFilterStrategy.applyFilterToExternalHeaders(name, value, message.getExchange())) {
                                HttpHelper.appendHeader(headers, name, value);
                            }
                        } else {
                            throw new IllegalArgumentException("Invalid parameter, expected to be a pair but was " + param);
                        }
                    }
                }

                // reset the stream cache if the body is the instance of StreamCache
                if (body instanceof StreamCache) {
                    ((StreamCache) body).reset();
                }
            }
        }
    }

    private String getRawPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String servletPath = request.getServletPath();
        return uri.substring(contextPath.length() + servletPath.length());
    }

    protected void populateAttachments(HttpServletRequest request, HttpMessage message) {
        // check if there is multipart files, if so will put it into DataHandler
        Enumeration<?> names = request.getAttributeNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            Object object = request.getAttribute(name);
            LOG.trace("HTTP attachment {} = {}", name, object);
            if (object instanceof File) {
                String fileName = request.getParameter(name);
                message.addAttachment(fileName, new DataHandler(new CamelFileDataSource((File)object, fileName)));
            }
        }
    }

    public void writeResponse(Exchange exchange, HttpServletResponse response) throws IOException {
        Message target = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
        if (exchange.isFailed()) {
            if (exchange.getException() != null) {
                doWriteExceptionResponse(exchange.getException(), response);
            } else {
                // it must be a fault, no need to check for the fault flag on the message
                doWriteFaultResponse(target, response, exchange);
            }
        } else {
            if (exchange.hasOut()) {
                // just copy the protocol relates header if we do not have them
                copyProtocolHeaders(exchange.getIn(), exchange.getOut());
            }
            doWriteResponse(target, response, exchange);
        }
    }

    private void copyProtocolHeaders(Message request, Message response) {
        if (request.getHeader(Exchange.CONTENT_ENCODING) != null) {
            String contentEncoding = request.getHeader(Exchange.CONTENT_ENCODING, String.class);
            response.setHeader(Exchange.CONTENT_ENCODING, contentEncoding);
        }        
        if (checkChunked(response, response.getExchange())) {
            response.setHeader(Exchange.TRANSFER_ENCODING, "chunked");
        }
    }

    public void doWriteExceptionResponse(Throwable exception, HttpServletResponse response) throws IOException {
        // 500 for internal server error
        response.setStatus(500);

        if (isTransferException()) {
            // transfer the exception as a serialized java object
            HttpHelper.writeObjectToServletResponse(response, exception);
        } else {
            // write stacktrace as plain text
            response.setContentType("text/plain");
            PrintWriter pw = response.getWriter();
            exception.printStackTrace(pw);
            pw.flush();
        }
    }

    public void doWriteFaultResponse(Message message, HttpServletResponse response, Exchange exchange) throws IOException {
        message.setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
        doWriteResponse(message, response, exchange);
    }

    public void doWriteResponse(Message message, HttpServletResponse response, Exchange exchange) throws IOException {
        // set the status code in the response. Default is 200.
        if (message.getHeader(Exchange.HTTP_RESPONSE_CODE) != null) {
            int code = message.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
            response.setStatus(code);
        }
        // set the content type in the response.
        String contentType = MessageHelper.getContentType(message);
        if (contentType != null) {
            response.setContentType(contentType);
        }

        // append headers
        // must use entrySet to ensure case of keys is preserved
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // use an iterator as there can be multiple values. (must not use a delimiter)
            final Iterator<?> it = ObjectHelper.createIterator(value, null);
            while (it.hasNext()) {
                String headerValue = convertHeaderValueToString(exchange, it.next());
                if (headerValue != null && headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToCamelHeaders(key, headerValue, exchange)) {
                    response.addHeader(key, headerValue);
                }
            }
        }

        // write the body.
        if (message.getBody() != null) {
            if (GZIPHelper.isGzip(message)) {
                doWriteGZIPResponse(message, response, exchange);
            } else {
                doWriteDirectResponse(message, response, exchange);
            }
        }
    }
    
    protected String convertHeaderValueToString(Exchange exchange, Object headerValue) {
        if ((headerValue instanceof Date || headerValue instanceof Locale)
            && convertDateAndLocaleLocally(exchange)) {
            if (headerValue instanceof Date) {
                return toHttpDate((Date)headerValue);
            } else {
                return toHttpLanguage((Locale)headerValue);
            }
        } else {
            return exchange.getContext().getTypeConverter().convertTo(String.class, headerValue);
        }
    }

    protected boolean convertDateAndLocaleLocally(Exchange exchange) {
        // This check is done only if a given header value is Date or Locale
        return exchange.getProperty(DATE_LOCALE_CONVERSION, Boolean.TRUE, Boolean.class);
    }

    protected boolean isText(String contentType) {
        if (contentType != null) {
            String temp = contentType.toLowerCase();
            if (temp.contains("text") || temp.contains("html")) {
                return true;
            }
        }
        return false;
    }
    
    protected int copyStream(InputStream is, OutputStream os, int bufferSize) throws IOException {
        try {
            // copy stream, and must flush on each write as etc Jetty has better performance when
            // flushing after writing to its servlet output stream
            return IOHelper.copy(is, os, bufferSize, true);
        } finally {
            IOHelper.close(os, is);
        }
    }

    protected void doWriteDirectResponse(Message message, HttpServletResponse response, Exchange exchange) throws IOException {
        // if content type is serialized Java object, then serialize and write it to the response
        String contentType = message.getHeader(Exchange.CONTENT_TYPE, String.class);
        if (contentType != null && HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentType)) {
            if (allowJavaSerializedObject || isTransferException()) {
                try {
                    Object object = message.getMandatoryBody(Serializable.class);
                    HttpHelper.writeObjectToServletResponse(response, object);
                    // object is written so return
                    return;
                } catch (InvalidPayloadException e) {
                    throw new IOException(e);
                }
            } else {
                throw new RuntimeCamelException("Content-type " + HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT + " is not allowed");
            }
        }

        // prefer streaming
        InputStream is = null;
        if (checkChunked(message, exchange)) {
            is = message.getBody(InputStream.class);
        } else {
            // try to use input stream first, so we can copy directly
            if (!isText(contentType)) {
                is = exchange.getContext().getTypeConverter().tryConvertTo(InputStream.class, message.getBody());
            }
        }

        if (is != null) {
            ServletOutputStream os = response.getOutputStream();
            if (!checkChunked(message, exchange)) {
                CachedOutputStream stream = new CachedOutputStream(exchange);
                try {
                    // copy directly from input stream to the cached output stream to get the content length
                    int len = copyStream(is, stream, response.getBufferSize());
                    // we need to setup the length if message is not chucked
                    response.setContentLength(len);
                    OutputStream current = stream.getCurrentStream();
                    if (current instanceof ByteArrayOutputStream) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Streaming (direct) response in non-chunked mode with content-length {}");
                        }
                        ByteArrayOutputStream bos = (ByteArrayOutputStream) current;
                        bos.writeTo(os);
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Streaming response in non-chunked mode with content-length {} and buffer size: {}", len, len);
                        }
                        copyStream(stream.getInputStream(), os, len);
                    }
                } finally {
                    IOHelper.close(is, os);
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Streaming response in chunked mode with buffer size {}", response.getBufferSize());
                }
                copyStream(is, os, response.getBufferSize());
            }
        } else {
            // not convertable as a stream so fallback as a String
            String data = message.getBody(String.class);
            if (data != null) {
                // set content length and encoding before we write data
                String charset = IOHelper.getCharsetName(exchange, true);
                final int dataByteLength = data.getBytes(charset).length;
                response.setCharacterEncoding(charset);
                response.setContentLength(dataByteLength);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Writing response in non-chunked mode as plain text with content-length {} and buffer size: {}", dataByteLength, response.getBufferSize());
                }
                try {
                    response.getWriter().print(data);
                } finally {
                    response.getWriter().flush();
                }
            }
        }
    }

    protected boolean checkChunked(Message message, Exchange exchange) {
        boolean answer = true;
        if (message.getHeader(Exchange.HTTP_CHUNKED) == null) {
            // check the endpoint option
            Endpoint endpoint = exchange.getFromEndpoint();
            if (endpoint instanceof HttpCommonEndpoint) {
                answer = ((HttpCommonEndpoint)endpoint).isChunked();
            }
        } else {
            answer = message.getHeader(Exchange.HTTP_CHUNKED, boolean.class);
        }
        return answer;
    }

    protected void doWriteGZIPResponse(Message message, HttpServletResponse response, Exchange exchange) throws IOException {
        byte[] bytes;
        try {
            bytes = message.getMandatoryBody(byte[].class);
        } catch (InvalidPayloadException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        byte[] data = GZIPHelper.compressGZIP(bytes);
        ServletOutputStream os = response.getOutputStream();
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Streaming response as GZIP in non-chunked mode with content-length {} and buffer size: {}", data.length, response.getBufferSize());
            }
            response.setContentLength(data.length);
            os.write(data);
            os.flush();
        } finally {
            IOHelper.close(os);
        }
    }

    public Object parseBody(HttpMessage httpMessage) throws IOException {
        // lets assume the body is a reader
        HttpServletRequest request = httpMessage.getRequest();
        // there is only a body if we have a content length, or its -1 to indicate unknown length
        int len = request.getContentLength();
        LOG.trace("HttpServletRequest content-length: {}", len);
        if (len == 0) {
            return null;
        }
        if (isUseReaderForPayload()) {
            // use reader to read the response body
            return request.getReader();
        } else {
            // if we do not know if there is any data at all, then make sure to check the stream first
            if (len < 0 && isEagerCheckContentAvailable()) {
                InputStream is = request.getInputStream();
                if (is.available() == 0) {
                    // no data so return null
                    return null;
                }
            }
            // read the response body from servlet request
            return HttpHelper.readRequestBodyFromServletRequest(request, httpMessage.getExchange());
        }
    }

    public boolean isUseReaderForPayload() {
        return useReaderForPayload;
    }

    public void setUseReaderForPayload(boolean useReaderForPayload) {
        this.useReaderForPayload = useReaderForPayload;
    }

    public boolean isEagerCheckContentAvailable() {
        return eagerCheckContentAvailable;
    }

    public void setEagerCheckContentAvailable(boolean eagerCheckContentAvailable) {
        this.eagerCheckContentAvailable = eagerCheckContentAvailable;
    }

    public boolean isTransferException() {
        return transferException;
    }

    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }

    public boolean isAllowJavaSerializedObject() {
        return allowJavaSerializedObject;
    }

    public void setAllowJavaSerializedObject(boolean allowJavaSerializedObject) {
        this.allowJavaSerializedObject = allowJavaSerializedObject;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public boolean isMapHttpMessageBody() {
        return mapHttpMessageBody;
    }

    public void setMapHttpMessageBody(boolean mapHttpMessageBody) {
        this.mapHttpMessageBody = mapHttpMessageBody;
    }

    public boolean isMapHttpMessageHeaders() {
        return mapHttpMessageHeaders;
    }

    public void setMapHttpMessageHeaders(boolean mapHttpMessageHeaders) {
        this.mapHttpMessageHeaders = mapHttpMessageHeaders;
    }

    protected static SimpleDateFormat getHttpDateFormat() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        dateFormat.setTimeZone(TIME_ZONE_GMT);
        return dateFormat;
    }
    
    protected static String toHttpDate(Date date) {
        SimpleDateFormat format = getHttpDateFormat();
        return format.format(date);
    }
    
    protected static String toHttpLanguage(Locale locale) {
        StringBuilder sb = new StringBuilder();
        sb.append(locale.getLanguage());
        if (locale.getCountry() != null) {
            // Locale.toString() will use a "_" separator instead, 
            // while '-' is expected in headers such as Content-Language, etc:
            // http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.10
            sb.append('-').append(locale.getCountry());
        }
        return sb.toString();
    }
}
