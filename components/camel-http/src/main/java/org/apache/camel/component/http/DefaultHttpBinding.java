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

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Message;
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
            Message out = exchange.getOut();
            if (out != null) {
                doWriteResponse(out, response);
            }
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
            int length = 0;
            if (is != null) {
                ServletOutputStream os = null;
                try {
                    os = response.getOutputStream();
                    int c;
                    while ((c = is.read()) >= 0) {
                        os.write(c);
                        length++;
                    }
                    // set content length before we flush
                    response.setContentLength(length);
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
