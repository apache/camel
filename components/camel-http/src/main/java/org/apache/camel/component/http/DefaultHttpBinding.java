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
package org.apache.camel.component.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.Map;
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
import org.apache.camel.component.http.helper.CamelFileDataSource;
import org.apache.camel.component.http.helper.GZIPHelper;
import org.apache.camel.component.http.helper.HttpHelper;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Binding between {@link HttpMessage} and {@link HttpServletResponse}.
 *
 * @version 
 */
public class DefaultHttpBinding implements HttpBinding {

    private boolean useReaderForPayload;
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    private HttpEndpoint endpoint;

    @Deprecated
    public DefaultHttpBinding() {
    }

    @Deprecated
    public DefaultHttpBinding(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public DefaultHttpBinding(HttpEndpoint endpoint) {
        this.endpoint = endpoint;
        this.headerFilterStrategy = endpoint.getHeaderFilterStrategy();
    }

    public void readRequest(HttpServletRequest request, HttpMessage message) {
        
        // lets force a parse of the body and headers
        message.getBody();
        // populate the headers from the request
        Map<String, Object> headers = message.getHeaders();
        
        //apply the headerFilterStrategy
        Enumeration names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = (String)names.nextElement();
            Object value = request.getHeader(name);
            // mapping the content-type 
            if (name.toLowerCase().equals("content-type")) {
                name = Exchange.CONTENT_TYPE;
            }
            if (headerFilterStrategy != null
                && !headerFilterStrategy.applyFilterToExternalHeaders(name, value, message.getExchange())) {
                headers.put(name, value);
            }
        }
                
        if (request.getCharacterEncoding() != null) {
            headers.put(Exchange.HTTP_CHARACTER_ENCODING, request.getCharacterEncoding());
            message.getExchange().setProperty(Exchange.CHARSET_NAME, request.getCharacterEncoding());
        }

        try {
            populateRequestParameters(request, message);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeCamelException("Cannot read request parameters due " + e.getMessage(), e);
        }
        
        Object body = message.getBody();
        // reset the stream cache if the body is the instance of StreamCache
        if (body instanceof StreamCache) {
            ((StreamCache)body).reset();
        }

        // store the method and query and other info in headers
        headers.put(Exchange.HTTP_METHOD, request.getMethod());
        headers.put(Exchange.HTTP_QUERY, request.getQueryString());
        headers.put(Exchange.HTTP_URL, request.getRequestURL());
        headers.put(Exchange.HTTP_URI, request.getRequestURI());
        headers.put(Exchange.HTTP_PATH, request.getPathInfo());
        headers.put(Exchange.CONTENT_TYPE, request.getContentType());

        // if content type is serialized java object, then de-serialize it to a Java object
        if (request.getContentType() != null && HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(request.getContentType())) {
            try {
                InputStream is = endpoint.getCamelContext().getTypeConverter().mandatoryConvertTo(InputStream.class, body);
                Object object = HttpHelper.deserializeJavaObjectFromStream(is);
                if (object != null) {
                    message.setBody(object);
                }
            } catch (Exception e) {
                throw new RuntimeCamelException("Cannot deserialize body to Java object", e);
            }
        }
        
        populateAttachments(request, message);
    }
    
    protected void populateRequestParameters(HttpServletRequest request, HttpMessage message) throws UnsupportedEncodingException {
        //we populate the http request parameters without checking the request method
        Map<String, Object> headers = message.getHeaders();
        Enumeration names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = (String)names.nextElement();
            Object value = request.getParameter(name);
            if (headerFilterStrategy != null
                && !headerFilterStrategy.applyFilterToExternalHeaders(name, value, message.getExchange())) {
                headers.put(name, value);
            }
        }
        
        if (request.getMethod().equals("POST") && request.getContentType() != null
                && request.getContentType().startsWith(HttpConstants.CONTENT_TYPE_WWW_FORM_URLENCODED)) {
            String charset = request.getCharacterEncoding();
            if (charset == null) {
                charset = "UTF-8";
            }
            // Push POST form params into the headers to retain compatibility with DefaultHttpBinding
            String body = message.getBody(String.class);
            for (String param : body.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    String name = URLDecoder.decode(pair[0], charset);
                    String value = URLDecoder.decode(pair[1], charset);
                    if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToExternalHeaders(name, value, message.getExchange())) {
                        headers.put(name, value);
                    }
                }
            }
        }
    }
    
    protected void populateAttachments(HttpServletRequest request, HttpMessage message) {
        // check if there is multipart files, if so will put it into DataHandler
        Enumeration names = request.getAttributeNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            Object object = request.getAttribute(name);
            if (object instanceof File) {
                String fileName = request.getParameter(name);
                message.addAttachment(fileName, new DataHandler(new CamelFileDataSource((File)object, fileName)));
            }
        }
    }

    public void writeResponse(Exchange exchange, HttpServletResponse response) throws IOException {
        if (exchange.isFailed()) {
            if (exchange.getException() != null) {
                doWriteExceptionResponse(exchange.getException(), response);
            } else {
                // it must be a fault, no need to check for the fault flag on the message
                doWriteFaultResponse(exchange.getOut(), response, exchange);
            }
        } else {
            // just copy the protocol relates header
            copyProtocolHeaders(exchange.getIn(), exchange.getOut());
            Message out = exchange.getOut();            
            if (out != null) {
                doWriteResponse(out, response, exchange);
            }
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

        if (endpoint != null && endpoint.isTransferException()) {
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
        if (MessageHelper.getContentType(message) != null) {
            response.setContentType(contentType);
        }

        // append headers
        // must use entrySet to ensure case of keys is preserved
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value != null && headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToCamelHeaders(key, value, exchange)) {
                response.setHeader(key, value.toString());
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

    protected void doWriteDirectResponse(Message message, HttpServletResponse response, Exchange exchange) throws IOException {
        // if content type is serialized Java object, then serialize and write it to the response
        String contentType = message.getHeader(Exchange.CONTENT_TYPE, String.class);
        if (contentType != null && HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentType)) {
            try {
                Object object = message.getMandatoryBody(Serializable.class);
                HttpHelper.writeObjectToServletResponse(response, object);
                // object is written so return
                return;
            } catch (InvalidPayloadException e) {
                throw new IOException(e);
            }
        }

        // other kind of content type
        InputStream is = null;
        if (checkChunked(message, exchange)) {
            is = message.getBody(InputStream.class);
        }
        if (is != null) {
            ServletOutputStream os = response.getOutputStream();
            try {
                // copy directly from input stream to output stream
                IOHelper.copy(is, os);
            } finally {
                IOHelper.close(os);
                IOHelper.close(is);
            }
        } else {
            // not convertable as a stream so try as a String
            String data = message.getBody(String.class);
            if (data != null) {
                // set content length before we write data
                response.setContentLength(data.length());
                response.getWriter().print(data);
                response.getWriter().flush();
            }
        }
    }

    protected boolean checkChunked(Message message, Exchange exchange) {
        boolean answer = true;
        if (message.getHeader(Exchange.HTTP_CHUNKED) == null) {
            // check the endpoint option
            Endpoint endpoint = exchange.getFromEndpoint();
            if (endpoint instanceof HttpEndpoint) {
                answer = ((HttpEndpoint)endpoint).isChunked();
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
        // Need to handle the GET Method which has no inputStream
        if ("GET".equals(request.getMethod())) {
            return null;
        }
        if (isUseReaderForPayload()) {
            // use reader to read the response body
            return request.getReader();
        } else {
            // reade the response body from servlet request
            return HttpHelper.readResponseBodyFromServletRequest(request, httpMessage.getExchange());
        }
    }

    public boolean isUseReaderForPayload() {
        return useReaderForPayload;
    }

    public void setUseReaderForPayload(boolean useReaderForPayload) {
        this.useReaderForPayload = useReaderForPayload;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

}
