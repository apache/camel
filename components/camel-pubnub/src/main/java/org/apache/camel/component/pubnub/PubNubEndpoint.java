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
package org.apache.camel.component.pubnub;

import com.pubnub.api.PNConfiguration;
import com.pubnub.api.PubNub;
import com.pubnub.api.PubNubException;
import com.pubnub.api.UserId;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Send and receive messages to/from PubNub data stream network for connected devices.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "pubnub", title = "PubNub", syntax = "pubnub:channel",
             category = { Category.CLOUD, Category.IOT, Category.MESSAGING }, headersClass = PubNubConstants.class)
public class PubNubEndpoint extends DefaultEndpoint {

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private PubNub pubnub;

    @UriParam
    private PubNubConfiguration configuration;

    public PubNubEndpoint(String uri, PubNubComponent component, PubNubConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new PubNubProducer(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new PubNubConsumer(this, processor, configuration);
    }

    public PubNubConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Reference to a Pubnub client in the registry.
     */
    public PubNub getPubnub() {
        return pubnub;
    }

    public void setPubnub(PubNub pubnub) {
        this.pubnub = pubnub;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (pubnub != null) {
            pubnub.destroy();
            pubnub = null;
        }
    }

    @Override
    protected void doStart() throws Exception {
        this.pubnub = getPubnub() != null ? getPubnub() : getInstance();
        super.doStart();
    }

    private PubNub getInstance() throws PubNubException {
        PubNub answer = null;
        PNConfiguration pnConfiguration = new PNConfiguration(new UserId(configuration.getUuid()));
        pnConfiguration.setPublishKey(configuration.getPublishKey());
        pnConfiguration.setSubscribeKey(configuration.getSubscribeKey());
        pnConfiguration.setSecretKey(configuration.getSecretKey());
        pnConfiguration.setAuthKey(configuration.getAuthKey());
        pnConfiguration.setCipherKey(configuration.getCipherKey());
        pnConfiguration.setSecure(configuration.isSecure());

        answer = new PubNub(pnConfiguration);
        return answer;
    }
}
