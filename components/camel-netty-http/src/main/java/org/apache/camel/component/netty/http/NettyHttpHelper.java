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
package org.apache.camel.component.netty.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.util.IOHelper;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * Helpers.
 */
public final class NettyHttpHelper {

    private NettyHttpHelper() {
    }

    @SuppressWarnings("deprecation")
    public static void setCharsetFromContentType(String contentType, Exchange exchange) {
        String charset = getCharsetFromContentType(contentType);
        if (charset != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, IOConverter.normalizeCharset(charset));
        }
    }

    public static String getCharsetFromContentType(String contentType) {
        if (contentType != null) {
            // find the charset and set it to the Exchange
            int index = contentType.indexOf("charset=");
            if (index > 0) {
                String charset = contentType.substring(index + 8);
                return IOHelper.normalizeCharset(charset);
            }
        }
        return null;
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
     * Creates the {@link HttpMethod} to use to call the remote server, often either its GET or POST.
     *
     * @param message  the Camel message
     * @return the created method
     */
    public static HttpMethod createMethod(Message message, boolean hasPayload) {
        // use header first
        HttpMethod m = message.getHeader(Exchange.HTTP_METHOD, HttpMethod.class);
        if (m != null) {
            return m;
        }
        String name = message.getHeader(Exchange.HTTP_METHOD, String.class);
        if (name != null) {
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

    public static Exception populateNettyHttpOperationFailedException(Exchange exchange, String url, HttpResponse response, int responseCode, boolean transferException) {
        String uri = url;
        String statusText = response.getStatus().getReasonPhrase();

        if (responseCode >= 300 && responseCode < 400) {
            String redirectLocation = response.getHeader("location");
            if (redirectLocation != null) {
                return new NettyHttpOperationFailedException(uri, responseCode, statusText, redirectLocation, response);
            } else {
                // no redirect location
                return new NettyHttpOperationFailedException(uri, responseCode, statusText, null, response);
            }
        }

        if (transferException) {
            String contentType = response.getHeader(Exchange.CONTENT_TYPE);
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
        return new NettyHttpOperationFailedException(uri, responseCode, statusText, null, response);
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

}
