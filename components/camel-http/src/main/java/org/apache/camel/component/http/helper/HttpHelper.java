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
package org.apache.camel.component.http.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.URI;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.http.HttpConstants;
import org.apache.camel.component.http.HttpConverter;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.component.http.HttpMethods;
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
        writeObjectToStream(response.getOutputStream(), target);
    }

    /**
     * Writes the given object as response body to the output stream
     *
     * @param stream output stream
     * @param target   object to write
     * @throws IOException is thrown if error writing
     */
    public static void writeObjectToStream(OutputStream stream, Object target) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(stream);
        oos.writeObject(target);
        oos.flush();
        IOHelper.close(oos);
    }

    /**
     * Deserializes the input stream to a Java object
     *
     * @param is input stream for the Java object
     * @return the java object, or <tt>null</tt> if input stream was <tt>null</tt>
     * @throws ClassNotFoundException is thrown if class not found
     * @throws IOException can be thrown
     */
    public static Object deserializeJavaObjectFromStream(InputStream is) throws ClassNotFoundException, IOException {
        if (is == null) {
            return null;
        }

        Object answer = null;
        ObjectInputStream ois = new ObjectInputStream(is);
        try {
            answer = ois.readObject();
        } finally {
            IOHelper.close(ois);
        }

        return answer;
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

    /**
     * Creates the URL to invoke.
     *
     * @param exchange the exchange
     * @param endpoint the endpoint
     * @return the URL to invoke
     */
    public static String createURL(Exchange exchange, HttpEndpoint endpoint) {
        String uri = null;
        if (!(endpoint.isBridgeEndpoint())) {
            uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
        }
        if (uri == null) {
            uri = endpoint.getHttpUri().toASCIIString();
        }

        // resolve placeholders in uri
        try {
            uri = exchange.getContext().resolvePropertyPlaceholders(uri);
        } catch (Exception e) {
            throw new RuntimeExchangeException("Cannot resolve property placeholders with uri: " + uri, exchange, e);
        }

        // append HTTP_PATH to HTTP_URI if it is provided in the header
        String path = exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class);
        if (path != null) {
            if (path.startsWith("/")) {
                URI baseURI;
                String baseURIString = exchange.getIn().getHeader(Exchange.HTTP_BASE_URI, String.class);
                try {
                    if (baseURIString == null) {
                        if (exchange.getFromEndpoint() != null) {
                            baseURIString = exchange.getFromEndpoint().getEndpointUri();
                        } else {
                            // will set a default one for it
                            baseURIString = "/";
                        }
                    }
                    baseURI = new URI(baseURIString);
                    String basePath = baseURI.getRawPath();
                    if (path.startsWith(basePath)) {
                        path = path.substring(basePath.length());
                        if (path.startsWith("/")) {
                            path = path.substring(1);
                        }
                    } else {
                        throw new RuntimeExchangeException("Cannot analyze the Exchange.HTTP_PATH header, due to: cannot find the right HTTP_BASE_URI", exchange);
                    }
                } catch (Throwable t) {
                    throw new RuntimeExchangeException("Cannot analyze the Exchange.HTTP_PATH header, due to: " + t.getMessage(), exchange, t);
                }

            }
            if (path.length() > 0) {
                // make sure that there is exactly one "/" between HTTP_URI and
                // HTTP_PATH
                if (!uri.endsWith("/")) {
                    uri = uri + "/";
                }
                uri = uri.concat(path);
            }
        }
        return uri;
    }

    /**
     * Creates the HttpMethod to use to call the remote server, often either its GET or POST.
     *
     * @param exchange  the exchange
     * @return the created method
     */
    public static HttpMethods createMethod(Exchange exchange, HttpEndpoint endpoint, boolean hasPayload) {
        // is a query string provided in the endpoint URI or in a header (header
        // overrules endpoint)
        String queryString = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        if (queryString == null) {
            queryString = endpoint.getHttpUri().getQuery();
        }

        // compute what method to use either GET or POST
        HttpMethods answer;
        HttpMethods m = exchange.getIn().getHeader(Exchange.HTTP_METHOD, HttpMethods.class);
        if (m != null) {
            // always use what end-user provides in a header
            answer = m;
        } else if (queryString != null) {
            // if a query string is provided then use GET
            answer = HttpMethods.GET;
        } else {
            // fallback to POST if we have payload, otherwise GET
            answer = hasPayload ? HttpMethods.POST : HttpMethods.GET;
        }

        return answer;
    }
}
