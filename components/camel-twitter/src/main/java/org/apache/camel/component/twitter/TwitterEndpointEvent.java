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
package org.apache.camel.component.twitter;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.twitter.consumer.AbstractTwitterConsumerHandler;
import org.apache.camel.component.twitter.consumer.TwitterConsumerEvent;
import org.apache.camel.component.twitter.data.EndpointType;
import org.apache.camel.impl.DefaultEndpoint;

@Deprecated
public class TwitterEndpointEvent extends DefaultEndpoint implements CommonPropertiesTwitterEndpoint {
    private final String kind;

    // only TwitterEndpointPolling is annotated
    private TwitterConfiguration properties;

    private String user;

    private String keywords;

    public TwitterEndpointEvent(String uri, String remaining, TwitterComponent component, TwitterConfiguration properties) {
        super(uri, component);
        this.kind = remaining;
        this.properties = properties;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AbstractTwitterConsumerHandler twitter4jConsumer = TwitterHelper.createConsumer(this, getEndpointUri(), kind);
        return new TwitterConsumerEvent(this, processor, twitter4jConsumer);
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer not supported");
    }

    public TwitterConfiguration getProperties() {
        return properties;
    }

    public void setProperties(TwitterConfiguration properties) {
        this.properties = properties;
    }

    @Override
    public EndpointType getEndpointType() {
        return EndpointType.EVENT;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (properties.getTwitterStream() != null) {
            properties.getTwitterStream().shutdown();
        }
    }

    @Override
    public String getUser() {
        return user;
    }

    @Override
    public void setUser(String user) {
        this.user = user;
    }

    @Override
    public String getKeywords() {
        return keywords;
    }

    @Override
    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

}
