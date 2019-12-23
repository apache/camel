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
package org.apache.camel.component.sip;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * To send and receive messages using the SIP protocol (used in telco and mobile).
 */
@UriEndpoint(firstVersion = "2.5.0", scheme = "sip,sips", title = "SIP", syntax = "sip:uri", label = "mobile")
public class SipEndpoint extends DefaultEndpoint {
    @UriParam
    private SipConfiguration configuration;

    public SipEndpoint(String endpointUri, Component component, SipConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (configuration.isPresenceAgent()) {
            SipPresenceAgent answer = new SipPresenceAgent(this, processor, configuration);
            configureConsumer(answer);
            return answer;
        } else {
            SipSubscriber answer = new SipSubscriber(this, processor, configuration);
            configureConsumer(answer);
            return answer;
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SipPublisher(this, configuration);
    }
    
    public SipConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(SipConfiguration configuration) {
        this.configuration = configuration;
    }

}
