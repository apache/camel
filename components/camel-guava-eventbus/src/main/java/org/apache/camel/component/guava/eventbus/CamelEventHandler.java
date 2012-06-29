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
package org.apache.camel.component.guava.eventbus;

import com.google.common.eventbus.Subscribe;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class with public method marked with Guava @Subscribe annotation. Responsible for receiving events from the bus and
 * sending them to the Camel infrastructure.
 */
public class CamelEventHandler {

    private static final transient Logger LOG = LoggerFactory.getLogger(CamelEventHandler.class);
    private final GuavaEventBusEndpoint eventBusEndpoint;
    private final AsyncProcessor processor;
    private final Class<?> eventClass;

    public CamelEventHandler(GuavaEventBusEndpoint eventBusEndpoint, Processor processor, Class<?> eventClass) {
        ObjectHelper.notNull(eventBusEndpoint, "eventBusEndpoint");
        ObjectHelper.notNull(processor, "processor");

        this.eventBusEndpoint = eventBusEndpoint;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
        this.eventClass = eventClass;
    }

    /**
     * Guava callback when an event was received
     * @param event the event
     * @throws Exception is thrown if error processing the even
     */
    @Subscribe
    public void eventReceived(Object event) throws Exception {
        LOG.trace("Received event: {}");
        if (eventClass == null || eventClass.isAssignableFrom(event.getClass())) {
            final Exchange exchange = eventBusEndpoint.createExchange(event);
            LOG.debug("Processing event: {}", event);
            // use async processor to support async routing engine
            processor.process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    // noop
                }
            });
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cannot process event: {} as its class type: {} is not assignable with: {}",
                        new Object[]{event, event.getClass().getName(), eventClass.getName()});
            }
        }
    }

}
