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
package org.apache.camel.component.jms;

import javax.jms.Message;
import javax.jms.MessageListener;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.impl.ProducerTemplateProcessor;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

/**
 * A JMS {@link MessageListener} which converts an incoming JMS message into a Camel message {@link Exchange} then
 * processing it by Camel; either using a custom {@link Processor} or if you use one of the static <code>newInstance()</code>
 * methods such as {@link #newInstance(org.apache.camel.CamelContext, String)} or
 * {@link #newInstance(org.apache.camel.CamelContext, org.apache.camel.ProducerTemplate)}
 * you can send the message exchange into a Camel endpoint for processing.
 *
 * @version $Revision$
 */
public class CamelMessageListener implements MessageListener, Processor {
    private final CamelContext camelContext;
    private final Processor processor;
    private JmsBinding binding = new JmsBinding();
    private ExchangePattern pattern = ExchangePattern.InOnly;
    
    public CamelMessageListener(CamelContext camelContext, Processor processor) {
        this.camelContext = camelContext;
        this.processor = processor;
        ObjectHelper.notNull(processor, "processor");
    }


    /**
     * Creates a new CamelMessageListener which will invoke a Camel endpoint
     *
     * @param camelContext the context to use
     * @param endpointUri  the endpoint to invoke with the JMS message {@link Exchange}
     * @return a newly created JMS MessageListener
     */
    public static CamelMessageListener newInstance(CamelContext camelContext, String endpointUri) {
        DefaultProducerTemplate producerTemplate = DefaultProducerTemplate.newInstance(camelContext, endpointUri);
        return newInstance(camelContext, producerTemplate);
    }

    /**
     * Creates a new CamelMessageListener which will invoke the default Camel endpoint of the given
     * {@link ProducerTemplate}
     *
     * @param camelContext the context to use
     * @param producerTemplate  the template used to send the exchange
     * @return a newly created JMS MessageListener
     */
    public static CamelMessageListener newInstance(CamelContext camelContext, ProducerTemplate producerTemplate) {
        return new CamelMessageListener(camelContext, new ProducerTemplateProcessor(producerTemplate));
    }
   
    /**
     * Processes the incoming JMS message
     */
    public void onMessage(Message message) {
        try {
            Exchange exchange = createExchange(message);
            process(exchange);
        } catch (Exception e) {
            throw wrapRuntimeCamelException(e);
        }
    }

    /**
     * Processes the Camel message {@link Exchange}
     */
    public void process(Exchange exchange) throws Exception {
        ObjectHelper.notNull(exchange, "exchange");
        processor.process(exchange);
    }

    // Properties
    //-------------------------------------------------------------------------

    public JmsBinding getBinding() {
        return binding;
    }

    /**
     * Sets the JMS binding used to adapt the incoming JMS message into a Camel message {@link Exchange}
     */
    public void setBinding(JmsBinding binding) {
        this.binding = binding;
    }

    public ExchangePattern getPattern() {
        return pattern;
    }

    /**
     * Sets the message exchange pattern that will be used on the Camel message {@link Exchange}
     */
    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    // Implementation methods
    //-------------------------------------------------------------------------


    /**
     * Returns a newly created Camel message {@link Exchange} from an inbound JMS message
     */
    protected Exchange createExchange(Message message) {
        return new JmsExchange(camelContext, pattern, binding, message);
    }
}
