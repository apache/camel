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

public class ExchangeFailedEvent extends AbstractExchangeEvent implements CamelEvent.ExchangeFailedEvent {
    private static final long serialVersionUID = -8484326904627268101L;

    public ExchangeFailedEvent(Exchange source) {
        super(source);
    }

    @Override
    public Throwable getCause() {
        return getExchange().getException();
    }

    @Override
    public final String toString() {
        Exception cause = getExchange().getException();
        if (cause != null) {
            return getExchange().getExchangeId() + " exchange failed due to: " + cause;
        } else {
            return getExchange().getExchangeId() + " exchange failed";
        }
    }

}
