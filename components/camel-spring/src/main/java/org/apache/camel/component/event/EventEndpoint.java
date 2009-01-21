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
package org.apache.camel.component.event;

import java.util.ArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;


/**
 * An <a href="http://activemq.apache.org/camel/event.html">Event Endpoint</a>
 * for working with Spring ApplicationEvents
 *
 * @version $Revision$
 */
public class EventEndpoint extends DefaultEndpoint implements ApplicationContextAware {
    private MulticastProcessor processor;
    private ApplicationContext applicationContext;

    public EventEndpoint(String endpointUri, EventComponent component) {
        super(endpointUri, component);
        this.applicationContext = component.getApplicationContext();
    }

    public EventEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public boolean isSingleton() {
        return true;
    }

    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(getApplicationContext(), "applicationContext");
        return new DefaultProducer(this) {
            public void process(Exchange exchange) throws Exception {
                ApplicationEvent event = toApplicationEvent(exchange);
                getApplicationContext().publishEvent(event);
            }
        };
    }

    public EventConsumer createConsumer(Processor processor) throws Exception {
        return new EventConsumer(this, processor);
    }

    public void onApplicationEvent(ApplicationEvent event) {
        Exchange exchange = createExchange();
        exchange.getIn().setBody(event);
        try {
            getMulticastProcessor().process(exchange);
        } catch (Exception e) {
            throw wrapRuntimeCamelException(e);
        }
    }

    protected synchronized MulticastProcessor getMulticastProcessor() {
        if (processor == null) {
            processor = new MulticastProcessor(new ArrayList<Processor>());
        }
        return processor;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    public synchronized void consumerStarted(EventConsumer consumer) {
        getMulticastProcessor().getProcessors().add(consumer.getProcessor());
    }

    public synchronized void consumerStopped(EventConsumer consumer) {
        getMulticastProcessor().getProcessors().remove(consumer.getProcessor());
    }

    protected ApplicationEvent toApplicationEvent(Exchange exchange) {
        try {
            ApplicationEvent event = exchange.getIn().getBody(ApplicationEvent.class);
            if (event != null) {
                return event;
            }
        } catch (NoTypeConversionAvailableException ex) {
            // ignore, handled below
        }
        return new CamelEvent(this, exchange);
    }
}
