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
package org.apache.camel.component.netty.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

/**
 * Helpers.
 */
public final class NettyHttpHelper {

    private NettyHttpHelper() {
    }

    public static void setCharsetFromContentType(String contentType, Exchange exchange) {
        String charset = getCharsetFromContentType(contentType);
        if (charset != null) {
            exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, IOHelper.normalizeCharset(charset));
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
                    charset = StringHelper.before(charset, ";");
                }
                return IOHelper.normalizeCharset(charset);
            }
        }
        return null;
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
    @SuppressWarnings("unchecked")
    public static void appendHeader(Map<String, Object> headers, String key, Object value) {
        if (headers.containsKey(key)) {
            Object existing = headers.get(key);
            List<Object> list;
            if (existing instanceof List) {
                list = (List<Object>) existing;
            } else {
                list = new ArrayList<>();
                list.add(existing);
            }
            list.add(value);
            value = list;
        }

        headers.put(key, value);
    }

    /**
     * Creates the {@link HttpMethod} to use to call the remote server, often either its GET or POST.
     *
     * @param  message the Camel message
     * @return         the created method
     */
    public static HttpMethod createMethod(Message message, boolean hasPayload) {
        // use header first
        HttpMethod m = message.getHeader(NettyHttpConstants.HTTP_METHOD, HttpMethod.class);
        if (m != null) {
            return m;
        }
        String name = message.getHeader(NettyHttpConstants.HTTP_METHOD, String.class);
        if (name != null) {
            // must be in upper case
            name = name.toUpperCase();
            return HttpMethod.valueOf(name);
        }

        if (hasPayload) {
            // use POST if we have payload
            return HttpMethod.POST;
        } else {
            // fallback to GET
            return HttpMethod.GET;
        }
    }

    public static Exception populateNettyHttpOperationFailedException(
            Exchange exchange, String url, FullHttpResponse response, int responseCode, boolean transferException) {
        String statusText = response.status().reasonPhrase();

        if (responseCode >= 300 && responseCode < 400) {
            String redirectLocation = response.headers().get("location");
            if (redirectLocation != null) {
                return new NettyHttpOperationFailedException(url, responseCode, statusText, redirectLocation, response);
            } else {
                // no redirect location
                return new NettyHttpOperationFailedException(url, responseCode, statusText, null, response);
            }
        }

        if (transferException) {
            String contentType = response.headers().get(NettyHttpConstants.CONTENT_TYPE);
            if (NettyHttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentType)) {
                // if the response was a serialized exception then use that
                InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, response);
                if (is != null) {
                    try {
                        Object body = deserializeJavaObjectFromStream(is);
                        if (body instanceof Exception) {
                            return (Exception) body;
                        }
                    } catch (Exception e) {
                        return e;
                    } finally {
                        IOHelper.close(is);
                    }
                }
            }
        }

        // internal server error (error code 500)
        return new NettyHttpOperationFailedException(url, responseCode, statusText, null, response);
    }

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
     * Creates the URL to invoke.
     *
     * @param  exchange the exchange
     * @param  endpoint the endpoint
     * @return          the URL to invoke
     */
    public static String createURL(Exchange exchange, NettyHttpEndpoint endpoint) {
        // rest producer may provide an override url to be used which we should discard if using (hence the remove)
        String uri = (String) exchange.getIn().removeHeader(Exchange.REST_HTTP_URI);
        if (uri == null) {
            uri = endpoint.getEndpointUri();
        }

        // resolve placeholders in uri
        try {
            uri = exchange.getContext().resolvePropertyPlaceholders(uri);
        } catch (Exception e) {
            throw new RuntimeExchangeException("Cannot resolve property placeholders with uri: " + uri, exchange, e);
        }

        // append HTTP_PATH to HTTP_URI if it is provided in the header
        String path = exchange.getIn().getHeader(NettyHttpConstants.HTTP_PATH, String.class);
        // NOW the HTTP_PATH is just related path, we don't need to trim it
        if (path != null && !path.isEmpty()) {
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            // inject the dynamic path before the query params, if there are any
            int idx = uri.indexOf('?');

            // if there are no query params
            if (idx == -1) {
                // make sure that there is exactly one "/" between HTTP_URI and HTTP_PATH
                uri = uri.endsWith("/") ? uri : uri + "/";
                uri = uri.concat(path);
            } else {
                // there are query params, so inject the relative path in the right place
                String base = uri.substring(0, idx);
                base = base.endsWith("/") ? base : base + "/";
                base = base.concat(path);
                uri = base.concat(uri.substring(idx));
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
     * @return          the URI to invoke
     */
    public static URI createURI(Exchange exchange, String url) throws URISyntaxException {
        URI uri = new URI(url);

        // rest producer may provide an override query string to be used which we should discard if using (hence the remove)
        String queryString = (String) exchange.getIn().removeHeader(Exchange.REST_HTTP_QUERY);
        // is a query string provided in the endpoint URI or in a header
        // (header overrules endpoint, raw query header overrules query header)
        if (queryString == null) {
            queryString = exchange.getIn().getHeader(NettyHttpConstants.HTTP_RAW_QUERY, String.class);
        }
        if (queryString == null) {
            queryString = exchange.getIn().getHeader(NettyHttpConstants.HTTP_QUERY, String.class);
        }
        if (queryString == null) {
            // use raw as we encode just below
            queryString = uri.getRawQuery();
        }
        if (queryString != null) {
            // need to encode query string
            queryString = UnsafeUriCharactersEncoder.encodeHttpURI(queryString);
            if (ObjectHelper.isEmpty(uri.getPath())) {
                // If queryString is present, the path cannot be empty - CAMEL-13707
                uri = new URI(url + "/");
            }

            uri = URISupport.createURIWithQuery(uri, queryString);
        }
        return uri;
    }

    /**
     * Checks whether the given http status code is within the ok range
     *
     * @param  statusCode        the status code
     * @param  okStatusCodeRange the ok range (inclusive)
     * @return                   <tt>true</tt> if ok, <tt>false</tt> otherwise
     */
    public static boolean isStatusCodeOk(int statusCode, String okStatusCodeRange) {
        String[] ranges = okStatusCodeRange.split(",");
        for (String range : ranges) {
            boolean ok;
            if (range.contains("-")) {
                int from = Integer.parseInt(StringHelper.before(range, "-"));
                int to = Integer.parseInt(StringHelper.after(range, "-"));
                ok = statusCode >= from && statusCode <= to;
            } else {
                int exact = Integer.parseInt(range);
                ok = exact == statusCode;
            }
            if (ok) {
                return true;
            }
        }
        return false;
    }

}
