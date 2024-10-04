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
package org.apache.camel.component.torchserve;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.torchserve.client.TorchServeClient;
import org.apache.camel.component.torchserve.producer.InferenceProducer;
import org.apache.camel.component.torchserve.producer.ManagementProducer;
import org.apache.camel.component.torchserve.producer.MetricsProducer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "4.9.0", scheme = "torchserve", title = "TorchServe",
             syntax = "torchserve:api/operation", producerOnly = true,
             category = { Category.AI }, headersClass = TorchServeConstants.class)
public class TorchServeEndpoint extends DefaultEndpoint {

    @UriPath(enums = "inference,management,metrics", description = "The TorchServe API")
    @Metadata(required = true)
    private final String api;

    @UriPath(enums = "ping,predictions,explanations,register,scale-worker,describe,unregister,list,set-default,metrics",
             description = "The API operation")
    @Metadata(required = true)
    private final String operation;

    @UriParam
    private TorchServeConfiguration configuration;

    private TorchServeClient client;

    public TorchServeEndpoint(String uri, TorchServeComponent component, String path,
                              TorchServeConfiguration configuration) {
        super(uri, component);
        String[] parts = extractPath(path);
        this.api = parts[0];
        this.operation = parts[1];
        this.configuration = configuration;
    }

    private static String[] extractPath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        String[] parts = path.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("path must be <api>/<operation>: " + path);
        }
        return parts;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        client = createClient();
    }

    private TorchServeClient createClient() {
        TorchServeClient.Builder builder = TorchServeClient.builder();
        // Inference
        if (configuration.getInferenceKey() != null) {
            builder.inferenceKey(configuration.getInferenceKey());
        }
        if (configuration.getInferenceAddress() != null) {
            builder.inferenceAddress(configuration.getInferenceAddress());
        } else {
            builder.inferencePort(configuration.getInferencePort());
        }
        // Management
        if (configuration.getManagementKey() != null) {
            builder.managementKey(configuration.getManagementKey());
        }
        if (configuration.getManagementAddress() != null) {
            builder.managementAddress(configuration.getManagementAddress());
        } else {
            builder.managementPort(configuration.getManagementPort());
        }
        // Metrics
        if (configuration.getMetricsAddress() != null) {
            builder.metricsAddress(configuration.getMetricsAddress());
        } else {
            builder.metricsPort(configuration.getMetricsPort());
        }
        return builder.build();
    }

    @Override
    public Producer createProducer() {
        return switch (api) {
            case "inference" -> new InferenceProducer(this);
            case "management" -> new ManagementProducer(this);
            case "metrics" -> new MetricsProducer(this);
            default -> throw new IllegalArgumentException("Unknown API: " + api);
        };
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    public String getApi() {
        return api;
    }

    public String getOperation() {
        return operation;
    }

    public TorchServeConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(TorchServeConfiguration configuration) {
        this.configuration = configuration;
    }

    public TorchServeClient getClient() {
        return client;
    }
}
