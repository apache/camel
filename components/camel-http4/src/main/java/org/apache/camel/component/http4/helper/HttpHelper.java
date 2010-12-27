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
package org.apache.camel.component.http4.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.component.http4.HttpConstants;
import org.apache.camel.component.http4.HttpConverter;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.util.IOHelper;

public final class HttpHelper {

    private HttpHelper() {
        // Helper class
    }

    public static void setCharsetFromContentType(String contentType, Exchange exchange) {
        if (contentType != null) {
            // find the charset and set it to the Exchange
            int index = contentType.indexOf("charset=");
            if (index > 0) {
                String charset = contentType.substring(index + 8);
                exchange.setProperty(Exchange.CHARSET_NAME, IOConverter.normalizeCharset(charset));
            }
        }
    }

    /**
     * Writes the given object as response body to the servlet response
     * <p/>
     * The content type will be set to {@link HttpConstants#CONTENT_TYPE_JAVA_SERIALIZED_OBJECT}
     *
     * @param response servlet response
     * @param target   object to write
     * @throws IOException is thrown if error writing
     */
    public static void writeObjectToServletResponse(ServletResponse response, Object target) throws IOException {
        response.setContentType(HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);
        ObjectOutputStream oos = new ObjectOutputStream(response.getOutputStream());
        oos.writeObject(target);
        oos.flush();
        IOHelper.close(oos);
    }

    /**
     * Reads the response body from the given http servlet request.
     *
     * @param request  http servlet request
     * @param exchange the exchange
     * @return the response body, can be <tt>null</tt> if no body
     * @throws IOException is thrown if error reading response body
     */
    public static Object readResponseBodyFromServletRequest(HttpServletRequest request, Exchange exchange) throws IOException {
        InputStream is = HttpConverter.toInputStream(request, exchange);
        return readResponseBodyFromInputStream(is, exchange);
    }

    /**
     * Reads the response body from the given input stream.
     *
     * @param is       the input stream
     * @param exchange the exchange
     * @return the response body, can be <tt>null</tt> if no body
     * @throws IOException is thrown if error reading response body
     */
    public static Object readResponseBodyFromInputStream(InputStream is, Exchange exchange) throws IOException {
        if (is == null) {
            return null;
        }

        // convert the input stream to StreamCache if the stream cache is not disabled
        if (exchange.getProperty(Exchange.DISABLE_HTTP_STREAM_CACHE, Boolean.FALSE, Boolean.class)) {
            return is;
        } else {
            CachedOutputStream cos = new CachedOutputStream(exchange);
            IOHelper.copyAndCloseInput(is, cos);
            return cos.getStreamCache();
        }
    }

}
