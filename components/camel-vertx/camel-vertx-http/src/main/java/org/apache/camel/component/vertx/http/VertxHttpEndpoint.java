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

import io.vertx.core.Vertx;
import io.vertx.core.net.ProxyOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.ext.web.client.spi.CookieStore;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.vertx.common.VertxHelper;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.http.HttpUtil;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;

@UriEndpoint(firstVersion = "3.5.0", scheme = "vertx-http", title = "Vert.x HTTP Client", syntax = "vertx-http:httpUri",
             category = { Category.HTTP }, producerOnly = true, lenientProperties = true,
             headersClass = VertxHttpConstants.class)
public class VertxHttpEndpoint extends DefaultEndpoint {

    @UriParam
    private final VertxHttpConfiguration configuration;

    private WebClient webClient;
    private int minOkRange;
    private int maxOkRange;

    public VertxHttpEndpoint(String uri, VertxHttpComponent component, VertxHttpConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public VertxHttpComponent getComponent() {
        return (VertxHttpComponent) super.getComponent();
    }

    @Override
    protected void doInit() throws Exception {
        String range = configuration.getOkStatusCodeRange();
        parseStatusRange(range);
    }

    private void parseStatusRange(String range) {
        if (!range.contains(",")) {
            if (!HttpUtil.parseStatusRange(range, this::setRanges)) {
                minOkRange = Integer.parseInt(range);
                maxOkRange = minOkRange;
            }
        }
    }

    private void setRanges(int minOkRange, int maxOkRange) {
        this.minOkRange = minOkRange;
        this.maxOkRange = maxOkRange;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new VertxHttpProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("vertx-http consumers are not supported");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (webClient == null) {
            WebClientOptions options = configuration.getWebClientOptions();
            if (options == null) {
                options = new WebClientOptions();
                options.setTryUseCompression(configuration.isUseCompression());
                options.setConnectTimeout(configuration.getConnectTimeout());
                configureProxyOptionsIfRequired(options);
            }

            SSLContextParameters sslContextParameters = configuration.getSslContextParameters();
            if (sslContextParameters != null) {
                VertxHelper.setupSSLOptions(getCamelContext(), sslContextParameters, options);
            }

            webClient = WebClient.create(getVertx(), options);
            if (configuration.isSessionManagement()) {
                CookieStore cookieStore
                        = configuration.getCookieStore() == null ? CookieStore.build() : configuration.getCookieStore();
                webClient = WebClientSession.create(webClient, cookieStore);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (webClient != null) {
            webClient.close();
            webClient = null;
        }
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    public VertxHttpConfiguration getConfiguration() {
        return configuration;
    }

    protected Vertx getVertx() {
        return getComponent().getVertx();
    }

    protected WebClient getWebClient() {
        return this.webClient;
    }

    protected boolean isStatusCodeOk(int responseCode) {
        if (minOkRange > 0) {
            return responseCode >= minOkRange && responseCode <= maxOkRange;
        } else {
            return HttpHelper.isStatusCodeOk(responseCode, configuration.getOkStatusCodeRange());
        }
    }

    private void configureProxyOptionsIfRequired(WebClientOptions options) {
        if (isProxyConfigurationPresent()) {
            ProxyOptions proxyOptions = new ProxyOptions();

            if (ObjectHelper.isNotEmpty(configuration.getProxyHost())) {
                proxyOptions.setHost(configuration.getProxyHost());
            }

            if (ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
                proxyOptions.setPort(configuration.getProxyPort());
            }

            if (ObjectHelper.isNotEmpty(configuration.getProxyUsername())) {
                proxyOptions.setUsername(configuration.getProxyUsername());
            }

            if (ObjectHelper.isNotEmpty(configuration.getProxyPassword())) {
                proxyOptions.setPassword(configuration.getProxyPassword());
            }

            if (ObjectHelper.isNotEmpty(configuration.getProxyType())) {
                proxyOptions.setType(configuration.getProxyType());
            }

            options.setProxyOptions(proxyOptions);
        }
    }

    private boolean isProxyConfigurationPresent() {
        return ObjectHelper.isNotEmpty(configuration.getProxyHost())
                || ObjectHelper.isNotEmpty(configuration.getProxyPort())
                || ObjectHelper.isNotEmpty(configuration.getProxyUsername())
                || ObjectHelper.isNotEmpty(configuration.getProxyPassword())
                || ObjectHelper.isNotEmpty(configuration.getProxyType());
    }
}
