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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Message;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;

/**
 * @version $Revision$
 */
public class HttpBinding {

    private boolean useReaderForPayload;
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();

    public HttpBinding(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    /**
     * Writes the exchange to the servlet response
     */
    public void writeResponse(HttpExchange exchange, HttpServletResponse response) throws IOException {
        Message out = exchange.getOut();
        if (out != null) {
            // Set the status code in the response. Default is 200.
            if (out.getHeader(HttpProducer.HTTP_RESPONSE_CODE) != null) {
                int code = out.getHeader(HttpProducer.HTTP_RESPONSE_CODE, Integer.class);
                response.setStatus(code);
            }

            // Write out the headers
            for (String key : out.getHeaders().keySet()) {
                String value = out.getHeader(key, String.class);
                if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToCamelHeaders(key, value)) {
                    response.setHeader(key, value);
                }
            }

            // Write out the body.
            if (out.getBody() != null) {

                // Try to stream the body since that would be the most efficient
                InputStream is = out.getBody(InputStream.class);
                if (is != null) {
                    ServletOutputStream os = null;
                    try {
                        os = response.getOutputStream();
                        int c;
                        while ((c = is.read()) >= 0) {
                            os.write(c);
                        }
                        os.flush();
                    } finally {
                        os.close();
                        is.close();
                    }
                } else {
                    String data = out.getBody(String.class);
                    if (data != null) {
                        response.getWriter().print(data);
                    }
                }
            }
        }
    }

    /**
     * Parses the body from a HTTP message
     */
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

    /**
     * Should the {@link HttpServletRequest#getReader()} be exposed as the payload of input messages in the Camel
     * {@link Message#getBody()} or not. If false then the {@link HttpServletRequest#getInputStream()} will be exposed.
     */
    public void setUseReaderForPayload(boolean useReaderForPayload) {
        this.useReaderForPayload = useReaderForPayload;
    }

}
