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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * @version 
 */
public class ExchangeFailureHandledEvent extends AbstractExchangeEvent {
    private static final long serialVersionUID = -7554809462006009547L;

    private final Processor failureHandler;
    private final boolean deadLetterChannel;
    private final boolean handled;

    public ExchangeFailureHandledEvent(Exchange source, Processor failureHandler, boolean deadLetterChannel) {
        super(source);
        this.failureHandler = failureHandler;
        this.deadLetterChannel = deadLetterChannel;
        this.handled = source.getProperty(Exchange.ERRORHANDLER_HANDLED, false, Boolean.class);
    }

    public Processor getFailureHandler() {
        return failureHandler;
    }

    public boolean isDeadLetterChannel() {
        return deadLetterChannel;
    }

    public boolean isHandled() {
        return handled;
    }

    @Override
    public String toString() {
        if (isDeadLetterChannel()) {
            return getExchange().getExchangeId() + " exchange failed: " + getExchange() + " but was handled by dead letter channel: " + failureHandler;
        } else {
            return getExchange().getExchangeId() + " exchange failed: " + getExchange() + " but was processed by: " + failureHandler;
        }
    }
}
