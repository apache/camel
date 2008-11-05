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
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.integration.core.MessageChannel;

/**
 * Defines the <a href="http://activemq.apache.org/camel/springIntergration.html">Spring Intergration Endpoint</a>
 *
 * @version $Revision$
 */
public class SpringIntegrationEndpoint extends ScheduledPollEndpoint<SpringIntegrationExchange> {
    private static final Log LOG = LogFactory.getLog(SpringIntegrationEndpoint.class);
    private String inputChannel;
    private String outputChannel;
    private String defaultChannel;
    private MessageChannel messageChannel;
    private boolean inOut;

    public SpringIntegrationEndpoint(String uri, String channel, SpringIntegrationComponent component) {
        super(uri, component);
        defaultChannel = channel;
    }

    public SpringIntegrationEndpoint(String uri, MessageChannel channel, CamelContext context) {
        super(uri, context);
        messageChannel = channel;
    }

    public SpringIntegrationEndpoint(String endpointUri, MessageChannel messageChannel) {
        super(endpointUri);
        this.messageChannel = messageChannel;
    }

    public Producer<SpringIntegrationExchange> createProducer() throws Exception {
        return new SpringIntegrationProducer(this);
    }

    public Consumer<SpringIntegrationExchange> createConsumer(Processor processor) throws Exception {
        return new SpringIntegrationConsumer(this, processor);
    }

    public SpringIntegrationExchange createExchange() {
        return createExchange(getExchangePattern());
    }

    public SpringIntegrationExchange createExchange(ExchangePattern pattern) {
        return new SpringIntegrationExchange(getCamelContext(), pattern);
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
