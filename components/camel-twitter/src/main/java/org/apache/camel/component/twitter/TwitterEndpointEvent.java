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
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.component.twitter.consumer.Twitter4JConsumer;
import org.apache.camel.component.twitter.consumer.TwitterConsumerEvent;
import org.apache.camel.component.twitter.data.EndpointType;

import twitter4j.Twitter;

public class TwitterEndpointEvent extends DirectEndpoint implements TwitterEndpoint {

    private Twitter twitter;
    private TwitterConfiguration properties;

    public TwitterEndpointEvent(String uri, TwitterComponent component, TwitterConfiguration properties) {
        super(uri, component);
        this.properties = properties;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Twitter4JConsumer twitter4jConsumer = Twitter4JFactory.getConsumer(this, getEndpointUri());
        return new TwitterConsumerEvent(this, processor, twitter4jConsumer);
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer not supported");
    }

    @Override
    protected void doStart() {
        twitter = properties.getTwitterInstance();
    }

    public Twitter getTwitter() {
        return twitter;
    }

    public TwitterConfiguration getProperties() {
        return properties;
    }

    @Override
    public EndpointType getEndpointType() {
        return EndpointType.EVENT;
    }
}
