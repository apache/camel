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
package org.apache.camel.component.cm;

import java.util.UUID;

import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.StringHelper;

/**
 * The cm-sms component allows to integrate with <a href="https://www.cmtelecom.com/">CM SMS Gateway</a>.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "cm-sms", title = "CM SMS Gateway", syntax = "cm-sms:host", label = "mobile", producerOnly = true)
public class CMEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = "true")
    private String host;
    @UriParam
    private CMConfiguration configuration;

    public CMEndpoint(final String uri, final CMComponent component) {
        super(uri, component);
        setExchangePattern(ExchangePattern.InOut);
    }

    /**
     * Provides a channel on which clients can send Messages to a CM Endpoint
     */
    @Override
    public CMProducer createProducer() throws Exception {
        final CMConfiguration config = getConfiguration();

        // This is the camel exchange processor. Allows to send messages to CM
        // API.
        // TODO: Should i provide a CMSender factory? Dynamically choose
        // CMSender implementation? Sending strategy?
        // Consider:
        // 1. single - Single Message strategy.
        // 2. Multi - CM Api supports to 1000 messages per call.
        // 3. sliding - sliding window? 1000 messages or time thresold?
        // 4. mocked - in order to fake cm responses

        // CMConstants.DEFAULT_SCHEME + host is a valid URL. It was previously checked

        String token = config.getProductToken();
        StringHelper.notEmpty(token, "productToken");

        UUID uuid = UUID.fromString(token);
        return new CMProducer(this, new CMSenderOneMessageImpl(getCMUrl(), uuid));
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    public CMConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(final CMConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public String getCMUrl() {
        return CMConstants.DEFAULT_SCHEME + host;
    }

    @Override
    public CMComponent getComponent() {
        return (CMComponent) super.getComponent();
    }

    public String getHost() {
        return host;
    }

    /**
     * SMS Provider HOST with scheme
     */
    public void setHost(final String host) {
        this.host = host;
    }
}
