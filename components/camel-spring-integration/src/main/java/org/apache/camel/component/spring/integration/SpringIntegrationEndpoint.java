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
import org.springframework.messaging.MessageChannel;

/**
 * Bridges Camel with Spring Integration.
 */
@UriEndpoint(firstVersion = "1.4.0", scheme = "spring-integration", title = "Spring Integration", syntax = "spring-integration:defaultChannel",
        consumerClass = SpringIntegrationConsumer.class, label = "spring,eventbus")
public class SpringIntegrationEndpoint extends DefaultEndpoint {
    private MessageChannel messageChannel;
    @UriPath @Metadata(required = "true")
    private String defaultChannel;
    @UriParam(label = "consumer")
    private String inputChannel;
    @UriParam(label = "producer")
    private String outputChannel;
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

    /**
     * The Spring integration input channel name that this endpoint wants to consume from Spring integration.
     */
    public void setInputChannel(String input) {
        inputChannel = input;
    }

    public String getInputChannel() {
        return inputChannel;
    }

    /**
     * The Spring integration output channel name that is used to send messages to Spring integration.
     */
    public void setOutputChannel(String output) {
        outputChannel = output;
    }

    public String getOutputChannel() {
        return outputChannel;
    }

    /**
     * The default channel name which is used by the Spring Integration Spring context.
     * It will equal to the inputChannel name for the Spring Integration consumer and the outputChannel name for the Spring Integration provider.
     */
    public void setDefaultChannel(String defaultChannel) {
        this.defaultChannel = defaultChannel;
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

    /**
     * The exchange pattern that the Spring integration endpoint should use.
     * If inOut=true then a reply channel is expected, either from the Spring Integration Message header or configured on the endpoint.
     */
    public void setInOut(boolean inOut) {
        this.inOut = inOut;
    }

    public boolean isInOut() {
        return this.inOut;
    }

}
