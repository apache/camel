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

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class PubNubConfiguration {

    @UriPath()
    @Metadata(required = true)
    private String channel;

    @UriParam(label = "security", secret = true)
    private String publishKey;

    @UriParam(label = "security", secret = true)
    private String subscribeKey;

    @UriParam(label = "security", secret = true)
    private String secretKey;

    @UriParam(label = "security", secret = true)
    private String authKey;

    @UriParam(label = "security", secret = true)
    private String cipherKey;

    @UriParam(label = "security", defaultValue = "true")
    private boolean secure = true;

    @UriParam()
    private String uuid;

    @UriParam(label = "producer", enums = "HERENOW,WHERENOW,GETSTATE,SETSTATE,GETHISTORY,PUBLISH,FIRE")
    private String operation;

    @UriParam(label = "consumer", defaultValue = "false")
    private boolean withPresence;

    /**
     * The publish key obtained from your PubNub account. Required when publishing messages.
     */
    public String getPublishKey() {
        return this.publishKey;
    }

    public void setPublishKey(String publishKey) {
        this.publishKey = publishKey;
    }

    /**
     * The subscribe key obtained from your PubNub account. Required when subscribing to channels or listening for presence events
     */
    public String getSubscribeKey() {
        return this.subscribeKey;
    }

    public void setSubscribeKey(String subscribeKey) {
        this.subscribeKey = subscribeKey;
    }

    /**
     * The secret key used for message signing.
     */
    public String getSecretKey() {
        return this.secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * If Access Manager is utilized, client will use this authKey in all restricted requests.
     */
    public String getAuthKey() {
        return authKey;
    }

    public void setAuthKey(String authKey) {
        this.authKey = authKey;
    }

    /**
     * If cipher is passed, all communications to/from PubNub will be encrypted.
     */
    public String getCipherKey() {
        return cipherKey;
    }

    public void setCipherKey(String cipherKey) {
        this.cipherKey = cipherKey;
    }

    /**
     * Use SSL for secure transmission.
     */
    public boolean isSecure() {
        return this.secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    /**
     * The channel used for subscribing/publishing events
     */
    public String getChannel() {
        return this.channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    /**
     * UUID to be used as a device identifier, a default UUID is generated if not passed.
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return this.uuid;
    }

    /**
     * The operation to perform.
     * <ul>
     * <li>PUBLISH: Default. Send a message to all subscribers of a channel.</li>
     * <li>FIRE: allows the client to send a message to BLOCKS Event Handlers. These messages will go directly to any Event Handlers registered on the channel.</li>
     * <li>HERENOW: Obtain information about the current state of a channel including a list of unique user-ids currently subscribed to the channel and the total occupancy count.</li>
     * <li>WHERENOW: Obtain information about the current list of channels to which a uuid is subscribed to.</li>
     * <li>GETSTATE: Used to get key/value pairs specific to a subscriber uuid. State information is supplied as a JSON object of key/value pairs</li>
     * <li>SETSTATE: Used to set key/value pairs specific to a subscriber uuid</li>
     * <li>GETHISTORY: Fetches historical messages of a channel.</li>
     * </ul>
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOperation() {
        return this.operation;
    }

    /**
     * Also subscribe to related presence information
     */
    public void setWithPresence(boolean withPresence) {
        this.withPresence = withPresence;
    }

    public boolean isWithPresence() {
        return withPresence;
    }

}
