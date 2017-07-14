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
package org.apache.camel.component.hipchat;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * The hipchat component supports producing and consuming messages from/to Hipchat service.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "hipchat", title = "Hipchat", syntax = "hipchat:protocol:host:port", consumerClass = HipchatConsumer.class, label = "api,cloud")
public class HipchatEndpoint extends ScheduledPollEndpoint {

    @UriParam
    private HipchatConfiguration configuration;

    public HipchatEndpoint(String uri, HipchatComponent component) {
        super(uri, component);
        configuration = new HipchatConfiguration();
    }

    public Producer createProducer() throws Exception {
        return new HipchatProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        HipchatConsumer consumer =  new HipchatConsumer(this, processor);
        //Default delay of 500 millis is too often and would result in Rate Limit error's from
        //HipChat API as per https://www.hipchat.com/docs/apiv2/rate_limiting. End user can override using
        //consumer.delay parameter
        consumer.setDelay(HipchatConsumer.DEFAULT_CONSUMER_DELAY);
        configureConsumer(consumer);
        return consumer;
    }

    public boolean isSingleton() {
        return true;
    }

    public HipchatConfiguration getConfiguration() {
        return configuration;
    }
}
