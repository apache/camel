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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.CamelObjectInputStream;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpHelper {

    private static final Logger LOG = LoggerFactory.getLogger(HttpHelper.class);

    private HttpHelper() {
        // Helper class
    }

    public static boolean isSecureConnection(String uri) {
        return uri.startsWith("https");
    }

    public static int[] parserHttpVersion(String s) throws ProtocolException {
        int major;
        int minor;
        if (s == null) {
            throw new IllegalArgumentException("String may not be null");
        }
        if (!s.startsWith("HTTP/")) {
            throw new ProtocolException("Invalid HTTP version string: " + s);
        }
        int i1 = "HTTP/".length();
        int i2 = s.indexOf(".", i1);
        if (i2 == -1) {
            throw new ProtocolException("Invalid HTTP version number: " + s);
        }
        try {
            major = Integer.parseInt(s.substring(i1, i2));
        } catch (NumberFormatException e) {
            throw new ProtocolException("Invalid HTTP major version number: " + s);
        }
        i1 = i2 + 1;
        i2 = s.length();
        try {
            minor = Integer.parseInt(s.substring(i1, i2));
        } catch (NumberFormatException e) {
            throw new ProtocolException("Invalid HTTP minor version number: " + s);
        }
        return new int[]{major, minor};
    }

    @SuppressWarnings("deprecation")
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

    public static String getCharsetFromContentType(String contentType) {
        if (contentType != null) {
            // find the charset and set it to the Exchange
            int index = contentType.indexOf("charset=");
            if (index > 0) {
                String charset = contentType.substring(index + 8);
                // there may be another parameter after a semi colon, so skip that
                if (charset.contains(";")) {
                    charset = ObjectHelper.before(charset, ";");
                }
                return IOHelper.normalizeCharset(charset);
            }
        }
        return null;
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
     * @deprecated Camel 3.0 
     * Please use the one which has the parameter of camel context
     */
    @Deprecated
    public static Object deserializeJavaObjectFromStream(InputStream is) throws ClassNotFoundException, IOException {
        return deserializeJavaObjectFromStream(is, null);
    }
    
    /**
     * Deserializes the input stream to a Java object
     *
     * @param is input stream for the Java object
     * @param context the camel context which could help us to apply the customer classloader
     * @return the java object, or <tt>null</tt> if input stream was <tt>null</tt>
     * @throws ClassNotFoundException is thrown if class not found
     * @throws IOException can be thrown
     */
    public static Object deserializeJavaObjectFromStream(InputStream is, CamelContext context) throws ClassNotFoundException, IOException {
        if (is == null) {
            return null;
        }

        Object answer = null;
        ObjectInputStream ois = new CamelObjectInputStream(is, context);
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
     * @return the request body, can be <tt>null</tt> if no body
     * @throws IOException is thrown if error reading response body
     */
    public static Object readRequestBodyFromServletRequest(HttpServletRequest request, Exchange exchange) throws IOException {
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
    public static Object readRequestBodyFromInputStream(InputStream is, Exchange exchange) throws IOException {
        if (is == null) {
            return null;
        }
        boolean disableStreamCaching = false;
        // Just take the consideration of the setting of Camel Context StreamCaching
        if (exchange.getContext() instanceof DefaultCamelContext) { 
            DefaultCamelContext context = (DefaultCamelContext) exchange.getContext();
            disableStreamCaching = !context.isStreamCaching();
        }
        // convert the input stream to StreamCache if the stream cache is not disabled
        if (exchange.getProperty(Exchange.DISABLE_HTTP_STREAM_CACHE, disableStreamCaching, Boolean.class)) {
            return is;
        } else {
            CachedOutputStream cos = new CachedOutputStream(exchange);
            IOHelper.copyAndCloseInput(is, cos);
            return cos.newStreamCache();
        }
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
            return cos.newStreamCache();
        }
    }

    /**
     * Creates the URL to invoke.
     *
     * @param exchange the exchange
     * @param endpoint the endpoint
     * @return the URL to invoke
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
            if (path.length() > 0) {
                // inject the dynamic path before the query params, if there are any
                int idx = uri.indexOf("?");

                // if there are no query params
                if (idx == -1) {
                    // make sure that there is exactly one "/" between HTTP_URI and HTTP_PATH
                    uri = uri.endsWith("/") || path.startsWith("/") ? uri : uri + "/";
                    uri = uri.concat(path);
                } else {
                    // there are query params, so inject the relative path in the right place
                    String base = uri.substring(0, idx);
                    base = base.endsWith("/") ? base : base + "/";
                    base = base.concat(path);
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
     * @param exchange the exchange
     * @param url      the url to invoke
     * @param endpoint the endpoint
     * @return the URI to invoke
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
     * This implementation supports keys with multiple values. In such situations the value
     * will be a {@link java.util.List} that contains the multiple values.
     *
     * @param headers  headers
     * @param key      the key
     * @param value    the value
     */
    @SuppressWarnings("unchecked")
    public static void appendHeader(Map<String, Object> headers, String key, Object value) {
        if (headers.containsKey(key)) {
            Object existing = headers.get(key);
            List<Object> list;
            if (existing instanceof List) {
                list = (List<Object>) existing;
            } else {
                list = new ArrayList<Object>();
                list.add(existing);
            }
            list.add(value);
            value = list;
        }

        headers.put(key, value);
    }

    /**
     * Extracts the parameter value.
     * <p/>
     * This implementation supports HTTP multi value parameters which
     * is based on the syntax of <tt>[value1, value2, value3]</tt> by returning
     * a {@link List} containing the values.
     * <p/>
     * If the value is not a HTTP mulit value the value is returned as is.
     *
     * @param value the parameter value
     * @return the extracted parameter value, see more details in javadoc.
     */
    public static Object extractHttpParameterValue(String value) {
        if (value == null || ObjectHelper.isEmpty(value)) {
            return value;
        }

        // trim value before checking for multiple parameters
        String trimmed = value.trim();

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            // remove the [ ] markers
            trimmed = trimmed.substring(1, trimmed.length() - 1);
            List<String> list = new ArrayList<String>();
            String[] values = trimmed.split(",");
            for (String s : values) {
                list.add(s.trim());
            }
            return list;
        }

        return value;
    }

    /**
     * Processes any custom {@link org.apache.camel.http.common.UrlRewrite}.
     *
     * @param exchange    the exchange
     * @param url         the url
     * @param endpoint    the http endpoint
     * @param producer    the producer
     * @return            the rewritten url, or <tt>null</tt> to use original url
     * @throws Exception is thrown if any error during rewriting url
     */
    public static String urlRewrite(Exchange exchange, String url, HttpCommonEndpoint endpoint, Producer producer) throws Exception {
        String answer = null;

        String relativeUrl;
        if (endpoint.getUrlRewrite() != null) {
            // we should use the relative path if possible
            String baseUrl;
            relativeUrl = endpoint.getHttpUri().toASCIIString();
            // strip query parameters from relative url
            if (relativeUrl.contains("?")) {
                relativeUrl = ObjectHelper.before(relativeUrl, "?");
            }
            if (url.startsWith(relativeUrl)) {
                baseUrl = url.substring(0, relativeUrl.length());
                relativeUrl = url.substring(relativeUrl.length());
            } else {
                baseUrl = null;
                relativeUrl = url;
            }
            // mark it as null if its empty
            if (ObjectHelper.isEmpty(relativeUrl)) {
                relativeUrl = null;
            }

            String newUrl;
            if (endpoint.getUrlRewrite() instanceof HttpServletUrlRewrite) {
                // its servlet based, so we need the servlet request
                HttpServletRequest request = exchange.getIn().getBody(HttpServletRequest.class);
                if (request == null) {
                    HttpMessage msg = exchange.getIn(HttpMessage.class);
                    if (msg != null) {
                        request = msg.getRequest();
                    }
                }
                if (request == null) {
                    throw new IllegalArgumentException("UrlRewrite " + endpoint.getUrlRewrite() + " requires the message body to be a"
                            + "HttpServletRequest instance, but was: " + ObjectHelper.className(exchange.getIn().getBody()));
                }
                // we need to adapt the context-path to be the path from the endpoint, if it came from a http based endpoint
                // as eg camel-jetty have hardcoded context-path as / for all its servlets/endpoints
                // we have the actual context-path stored as a header with the key CamelServletContextPath
                String contextPath = exchange.getIn().getHeader("CamelServletContextPath", String.class);
                request = new UrlRewriteHttpServletRequestAdapter(request, contextPath);
                newUrl = ((HttpServletUrlRewrite) endpoint.getUrlRewrite()).rewrite(url, relativeUrl, producer, request);
            } else {
                newUrl = endpoint.getUrlRewrite().rewrite(url, relativeUrl, producer);
            }

            if (ObjectHelper.isNotEmpty(newUrl) && !newUrl.equals(url)) {
                // we got a new url back, that can either be a new absolute url
                // or a new relative url
                if (newUrl.startsWith("http:") || newUrl.startsWith("https:")) {
                    answer = newUrl;
                } else if (baseUrl != null) {
                    // avoid double // when adding the urls
                    if (baseUrl.endsWith("/") && newUrl.startsWith("/")) {
                        answer = baseUrl + newUrl.substring(1);
                    } else {
                        answer = baseUrl + newUrl;
                    }
                } else {
                    // use the new url as is
                    answer = newUrl;
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Using url rewrite to rewrite from url {} to {} -> {}",
                            new Object[]{relativeUrl != null ? relativeUrl : url, newUrl, answer});
                }
            }
        }

        return answer;
    }

    /**
     * Creates the HttpMethod to use to call the remote server, often either its GET or POST.
     *
     * @param exchange  the exchange
     * @return the created method
     * @throws URISyntaxException
     */
    public static HttpMethods createMethod(Exchange exchange, HttpCommonEndpoint endpoint, boolean hasPayload) throws URISyntaxException {
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
     * @param statusCode the status code
     * @param okStatusCodeRange the ok range (inclusive)
     * @return <tt>true</tt> if ok, <tt>false</tt> otherwise
     */
    public static boolean isStatusCodeOk(int statusCode, String okStatusCodeRange) {
        String[] ranges = okStatusCodeRange.split(",");
        for (String range : ranges) {
            boolean ok;
            if (range.contains("-")) {
                int from = Integer.valueOf(StringHelper.before(range, "-"));
                int to = Integer.valueOf(StringHelper.after(range, "-"));
                ok =  statusCode >= from && statusCode <= to;
            } else {
                int exact = Integer.valueOf(range);
                ok = exact == statusCode;
            }
            if (ok) {
                return true;
            }
        }
        return false;
    }

}
