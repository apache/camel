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
package org.apache.camel.component.torchserve.producer;

import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.torchserve.TorchServeConfiguration;
import org.apache.camel.component.torchserve.TorchServeConstants;
import org.apache.camel.component.torchserve.TorchServeEndpoint;
import org.apache.camel.component.torchserve.client.Metrics;
import org.apache.camel.component.torchserve.client.model.ApiException;

public class MetricsProducer extends TorchServeProducer {

    private final Metrics metrics;
    private final String operation;

    public MetricsProducer(TorchServeEndpoint endpoint) {
        super(endpoint);
        this.metrics = endpoint.getClient().metrics();
        this.operation = endpoint.getOperation();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if ("metrics".equals(this.operation)) {
            metrics(exchange);
        } else {
            throw new IllegalArgumentException("Unsupported operation: " + this.operation);
        }
    }

    private void metrics(Exchange exchange) throws ApiException {
        Message message = exchange.getMessage();
        TorchServeConfiguration configuration = getEndpoint().getConfiguration();
        String metricsName = Optional
                .ofNullable(message.getHeader(TorchServeConstants.METRICS_NAME, String.class))
                .orElse(configuration.getMetricsName());
        String response = metricsName == null ? this.metrics.metrics() : this.metrics.metrics(metricsName);
        message.setBody(response);
    }
}
