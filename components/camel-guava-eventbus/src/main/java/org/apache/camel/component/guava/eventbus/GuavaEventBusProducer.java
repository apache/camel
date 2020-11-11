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

import com.google.common.eventbus.EventBus;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guava EventBus (http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/eventbus/EventBus.html)
 * producer forwarding messages from Camel routes to the bus.
 */
public class GuavaEventBusProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GuavaEventBusProducer.class);

    private final EventBus eventBus;

    public GuavaEventBusProducer(Endpoint endpoint, EventBus eventBus) {
        super(endpoint);
        this.eventBus = eventBus;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (body != null) {
            LOG.debug("Posting: {} to EventBus: {}", body, eventBus);
            eventBus.post(body);
        } else {
            LOG.debug("Body is null, cannot post to EventBus");
        }
    }

}
