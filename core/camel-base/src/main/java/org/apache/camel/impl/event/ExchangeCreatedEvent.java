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
package org.apache.camel.impl.event;

import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent;

/**
 * Event after an {@link Exchange} has been created.
 * <p/>
 * <b>Notice:</b> This event may be emitted after an {@link ExchangeSendingEvent}, and
 * therefore its not guaranteed this event is the first event being send for a given {@link Exchange}
 * lifecycle.
 */
public class ExchangeCreatedEvent extends AbstractExchangeEvent implements CamelEvent.ExchangeCreatedEvent {
    private static final long serialVersionUID = -19248832613958243L;

    public ExchangeCreatedEvent(Exchange source) {
        super(source);
    }

    @Override
    public String toString() {
        return getExchange().getExchangeId() + " exchange created: " + getExchange();
    }
}
