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
package org.apache.camel.component.yammer;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * The yammer component allows you to interact with the Yammer enterprise social network.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "yammer", title = "Yammer", syntax = "yammer:function", consumerClass = YammerMessagePollingConsumer.class, label = "social")
public class YammerEndpoint extends ScheduledPollEndpoint {

    @UriParam
    private YammerConfiguration config;

    public YammerEndpoint() {
    }

    public YammerEndpoint(String uri, YammerComponent component) {
        super(uri, component);
    }

    public YammerEndpoint(String uri, YammerComponent yammerComponent, YammerConfiguration config) {
        this.setConfig(config);
    }

    public Producer createProducer() throws Exception {
        return new YammerMessageProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        switch (config.getFunctionType()) {
        case MESSAGES:
        case ALGO:
        case FOLLOWING:
        case MY_FEED:
        case PRIVATE:
        case SENT:
        case RECEIVED:
            return new YammerMessagePollingConsumer(this, processor);
        case USERS:
        case CURRENT:
            return new YammerUserPollingConsumer(this, processor);
        case RELATIONSHIPS:
            return new YammerRelationshipPollingConsumer(this, processor);
        default:
            throw new Exception(String.format("%s is not a valid Yammer function type.", config.getFunction()));
        }  

    }

    public boolean isSingleton() {
        return true;
    }

    public YammerConfiguration getConfig() {
        return config;
    }

    public void setConfig(YammerConfiguration config) {
        this.config = config;
    }

    @Override
    protected String createEndpointUri() {
        return String.format("yammer://%s?consumerKey=%s&consumerSecret=%s&accessToken=%s", config.getFunction(), config.getConsumerKey(), config.getConsumerSecret(), config.getAccessToken());
    }

}
