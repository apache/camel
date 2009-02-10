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

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Message;
import org.apache.camel.component.http.helper.GZIPHelper;
import org.apache.camel.spi.HeaderFilterStrategy;

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
        // lets force a parse of the body and headers
        message.getBody();
        message.getHeaders();
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

        // write the body.
        if (message.getBody() != null) {
            // try to stream the body since that would be the most efficient
            InputStream is = message.getBody(InputStream.class);
            if (is != null) {
                ServletOutputStream os = response.getOutputStream();
                try {
                    ByteArrayOutputStream initialArray = new ByteArrayOutputStream();
                    int c;
                    while ((c = is.read()) >= 0) {
                        initialArray.write(c);
                    }
                    byte[] processedArray = processReponseContent(message, initialArray.toByteArray(), response);
                    os.write(processedArray);
                    // set content length before we flush
                    // Here the processedArray length is used instead of the
                    // length of the characters in written to the initialArray
                    // because if the method processReponseContent compresses
                    // the data, the processedArray may contain a different length 
                    response.setContentLength(processedArray.length);
                    os.flush();
                } finally {
                    os.close();
                    is.close();
                }
            } else {
                String data = message.getBody(String.class);
                if (data != null) {
                    // set content length before we write data
                    response.setContentLength(data.length());
                    response.getWriter().print(data);
                    response.getWriter().flush();
                }
            }

        }
    }
    
    protected byte[] processReponseContent(Message message, byte[] array, HttpServletResponse response) throws IOException {
        String gzipEncoding = message.getHeader(GZIPHelper.CONTENT_ENCODING, String.class);        
        return GZIPHelper.compressArrayIfGZIPRequested(gzipEncoding, array, response);
    }

    public Object parseBody(HttpMessage httpMessage) throws IOException {
        // lets assume the body is a reader
        HttpServletRequest request = httpMessage.getRequest();
        if (isUseReaderForPayload()) {
            return request.getReader();
        } else {
            return request.getInputStream();
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
