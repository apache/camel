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

import com.google.common.eventbus.EventBus;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

public class GuavaEventBusConsumer extends DefaultConsumer {

    private final EventBus eventBus;
    private final CamelEventHandler eventHandler;

    public GuavaEventBusConsumer(GuavaEventBusEndpoint endpoint, Processor processor, EventBus eventBus, Class<?> eventClass) {
        super(endpoint, processor);
        this.eventBus = eventBus;
        this.eventHandler = new CamelEventHandler(endpoint, processor, eventClass);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.debug("Registering event handler: {} to EventBus: {}", eventHandler, eventBus);
        eventBus.register(eventHandler);
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("Unregistering event handler: {} from EventBus: {}", eventHandler, eventBus);
        eventBus.unregister(eventHandler);
        super.doStop();
    }
}

