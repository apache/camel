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
package org.apache.camel.component.event;

import org.apache.camel.Exchange;
import org.springframework.context.ApplicationEvent;

/**
 * Represents a Spring {@link ApplicationEvent} which contains a Camel {@link Exchange}
 */
public class CamelEvent extends ApplicationEvent {
    private static final long serialVersionUID = 1L;

    private final Exchange exchange;

    public CamelEvent(EventEndpoint source, Exchange exchange) {
        super(source);
        this.exchange = exchange;
    }

    @Override
    public EventEndpoint getSource() {
        return (EventEndpoint) super.getSource();
    }

    /**
     * Returns the message exchange
     *
     * @return the camel message exchange
     */
    public Exchange getExchange() {
        return exchange;
    }
}
