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
package org.apache.camel.component.vertx.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.TrustOptions;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

public final class VertxHttpHelper {

    private VertxHttpHelper() {
        // Utility class
    }

    /**
     * Configures key store and trust store options for the Vert.x client and server
     */
    public static void setupSSLOptions(SSLContextParameters sslContextParameters, TCPSSLOptions options) {
        options.setSsl(true);
        options.setKeyCertOptions(new KeyCertOptions() {
            @Override
            public KeyManagerFactory getKeyManagerFactory(Vertx vertx) throws Exception {
                return createKeyManagerFactory(sslContextParameters);
            }

            @Override
            @SuppressWarnings("deprecation")
            public KeyCertOptions clone() {
                return this;
            }
        });
        options.setTrustOptions(new TrustOptions() {
            @Override
            public TrustManagerFactory getTrustManagerFactory(Vertx vertx) throws Exception {
                return createTrustManagerFactory(sslContextParameters);
            }

            @Override
            @SuppressWarnings("deprecation")
            public TrustOptions clone() {
                return this;
            }
        });
    }

    /**
     * Resolves a HTTP URI query string from the given exchange message headers
     */
    public static String resolveQueryString(Exchange exchange) throws URISyntaxException {
        Message message = exchange.getMessage();
        String queryString = (String) message.removeHeader(Exchange.REST_HTTP_QUERY);
        if (ObjectHelper.isEmpty(queryString)) {
            queryString = message.getHeader(Exchange.HTTP_QUERY, String.class);
        }

        String uriString = message.getHeader(Exchange.HTTP_URI, String.class);
        uriString = exchange.getContext().resolvePropertyPlaceholders(uriString);

        if (uriString != null) {
            uriString = UnsafeUriCharactersEncoder.encodeHttpURI(uriString);
            URI uri = new URI(uriString);
            queryString = uri.getQuery();
        }

        return queryString;
    }

    /**
     * Resolves a HTTP URI and path string from the given exchange message headers
     */
    public static URI resolveHttpURI(Exchange exchange) throws URISyntaxException {
        Message message = exchange.getMessage();
        String uri = (String) message.removeHeader(Exchange.REST_HTTP_URI);

        if (ObjectHelper.isEmpty(uri)) {
            uri = message.getHeader(Exchange.HTTP_URI, String.class);
        }

        if (ObjectHelper.isEmpty(uri)) {
            return null;
        }

        // Resolve property placeholders that may be present in the URI
        uri = exchange.getContext().resolvePropertyPlaceholders(uri);

        // Append HTTP_PATH header value if is present
        String path = message.getHeader(Exchange.HTTP_PATH, String.class);
        if (ObjectHelper.isNotEmpty(path)) {
            if (path.startsWith("/")) {
                path = path.substring(1);
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

        uri = UnsafeUriCharactersEncoder.encodeHttpURI(uri);

        return new URI(uri);
    }

    /**
     * Verifies whether the Content-Type exchange header value matches an expected value
     */
    public static boolean isContentTypeMatching(Exchange exchange, String expected) {
        return isContentTypeMatching(expected, ExchangeHelper.getContentType(exchange));
    }

    /**
     * Verifies whether the expected Content-Type value matches an expected value
     */
    public static boolean isContentTypeMatching(String expected, String actual) {
        return actual != null && expected.equals(actual);
    }

    /**
     * Writes the given target object to an {@link ObjectOutputStream}
     */
    public static void writeObjectToStream(OutputStream stream, Object target) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(stream);
        oos.writeObject(target);
        oos.flush();
        IOHelper.close(oos);
    }

    /**
     * Deserializes an object from the given {@link InputStream}
     */
    public static Object deserializeJavaObjectFromStream(InputStream is) throws ClassNotFoundException, IOException {
        if (is == null) {
            return null;
        }

        Object answer;
        ObjectInputStream ois = new ObjectInputStream(is);
        try {
            answer = ois.readObject();
        } finally {
            IOHelper.close(ois);
        }

        return answer;
    }

    /**
     * Retrieves the charset from the exchange Content-Type header, or falls back to the CamelCharsetName exchange
     * property when not available
     */
    public static String getCharsetFromExchange(Exchange exchange) {
        String charset = null;
        if (exchange != null) {
            String contentType = exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
            charset = HttpHelper.getCharsetFromContentType(contentType);
            if (ObjectHelper.isEmpty(charset)) {
                charset = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
            }
        }
        return charset;
    }

    /**
     * Creates a KeyManagerFactory from a given SSLContextParameters
     */
    private static KeyManagerFactory createKeyManagerFactory(SSLContextParameters sslContextParameters) throws Exception {
        final KeyManagersParameters keyManagers = sslContextParameters.getKeyManagers();
        if (keyManagers == null) {
            return null;
        }

        String kmfAlgorithm = keyManagers.getAlgorithm();
        if (kmfAlgorithm == null) {
            kmfAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        }

        KeyManagerFactory kmf;
        if (keyManagers.getProvider() == null) {
            kmf = KeyManagerFactory.getInstance(kmfAlgorithm);
        } else {
            kmf = KeyManagerFactory.getInstance(kmfAlgorithm, keyManagers.getProvider());
        }

        char[] kmfPassword = null;
        if (keyManagers.getKeyPassword() != null) {
            kmfPassword = keyManagers.getKeyPassword().toCharArray();
        }

        KeyStore ks = keyManagers.getKeyStore() == null ? null : keyManagers.getKeyStore().createKeyStore();

        kmf.init(ks, kmfPassword);
        return kmf;
    }

    /**
     * Creates a TrustManagerFactory from a given SSLContextParameters
     */
    private static TrustManagerFactory createTrustManagerFactory(SSLContextParameters sslContextParameters) throws Exception {
        final TrustManagersParameters trustManagers = sslContextParameters.getTrustManagers();
        if (trustManagers == null) {
            return null;
        }

        TrustManagerFactory tmf = null;

        if (trustManagers.getKeyStore() != null) {
            String tmfAlgorithm = trustManagers.getAlgorithm();
            if (tmfAlgorithm == null) {
                tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            }

            if (trustManagers.getProvider() == null) {
                tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            } else {
                tmf = TrustManagerFactory.getInstance(tmfAlgorithm, trustManagers.getProvider());
            }

            KeyStore ks = trustManagers.getKeyStore() == null ? null : trustManagers.getKeyStore().createKeyStore();
            tmf.init(ks);
        }
        return tmf;
    }
}
