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
package org.apache.camel.management.event;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

/**
 * Event for <b>before</b> sending an {@link Exchange} to an {@link Endpoint}.
 * <p/>
 * This event is emitted before attempting to send the {@link Exchange} to an {@link Endpoint}.
 * There is still some internal processing occurring before the actual sending takes places, and
 * therefore its not a 100% guarantee that the sending actually happens, as there may cause an
 * internal error before.
 * <p/>
 * The {@link ExchangeSentEvent} is an event which is emitted <b>after</b> the sending is done.
 *
 * @see ExchangeSentEvent
 * @version 
 */
public class ExchangeSendingEvent extends AbstractExchangeEvent {
    private static final long serialVersionUID = -19248832613958122L;

    private final Endpoint endpoint;

    public ExchangeSendingEvent(Exchange source, Endpoint endpoint) {
        super(source);
        this.endpoint = endpoint;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public String toString() {
        return getExchange().getExchangeId() + " exchange " + getExchange() + " sending to: " + endpoint;
    }

}