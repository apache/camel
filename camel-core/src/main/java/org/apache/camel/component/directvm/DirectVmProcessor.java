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
package org.apache.camel.component.directvm;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.util.ExchangeHelper;

/**
*
*/
public final class DirectVmProcessor extends DelegateProcessor {

    private final DirectVmEndpoint endpoint;

    public DirectVmProcessor(Processor processor, DirectVmEndpoint endpoint) {
        super(processor);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // need to use a copy of the incoming exchange, so we route using this camel context
        Exchange copy = prepareExchange(exchange);
        try {
            getProcessor().process(copy);
        } finally {
            // make sure to copy results back
            ExchangeHelper.copyResults(exchange, copy);
        }
    }

    /**
     * Strategy to prepare exchange for being processed by this consumer
     *
     * @param exchange the exchange
     * @return the exchange to process by this consumer.
     */
    protected Exchange prepareExchange(Exchange exchange) {
        // send a new copied exchange with new camel context (do not handover completions)
        Exchange newExchange = ExchangeHelper.copyExchangeAndSetCamelContext(exchange, endpoint.getCamelContext(), false);
        // set the from endpoint
        newExchange.setFromEndpoint(endpoint);
        return newExchange;
    }

    @Override
    public String toString() {
        return "DirectVm[" + processor + "]";
    }
}
