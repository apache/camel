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

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class PubNubConfiguration {
    @UriParam
    private Pubnub pubnub;

    @UriPath(enums = "pubsub,presence")
    @Metadata(required = "true", defaultValue = "pubsub")
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

    public PubNubEndpointType getEndpointType() {
        return endpointType;
    }

    /**
     * The publish key obtained from your PubNub account. Required when publishing messages.
     */
    public String getPublisherKey() {
        return publisherKey;
    }

    public void setPublisherKey(String publisherKey) {
        this.publisherKey = publisherKey;
    }

    /**
     * The subscribe key obtained from your PubNub account. Required when subscribing to channels or listening for presence events
     */
    public String getSubscriberKey() {
        return subscriberKey;
    }

    public void setSubscriberKey(String subscriberKey) {
        this.subscriberKey = subscriberKey;
    }

    /**
     * The secret key used for message signing.
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

    public Pubnub getPubnub() {
        return pubnub;
    }

    public void setPubnub(Pubnub pubnub) {
        this.pubnub = pubnub;
    }

}
