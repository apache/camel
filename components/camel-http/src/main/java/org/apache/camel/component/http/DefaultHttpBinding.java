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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Map;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.InvalidTypeException;
import org.apache.camel.Message;
import org.apache.camel.component.http.helper.GZIPHelper;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Binding between {@link HttpMessage} and {@link HttpServletResponse}.
 *
 * @version $Revision$
 */
public class DefaultHttpBinding implements HttpBinding {

    private boolean useReaderForPayload;
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();

    public DefaultHttpBinding() {
    }

    public DefaultHttpBinding(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public void readRequest(HttpServletRequest request, HttpMessage message) {
        // lets parser the parameterMap first to avoid consuming the POST parameters as InputStream
        request.getParameterMap();
        
        // lets force a parse of the body and headers
        message.getBody();
        // populate the headers from the request
        Map<String, Object> headers = message.getHeaders();
        
        //apply the headerFilterStrategy
        Enumeration names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = (String)names.nextElement();
            Object value = request.getHeader(name);
            if (headerFilterStrategy != null
                && !headerFilterStrategy.applyFilterToExternalHeaders(name, value)) {
                headers.put(name, value);
            }
        }

        //if the request method is Get, we also populate the http request parameters
        if (request.getMethod().equalsIgnoreCase("GET")) {
            names = request.getParameterNames();
            while (names.hasMoreElements()) {
                String name = (String)names.nextElement();
                Object value = request.getParameter(name);
                if (headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToExternalHeaders(name, value)) {
                    headers.put(name, value);
                }
            }
        }
        
        // store the method and query and other info in headers
        headers.put(HttpMethods.HTTP_METHOD, request.getMethod());
        headers.put(HttpProducer.QUERY, request.getQueryString());
    }

    public void writeResponse(HttpExchange exchange, HttpServletResponse response) throws IOException {
        if (exchange.isFailed()) {
            Message fault = exchange.getFault(false);
            if (fault != null) {
                doWriteFaultResponse(fault, response);
            } else {
                doWriteExceptionResponse(exchange.getException(), response);
            }
        } else {
            // just copy the protocol relates header
            copyProtocolHeaders(exchange.getIn(), exchange.getOut());
            Message out = exchange.getOut();            
            if (out != null) {
                doWriteResponse(out, response);
            }
        }
    }

    private void copyProtocolHeaders(Message request, Message response) {
        if (request.getHeader(GZIPHelper.CONTENT_ENCODING) != null) {
            String contentEncoding = request.getHeader(GZIPHelper.CONTENT_ENCODING, String.class);
            response.setHeader(GZIPHelper.CONTENT_ENCODING, contentEncoding);
        }        
    }

    public void doWriteExceptionResponse(Throwable exception, HttpServletResponse response) throws IOException {
        response.setStatus(500); // 500 for internal server error
        response.setContentType("text/plain");

        // append the stacktrace as response
        PrintWriter pw = response.getWriter();
        exception.printStackTrace(pw);

        pw.flush();
    }

    public void doWriteFaultResponse(Message message, HttpServletResponse response) throws IOException {
        doWriteResponse(message, response);
    }

    public void doWriteResponse(Message message, HttpServletResponse response) throws IOException {
        // set the status code in the response. Default is 200.
        if (message.getHeader(HttpProducer.HTTP_RESPONSE_CODE) != null) {
            int code = message.getHeader(HttpProducer.HTTP_RESPONSE_CODE, Integer.class);
            response.setStatus(code);
        }
        // set the content type in the response.
        if (message.getHeader("Content-Type") != null) {            
            String contentType = message.getHeader("Content-Type", String.class);            
            response.setContentType(contentType);
        }

        // append headers
        for (String key : message.getHeaders().keySet()) {
            String value = message.getHeader(key, String.class);
            if (headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToCamelHeaders(key, value)) {
                response.setHeader(key, value);
            }
        }

        if (message.getBody() != null) {
            if (GZIPHelper.isGzip(message)) {
                doWriteGZIPResponse(message, response);
            } else {
                doWriteDirectResponse(message, response);
            }
        }
    }

    protected void doWriteDirectResponse(Message message, HttpServletResponse response) throws IOException {
        InputStream is = message.getBody(InputStream.class);
        if (is != null) {
            ServletOutputStream os = response.getOutputStream();
            try {
                // copy directly from input stream to output stream
                IOHelper.copy(is, os);
            } finally {
                os.close();
                is.close();
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

    protected void doWriteGZIPResponse(Message message, HttpServletResponse response) throws IOException {
        byte[] bytes;
        try {
            bytes = ExchangeHelper.convertToMandatoryType(message.getExchange(), byte[].class, message.getBody());
        } catch (InvalidTypeException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        byte[] data = GZIPHelper.compressGZIP(bytes);
        ServletOutputStream os = response.getOutputStream();
        try {
            response.setContentLength(data.length);
            os.write(data);
            os.flush();
        } finally {
            os.close();
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
            return request.getReader();
        } else {
            // otherwise use input stream
            return HttpConverter.toInputStream(request);
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
