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

import com.pubnub.api.PubNubException;
import com.pubnub.api.UserId;
import com.pubnub.api.java.PubNub;
import com.pubnub.api.java.v2.PNConfiguration;
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

    // whether the endpoint created the client (a client from the registry may be shared, and is not ours to destroy)
    private boolean createdClient;

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
        if (pubnub != null && createdClient) {
            pubnub.destroy();
            pubnub = null;
            createdClient = false;
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (pubnub == null) {
            pubnub = getInstance();
            createdClient = true;
        }
        super.doStart();
    }

    private PubNub getInstance() throws PubNubException {
        PNConfiguration.Builder builder
                = PNConfiguration.builder(new UserId(configuration.getUuid()), configuration.getSubscribeKey())
                        .secure(configuration.isSecure());
        // the builder does not accept null for the keys that are not configured, its defaults apply then
        if (configuration.getPublishKey() != null) {
            builder.publishKey(configuration.getPublishKey());
        }
        if (configuration.getSecretKey() != null) {
            builder.secretKey(configuration.getSecretKey());
        }
        if (configuration.getAuthKey() != null) {
            builder.authKey(configuration.getAuthKey());
        }
        return PubNub.create(builder.build());
    }
}
