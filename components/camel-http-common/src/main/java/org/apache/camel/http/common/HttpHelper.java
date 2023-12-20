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
package org.apache.camel.http.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.support.CamelObjectInputStream;
import org.apache.camel.support.http.HttpUtil;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

public final class HttpHelper {

    private HttpHelper() {
        // Helper class
    }

    public static boolean isSecureConnection(String uri) {
        return org.apache.camel.http.base.HttpHelper.isSecureConnection(uri);
    }

    public static int[] parserHttpVersion(String s) throws ProtocolException {
        return org.apache.camel.http.base.HttpHelper.parserHttpVersion(s);
    }

    public static void setCharsetFromContentType(String contentType, Exchange exchange) {
        HttpUtil.setCharsetFromContentType(contentType, exchange);
    }

    public static String getCharsetFromContentType(String contentType) {
        return HttpUtil.getCharsetFromContentType(contentType);
    }

    /**
     * Writes the given object as response body to the servlet response
     * <p/>
     * The content type will be set to {@link HttpConstants#CONTENT_TYPE_JAVA_SERIALIZED_OBJECT}
     *
     * @param  response    servlet response
     * @param  target      object to write
     * @throws IOException is thrown if error writing
     */
    public static void writeObjectToServletResponse(ServletResponse response, Object target) throws IOException {
        response.setContentType(HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);
        writeObjectToStream(response.getOutputStream(), target);
    }

    /**
     * Writes the given object as response body to the output stream
     *
     * @param  stream      output stream
     * @param  target      object to write
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
     * @param      is                     input stream for the Java object
     * @return                            the java object, or <tt>null</tt> if input stream was <tt>null</tt>
     * @throws     ClassNotFoundException is thrown if class not found
     * @throws     IOException            can be thrown
     * @deprecated                        Camel 3.0 Please use the one which has the parameter of camel context
     */
    @Deprecated
    public static Object deserializeJavaObjectFromStream(InputStream is) throws ClassNotFoundException, IOException {
        return deserializeJavaObjectFromStream(is, null);
    }

    /**
     * Deserializes the input stream to a Java object
     *
     * @param  is                     input stream for the Java object
     * @param  context                the camel context which could help us to apply the customer classloader
     * @return                        the java object, or <tt>null</tt> if input stream was <tt>null</tt>
     * @throws ClassNotFoundException is thrown if class not found
     * @throws IOException            can be thrown
     */
    public static Object deserializeJavaObjectFromStream(InputStream is, CamelContext context)
            throws ClassNotFoundException, IOException {
        if (is == null) {
            return null;
        }

        Object answer;
        ObjectInputStream ois = new CamelObjectInputStream(is, context);
        try {
            answer = ois.readObject();
        } finally {
            IOHelper.close(ois);
        }

        return answer;
    }

    /**
     * Reads the request body from the given http servlet request.
     *
     * @param  request     http servlet request
     * @param  exchange    the exchange
     * @return             the request body, can be <tt>null</tt> if no body
     * @throws IOException is thrown if error reading request body
     */
    public static Object readRequestBodyFromServletRequest(HttpServletRequest request, Exchange exchange) throws IOException {
        InputStream is = HttpConverter.toInputStream(request, exchange);
        // when using servlet (camel-servlet and camel-jetty) then they should always use stream caching
        // as the message body is parsed for url-form and other things, so we need to be able to re-read the message body
        // however there is an option to turn this off, which is set as exchange property
        boolean streamCaching = !exchange.getProperty(Exchange.DISABLE_HTTP_STREAM_CACHE, false, boolean.class);
        if (streamCaching) {
            return cacheResponseBodyFromInputStream(is, exchange);
        } else {
            return is;
        }
    }

    /**
     * Caches the response body from the given input stream, which is needed by
     * {@link org.apache.camel.PollingConsumer}.
     *
     * @param  is          the input stream
     * @param  exchange    the exchange
     * @return             the cached response body
     * @throws IOException is thrown if error reading response body
     */
    public static Object cacheResponseBodyFromInputStream(InputStream is, Exchange exchange) throws IOException {
        if (is == null) {
            return null;
        }
        CachedOutputStream cos = new CachedOutputStream(exchange);
        // do not close IS as it comes from http server such as servlet and the
        // servlet input stream may be used by others besides Camel
        IOHelper.copy(is, cos);
        return cos.newStreamCache();
    }

    /**
     * Creates the URL to invoke.
     *
     * @param  exchange the exchange
     * @param  endpoint the endpoint
     * @return          the URL to invoke
     */
    public static String createURL(Exchange exchange, HttpCommonEndpoint endpoint) {
        // rest producer may provide an override url to be used which we should discard if using (hence the remove)
        String uri = (String) exchange.getIn().removeHeader(Exchange.REST_HTTP_URI);

        if (uri == null && !(endpoint.isBridgeEndpoint())) {
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
        // NOW the HTTP_PATH is just related path, we don't need to trim it
        if (path != null) {
            if (path.length() > 1 && path.startsWith("/")) {
                path = path.substring(1);
            }
            if (!path.isEmpty()) {
                // inject the dynamic path before the query params, if there are any
                int idx = uri.indexOf('?');

                // if there are no query params
                if (idx == -1) {
                    // make sure that there is exactly one "/" between HTTP_URI and HTTP_PATH
                    if (uri.endsWith("/") && path.startsWith("/")) {
                        uri = uri.concat(path.substring(1));
                    } else {
                        uri = uri.endsWith("/") || path.startsWith("/") ? uri : uri + "/";
                        uri = uri.concat(path);
                    }
                } else {
                    // there are query params, so inject the relative path in the right place
                    String base = uri.substring(0, idx);
                    base = base.endsWith("/") ? base : base + "/";
                    base = base.concat(path.startsWith("/") ? path.substring(1) : path);
                    uri = base.concat(uri.substring(idx));
                }
            }
        }

        // ensure uri is encoded to be valid
        uri = UnsafeUriCharactersEncoder.encodeHttpURI(uri);

        return uri;
    }

    /**
     * Creates the URI to invoke.
     *
     * @param  exchange the exchange
     * @param  url      the url to invoke
     * @param  endpoint the endpoint
     * @return          the URI to invoke
     */
    public static URI createURI(Exchange exchange, String url, HttpCommonEndpoint endpoint) throws URISyntaxException {
        URI uri = new URI(url);
        // rest producer may provide an override query string to be used which we should discard if using (hence the remove)
        String queryString = (String) exchange.getIn().removeHeader(Exchange.REST_HTTP_QUERY);
        // is a query string provided in the endpoint URI or in a header
        // (header overrules endpoint, raw query header overrules query header)
        if (queryString == null) {
            queryString = exchange.getIn().getHeader(Exchange.HTTP_RAW_QUERY, String.class);
        }
        if (queryString == null) {
            queryString = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        }
        if (queryString == null) {
            queryString = endpoint.getHttpUri().getRawQuery();
        }
        // We should use the query string from the HTTP_URI header
        if (queryString == null) {
            queryString = uri.getRawQuery();
        }
        if (queryString != null) {
            // need to encode query string
            queryString = UnsafeUriCharactersEncoder.encodeHttpURI(queryString);
            uri = URISupport.createURIWithQuery(uri, queryString);
        }
        return uri;
    }

    /**
     * Appends the key/value to the headers.
     * <p/>
     * This implementation supports keys with multiple values. In such situations the value will be a
     * {@link java.util.List} that contains the multiple values.
     *
     * @param headers headers
     * @param key     the key
     * @param value   the value
     */
    public static void appendHeader(Map<String, Object> headers, String key, Object value) {
        CollectionHelper.appendEntry(headers, key, value);
    }

    /**
     * Extracts the parameter value.
     * <p/>
     * This implementation supports HTTP multi value parameters which is based on the syntax of
     * <tt>[value1, value2, value3]</tt> by returning a {@link List} containing the values.
     * <p/>
     * If the value is not a HTTP mulit value the value is returned as is.
     *
     * @param  value the parameter value
     * @return       the extracted parameter value, see more details in javadoc.
     */
    public static Object extractHttpParameterValue(String value) {
        return org.apache.camel.http.base.HttpHelper.extractHttpParameterValue(value);
    }

    /**
     * Creates the HttpMethod to use to call the remote server, often either its GET or POST.
     *
     * @param  exchange           the exchange
     * @return                    the created method
     * @throws URISyntaxException
     */
    public static HttpMethods createMethod(Exchange exchange, HttpCommonEndpoint endpoint, boolean hasPayload)
            throws URISyntaxException {
        // is a query string provided in the endpoint URI or in a header (header overrules endpoint)
        String queryString = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        // We need also check the HTTP_URI header query part
        String uriString = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
        // resolve placeholders in uriString
        try {
            uriString = exchange.getContext().resolvePropertyPlaceholders(uriString);
        } catch (Exception e) {
            throw new RuntimeExchangeException("Cannot resolve property placeholders with uri: " + uriString, exchange, e);
        }
        if (uriString != null) {
            // in case the URI string contains unsafe characters
            uriString = UnsafeUriCharactersEncoder.encodeHttpURI(uriString);
            URI uri = new URI(uriString);
            queryString = uri.getQuery();
        }
        if (queryString == null) {
            queryString = endpoint.getHttpUri().getRawQuery();
        }

        HttpMethods answer;
        if (endpoint.getHttpMethod() != null) {
            // endpoint configured take precedence
            answer = endpoint.getHttpMethod();
        } else {
            // compute what method to use either GET or POST (header take precedence)
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
        }

        return answer;
    }

    /**
     * Checks whether the given http status code is within the ok range
     *
     * @param  statusCode        the status code
     * @param  okStatusCodeRange the ok range (inclusive)
     * @return                   <tt>true</tt> if ok, <tt>false</tt> otherwise
     */
    public static boolean isStatusCodeOk(int statusCode, String okStatusCodeRange) {
        return org.apache.camel.http.base.HttpHelper.isStatusCodeOk(statusCode, okStatusCodeRange);
    }

}
