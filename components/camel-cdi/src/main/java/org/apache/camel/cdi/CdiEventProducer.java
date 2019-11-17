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
package org.apache.camel.cdi;

import javax.enterprise.event.Event;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CdiEventProducer<T> extends DefaultProducer {

    private final Logger logger = LoggerFactory.getLogger(CdiEventProducer.class);

    private final Event<T> event;

    CdiEventProducer(CdiEventEndpoint<T> endpoint, Event<T> event) {
        super(endpoint);
        this.event = event;
    }

    @Override
    public void process(Exchange exchange) {
        logger.debug("Firing CDI event [{}] with {}", event, this);
        // TODO: leverage Camel type converter mechanism based on the endpoint type
        // The EventMetadata injection point will be that of the event which is not very useful
        // for the end user. Using BeanManager.fire would be a way to hide that internal though
        // it will be necessary to check whether the exchange event type is assignable to the
        // endpoint event type.
        event.fire((T) exchange.getIn().getBody());
    }
}
