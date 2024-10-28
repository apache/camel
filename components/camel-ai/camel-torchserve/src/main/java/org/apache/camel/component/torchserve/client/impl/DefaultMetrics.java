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
package org.apache.camel.component.torchserve.client.impl;

import org.apache.camel.component.torchserve.client.Metrics;
import org.apache.camel.component.torchserve.client.metrics.api.DefaultApi;
import org.apache.camel.component.torchserve.client.metrics.invoker.ApiClient;
import org.apache.camel.component.torchserve.client.model.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMetrics implements Metrics {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMetrics.class);

    private final DefaultApi api;

    public DefaultMetrics() {
        this(8082);
    }

    public DefaultMetrics(int port) {
        this("http://localhost:" + port);
    }

    public DefaultMetrics(String address) {
        ApiClient client = new ApiClient().setBasePath(address);
        this.api = new DefaultApi(client);
        LOG.debug("Metrics API address: {}", address);
    }

    @Override
    public String metrics() throws ApiException {
        return metrics(null);
    }

    @Override
    public String metrics(String name) throws ApiException {
        try {
            return api.metrics(name);
        } catch (org.apache.camel.component.torchserve.client.metrics.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

}
