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

import java.util.EventObject;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * @version $Revision$
 */
public class ExchangeFailureHandledEvent extends EventObject {

    private final Exchange exchange;
    private final Processor failureHandler;
    private final boolean deadLetterChannel;
    private final boolean handled;

    public ExchangeFailureHandledEvent(Exchange source, Processor failureHandler, boolean deadLetterChannel) {
        super(source);
        this.exchange = source;
        this.failureHandler = failureHandler;
        this.deadLetterChannel = deadLetterChannel;
        this.handled = exchange.getProperty(Exchange.ERRORHANDLER_HANDLED, Boolean.class);
    }

    public Exchange getExchange() {
        return exchange;
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
            return "Exchange: " + exchange + (handled ? " handled" : "") + " by dead letter channel: " + failureHandler;
        } else {
            return "Exchange: " + exchange + (handled ? " handled" : "") + " by: " + failureHandler;
        }
    }

}
