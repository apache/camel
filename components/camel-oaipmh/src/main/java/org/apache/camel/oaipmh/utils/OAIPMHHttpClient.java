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
package org.apache.camel.oaipmh.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OAIPMHHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(OAIPMHHttpClient.class);

    private boolean ignoreSSLWarnings;

    public String doRequest(
            URI baseURI, String verb, String set, String from, String until, String metadataPrefix, String token,
            String identifier)
            throws IOException, URISyntaxException {

        try (CloseableHttpClient httpclient = getCloseableHttpClient()) {
            URIBuilder builder = new URIBuilder();
            builder.setScheme(baseURI.getScheme())
                    .setHost(baseURI.getHost())
                    .setPort(baseURI.getPort())
                    .setPath(baseURI.getPath())
                    .addParameter("verb", verb)
                    .addParameters(URLEncodedUtils.parse(baseURI, Charset.defaultCharset()));

            if (identifier != null) {
                builder.addParameter("identifier", identifier);
            }

            if (token != null) {
                builder.addParameter("resumptionToken", token);
            } else {
                if (metadataPrefix != null) {
                    builder.addParameter("metadataPrefix", metadataPrefix);
                }
                if (set != null) {
                    builder.addParameter("set", set);
                }
                if (from != null) {
                    builder.addParameter("from", from);
                }
                if (until != null) {
                    builder.addParameter("until", until);
                }
            }

            HttpGet httpget = new HttpGet(builder.build());

            LOG.info("Executing request: {} ", httpget);

            // Create a custom response handler
            HttpClientResponseHandler<String> responseHandler = new HttpClientResponseHandler<String>() {

                @Override
                public String handleResponse(final ClassicHttpResponse response) throws HttpException, IOException {
                    int status = response.getCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        if (entity == null) {
                            throw new IOException("No response received");
                        }
                        return EntityUtils.toString(entity, StandardCharsets.UTF_8);
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }

            };
            String responseBody = httpclient.execute(httpget, responseHandler);

            LOG.debug("Response received: {}", responseBody);

            return responseBody;
        }
    }

    protected CloseableHttpClient getCloseableHttpClient() throws IOException {
        if (isIgnoreSSLWarnings()) {
            try {
                SSLContextBuilder builder = new SSLContextBuilder();
                builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                        builder.build());
                RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.create();
                registryBuilder.register("https", sslsf);
                return HttpClients.custom()
                        .setConnectionManager(new PoolingHttpClientConnectionManager(registryBuilder.build()))
                        .build();
            } catch (KeyManagementException | KeyStoreException | NoSuchAlgorithmException ex) {
                throw new IOException("The HTTP Client could not be started", ex);
            }
        } else {
            return HttpClients.createDefault();
        }
    }

    public boolean isIgnoreSSLWarnings() {
        return ignoreSSLWarnings;
    }

    public void setIgnoreSSLWarnings(boolean ignoreSSLWarnings) {
        this.ignoreSSLWarnings = ignoreSSLWarnings;
    }

}
