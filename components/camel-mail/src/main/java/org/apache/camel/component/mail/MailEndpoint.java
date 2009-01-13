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
package org.apache.camel.component.mail;

import javax.mail.Message;

import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Endpoint for Camel Mail.
 *
 * @version $Revision:520964 $
 */
public class MailEndpoint extends ScheduledPollEndpoint<MailExchange> {
    private MailBinding binding;
    private MailConfiguration configuration;

    public MailEndpoint(String uri, MailComponent component, MailConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
        this.binding = new MailBinding(component.getHeaderFilterStrategy());
    }

    public MailEndpoint(String endpointUri, MailConfiguration configuration) {
        super(endpointUri);
        this.configuration = configuration;
    }

    public MailEndpoint(String endpointUri) {
        this(endpointUri, new MailConfiguration());
    }

    public Producer<MailExchange> createProducer() throws Exception {
        JavaMailSender sender = configuration.createJavaMailSender();
        return createProducer(sender);
    }

    /**
     * Creates a producer using the given sender
     */
    public Producer<MailExchange> createProducer(JavaMailSender sender) throws Exception {
        return new MailProducer(this, sender);
    }

    public Consumer<MailExchange> createConsumer(Processor processor) throws Exception {
        if (configuration.getProtocol().startsWith("smtp")) {
            throw new IllegalArgumentException("Protocol " + configuration.getProtocol()
                + " can not be used for a MailConsumer. Please use another protocol such as pop3 or imap.");
        }

        JavaMailSenderImpl sender = configuration.createJavaMailSender();
        return createConsumer(processor, sender);
    }

    /**
     * Creates a consumer using the given processor and sender
     */
    public Consumer<MailExchange> createConsumer(Processor processor, JavaMailSenderImpl sender) throws Exception {
        MailConsumer answer = new MailConsumer(this, processor, sender);

        // ScheduledPollConsumer default delay is 500 millis and that is too often for polling a mailbox,
        // so we override with a new default value. End user can override this value by providing a consumer.delay parameter
        answer.setDelay(MailConsumer.DEFAULT_CONSUMER_DELAY);
        configureConsumer(answer);

        return answer;
    }


    @Override
    public MailExchange createExchange(ExchangePattern pattern) {
        return new MailExchange(getCamelContext(), pattern, getBinding());
    }

    public MailExchange createExchange(Message message) {
        return new MailExchange(getCamelContext(), getExchangePattern(), getBinding(), message);
    }

    // Properties
    // -------------------------------------------------------------------------
    public MailBinding getBinding() {
        if (binding == null) {
            binding = new MailBinding();
        }
        return binding;
    }

    /**
     * Sets the binding used to convert from a Camel message to and from a Mail
     * message
     *
     * @param binding the binding to use
     */
    public void setBinding(MailBinding binding) {
        this.binding = binding;
    }

    public boolean isSingleton() {
        return false;
    }

    public MailConfiguration getConfiguration() {
        return configuration;
    }
}
