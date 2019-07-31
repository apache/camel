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
package org.apache.camel.component.atmos.integration.consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.atmos.AtmosConfiguration;
import org.apache.camel.component.atmos.AtmosEndpoint;
import org.apache.camel.component.atmos.core.AtmosAPIFacade;
import org.apache.camel.component.atmos.dto.AtmosResult;

public class AtmosScheduledPollGetConsumer extends AtmosScheduledPollConsumer {

    public AtmosScheduledPollGetConsumer(AtmosEndpoint endpoint, Processor processor, AtmosConfiguration configuration) {
        super(endpoint, processor, configuration);
    }

    /**
     * Poll from an atmos remote path and put the result in the message exchange
     * @return number of messages polled
     * @throws Exception
     */
    @Override
    protected int poll() throws Exception {
        Exchange exchange = endpoint.createExchange();
        AtmosResult result = AtmosAPIFacade.getInstance(configuration.getClient())
                .get(configuration.getRemotePath());
        result.populateExchange(exchange);

        try {
            // send message to next processor in the route
            getProcessor().process(exchange);
            return 1; // number of messages polled
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }
}
