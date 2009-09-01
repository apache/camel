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
package org.apache.camel.spi;

import java.util.EventObject;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;

/**
 * Factory to create {@link java.util.EventObject} events} that are emitted when such an event occur.
 * <p/>
 * For example when an {@link Exchange} is being created and then later when its done.
 *
 * @version $Revision$
 */
public interface EventFactory {

    EventObject createCamelContextStartingEvent(CamelContext context);

    EventObject createCamelContextStartedEvent(CamelContext context);

    EventObject createCamelContextStoppingEvent(CamelContext context);

    EventObject createCamelContextStoppedEvent(CamelContext context);

    EventObject createRouteStartedEvent(Route route);

    EventObject createRouteStoppedEvent(Route route);

    EventObject createExchangeCreatedEvent(Exchange exchange);

    EventObject createExchangeCompletedEvent(Exchange exchange);

    EventObject createExchangeFailedEvent(Exchange exchange);

    EventObject createExchangeFailureHandledEvent(Exchange exchange, Processor failureHandler, boolean deadLetterChannel);

}
