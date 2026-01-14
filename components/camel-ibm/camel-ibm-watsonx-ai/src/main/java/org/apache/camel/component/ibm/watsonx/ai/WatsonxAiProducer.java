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
package org.apache.camel.component.ibm.watsonx.ai;

import org.apache.camel.Exchange;
import org.apache.camel.component.ibm.watsonx.ai.handler.WatsonxAiOperationManager;
import org.apache.camel.component.ibm.watsonx.ai.handler.WatsonxAiOperationResponse;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer for IBM watsonx.ai component.
 */
public class WatsonxAiProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiProducer.class);

    private final WatsonxAiOperationManager operationManager;

    public WatsonxAiProducer(WatsonxAiEndpoint endpoint) {
        super(endpoint);
        this.operationManager = new WatsonxAiOperationManager(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        WatsonxAiOperations operation = determineOperation(exchange);
        LOG.debug("Processing operation: {}", operation);

        WatsonxAiOperationResponse response = operationManager.process(exchange, operation);
        setResponse(exchange, response);
    }

    private WatsonxAiOperations determineOperation(Exchange exchange) {
        // First check header
        WatsonxAiOperations operation = exchange.getIn().getHeader(
                WatsonxAiConstants.OPERATION, WatsonxAiOperations.class);

        // Fall back to configuration
        if (operation == null) {
            operation = getEndpoint().getConfiguration().getOperation();
        }

        if (operation == null) {
            throw new IllegalArgumentException(
                    "Operation must be specified either via header '" + WatsonxAiConstants.OPERATION
                                               + "' or endpoint configuration 'operation'");
        }

        return operation;
    }

    private void setResponse(Exchange exchange, WatsonxAiOperationResponse response) {
        if (response != null) {
            exchange.getMessage().setBody(response.getBody());
            if (response.getHeaders() != null) {
                response.getHeaders().forEach((key, value) -> exchange.getMessage().setHeader(key, value));
            }
        }
    }

    @Override
    public WatsonxAiEndpoint getEndpoint() {
        return (WatsonxAiEndpoint) super.getEndpoint();
    }
}
