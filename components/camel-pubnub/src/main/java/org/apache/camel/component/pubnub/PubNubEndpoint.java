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
package org.apache.camel.component.pubnub;

import com.pubnub.api.Pubnub;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

@UriEndpoint(scheme = "pubnub", title = "PubNub", syntax = "pubnub:endpointType:channel", consumerClass = PubNubConsumer.class, label = "cloud,iot,messaging")
public class PubNubEndpoint extends DefaultEndpoint {

    @UriParam
    private Pubnub pubnub;

    @UriPath(enums = "pubsub,presence")
    @Metadata(required = "true")
    private PubNubEndpointType endpointType = PubNubEndpointType.pubsub;

    @UriPath()
    @Metadata(required = "true")
    private String channel;

    @UriParam()
    private String publisherKey;

    @UriParam()
    private String subscriberKey;

    @UriParam()
    private String secretKey;

    @UriParam(defaultValue = "true")
    private boolean ssl = true;

    @UriParam()
    private String uuid;

    @UriParam(label = "producer", enums = "HERE_NOW, WHERE_NOW, GET_STATE, SET_STATE, GET_HISTORY, PUBLISH")
    private String operation;

    public PubNubEndpoint(String uri, PubNubComponent component) {
        super(uri, component);
    }

    

    @Override
    public Producer createProducer() throws Exception {
        return new PubNubProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new PubNubConsumer(this, processor);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    /**
     * The type endpoint type. Either pubsub or presence
     */

    public PubNubEndpointType getEndpointType() {
        return endpointType;
    }

    public void setEndpointType(PubNubEndpointType endpointType) {
        this.endpointType = endpointType;
    }

    /**
     * The pubnub publish key obtained from your pubnub account. Required when
     * publishing messages.
     */
    public String getPublisherKey() {
        return publisherKey;
    }

    public void setPublisherKey(String publisherKey) {
        this.publisherKey = publisherKey;
    }

    /**
     * The pubnub subscribe key obtained from your pubnub account. Required when
     * subscribing to channels or listening for presence events
     */
    public String getSubscriberKey() {
        return subscriberKey;
    }

    public void setSubscriberKey(String subscriberKey) {
        this.subscriberKey = subscriberKey;
    }

    /**
     * The pubnub secret key used for message signing.
     */
    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Use ssl
     */
    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    /**
     * The channel used for subscribing/publishing events
     */
    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    /**
     * The uuid identifying the connection. Will be auto assigned if not set.
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

    /**
     * The operation to perform.
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * Reference to a Pubnub client in the registry.
     */

    public Pubnub getPubnub() {
        return pubnub;
    }

    public void setPubnub(Pubnub pubnub) {
        this.pubnub = pubnub;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (pubnub != null) {
            pubnub.shutdown();
            pubnub = null;
        }
    }

    @Override
    protected void doStart() throws Exception {
        this.pubnub = getPubnub() != null ? getPubnub() : getInstance();
        super.doStart();
    }

    private Pubnub getInstance() {
        Pubnub answer = null;
        if (ObjectHelper.isNotEmpty(getSecretKey())) {
            answer = new Pubnub(getPublisherKey(), getSubscriberKey(), getSecretKey(), isSsl());
        } else {
            answer = new Pubnub(getPublisherKey(), getSubscriberKey(), isSsl());
        }
        if (ObjectHelper.isNotEmpty(getUuid())) {
            answer.setUUID(getUuid());
        } else {
            String autoUUID = answer.uuid();
            setUuid(autoUUID);
            answer.setUUID(autoUUID);
        }
        return answer;
    }
}
