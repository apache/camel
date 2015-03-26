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
package org.apache.camel.component.spring.integration;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.integration.MessageChannel;

/**
 * Defines the <a href="http://camel.apache.org/springIntergration.html">Spring Integration Endpoint</a>
 *
 * @version 
 */
@UriEndpoint(scheme = "spring-integration", title = "Spring Integration", syntax = "spring-integration:defaultChannel",
        consumerClass = SpringIntegrationConsumer.class, label = "spring,eventbus")
public class SpringIntegrationEndpoint extends DefaultEndpoint {
    @UriPath @Metadata(required = "true")
    private String defaultChannel;
    @UriParam
    private String inputChannel;
    @UriParam
    private String outputChannel;
    private MessageChannel messageChannel;
    @UriParam
    private boolean inOut;

    public SpringIntegrationEndpoint(String uri, String channel, SpringIntegrationComponent component) {
        super(uri, component);
        this.defaultChannel = channel;
    }

    @Deprecated
    public SpringIntegrationEndpoint(String uri, MessageChannel channel, CamelContext context) {
        super(uri, context);
        this.messageChannel = channel;
    }

    @Deprecated
    public SpringIntegrationEndpoint(String endpointUri, MessageChannel messageChannel) {
        super(endpointUri);
        this.messageChannel = messageChannel;
    }

    public Producer createProducer() throws Exception {
        return new SpringIntegrationProducer((SpringCamelContext) getCamelContext(), this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        SpringIntegrationConsumer answer = new SpringIntegrationConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public void setInputChannel(String input) {
        inputChannel = input;
    }

    public String getInputChannel() {
        return inputChannel;
    }

    public void setOutputChannel(String output) {
        outputChannel = output;
    }

    public String getOutputChannel() {
        return outputChannel;
    }

    public String getDefaultChannel() {
        return defaultChannel;
    }

    @Deprecated
    public MessageChannel getMessageChannel() {
        return messageChannel;
    }

    public boolean isSingleton() {
        return false;
    }

    public void setInOut(boolean inOut) {
        this.inOut = inOut;
    }

    public boolean isInOut() {
        return this.inOut;
    }

}
