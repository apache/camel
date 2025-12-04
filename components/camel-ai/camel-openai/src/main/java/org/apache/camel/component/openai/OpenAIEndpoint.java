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

package org.apache.camel.component.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI endpoint for chat completion.
 */
@UriEndpoint(
        firstVersion = "4.17.0",
        scheme = "openai",
        title = "OpenAI",
        syntax = "openai:operation",
        category = {Category.AI},
        producerOnly = true)
public class OpenAIEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true, description = "The operation to perform (currently only chat-completion is supported)")
    private String operation;

    @UriParam
    private OpenAIConfiguration configuration;

    private OpenAIClient client;

    public OpenAIEndpoint(String uri, OpenAIComponent component, OpenAIConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        if (!"chat-completion".equals(operation)) {
            throw new IllegalArgumentException("Only 'chat-completion' operation is supported");
        }
        return new OpenAIProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for OpenAI component");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        client = createClient();
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client = null;
        }
        super.doStop();
    }

    protected OpenAIClient createClient() {
        String apiKey = resolveApiKey();

        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder();

        if (ObjectHelper.isNotEmpty(apiKey)) {
            builder.apiKey(apiKey);
        }

        builder.baseUrl(ObjectHelper.notNullOrEmpty(configuration.getBaseUrl(), "baseUrl"));

        return builder.build();
    }

    protected String resolveApiKey() {
        // Priority: URI parameter > component config > environment variable > application.properties
        if (ObjectHelper.isNotEmpty(configuration.getApiKey())) {
            return configuration.getApiKey();
        }

        String envApiKey = System.getenv("OPENAI_API_KEY");
        if (ObjectHelper.isNotEmpty(envApiKey)) {
            return envApiKey;
        }

        return System.getProperty("openai.api.key");
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public OpenAIConfiguration getConfiguration() {
        return configuration;
    }

    public OpenAIClient getClient() {
        return client;
    }
}
