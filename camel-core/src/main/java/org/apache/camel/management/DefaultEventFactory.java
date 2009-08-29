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
package org.apache.camel.management;

import java.util.EventObject;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.CamelContextStartingEvent;
import org.apache.camel.management.event.CamelContextStoppedEvent;
import org.apache.camel.management.event.CamelContextStoppingEvent;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailedEvent;
import org.apache.camel.management.event.ExchangeFailureHandledEvent;
import org.apache.camel.management.event.RouteStartedEvent;
import org.apache.camel.management.event.RouteStoppedEvent;
import org.apache.camel.spi.EventFactory;

/**
 * @version $Revision$
 */
public class DefaultEventFactory implements EventFactory {

    public EventObject createCamelContextStartingEvent(CamelContext context) {
        return new CamelContextStartingEvent(context);
    }

    public EventObject createCamelContextStartedEvent(CamelContext context) {
        return new CamelContextStartedEvent(context);
    }

    public EventObject createCamelContextStoppingEvent(CamelContext context) {
        return new CamelContextStoppingEvent(context);
    }

    public EventObject createCamelContextStoppedEvent(CamelContext context) {
        return new CamelContextStoppedEvent(context);
    }

    public EventObject createRouteStartedEvent(Route route) {
        return new RouteStartedEvent(route);
    }

    public EventObject createRouteStoppedEvent(Route route) {
        return new RouteStoppedEvent(route);
    }

    public EventObject createExchangeCreatedEvent(Exchange exchange) {
        return new ExchangeCreatedEvent(exchange);
    }

    public EventObject createExchangeCompletedEvent(Exchange exchange) {
        return new ExchangeCompletedEvent(exchange);
    }

    public EventObject createExchangeFailedEvent(Exchange exchange) {
        return new ExchangeFailedEvent(exchange);
    }

    public EventObject createExchangeFailureHandled(Exchange exchange, Processor failureHandler, boolean deadLetterChannel) {
        return new ExchangeFailureHandledEvent(exchange, failureHandler, deadLetterChannel);
    }
}
