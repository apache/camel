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
package org.apache.camel.component.guava.eventbus;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Handler responsible for receiving events from the Guava event bus and sending them to the Camel infrastructure.
 */
public class CamelEventHandler {

    protected final Logger log = LoggerFactory.getLogger(CamelEventHandler.class);
    protected final GuavaEventBusEndpoint eventBusEndpoint;
    protected final AsyncProcessor processor;

    public CamelEventHandler(GuavaEventBusEndpoint eventBusEndpoint, Processor processor) {
        ObjectHelper.notNull(eventBusEndpoint, "eventBusEndpoint");
        ObjectHelper.notNull(processor, "processor");

        this.eventBusEndpoint = eventBusEndpoint;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    /**
     * Callback executed to propagate event from Guava listener to Camel route.
     *
     * @param event the event received by Guava EventBus.
     */
    public void doEventReceived(Object event) {
        log.trace("Received event: {}", event);
        final Exchange exchange = eventBusEndpoint.createExchange(event);
        log.debug("Processing event: {}", event);
        // use async processor to support async routing engine
        processor.process(exchange, new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                // noop
            }
        });
    }

}
