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
package org.apache.camel.impl;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * A base class for selector-based producers.
 */
public abstract class BaseSelectorProducer extends DefaultProducer {
    protected BaseSelectorProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final Processor processor = getProcessor(exchange);
        if (processor != null) {
            processor.process(exchange);
        } else {
            onMissingProcessor(exchange);
        }
    }

    /**
     * Determine the processor to use to handle the exchange.
     *
     * @param exchange the message exchange
     * @return the processor to processes the message exchange
     * @throws Exception
     */
    protected abstract Processor getProcessor(Exchange exchange) throws Exception;

    /**
     * Invoked when no processor has been defined to process the message exchnage.
     *
     * @param exchange the message exchange
     * @throws Exception
     */
    protected abstract void onMissingProcessor(Exchange exchange) throws Exception;
}
