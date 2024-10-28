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

import java.util.Map;

import org.apache.camel.component.torchserve.client.Inference;
import org.apache.camel.component.torchserve.client.inference.api.DefaultApi;
import org.apache.camel.component.torchserve.client.inference.invoker.ApiClient;
import org.apache.camel.component.torchserve.client.model.Api;
import org.apache.camel.component.torchserve.client.model.ApiException;
import org.apache.camel.component.torchserve.client.model.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultInference implements Inference {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultInference.class);

    private final DefaultApi api;

    public DefaultInference() {
        this(8080);
    }

    public DefaultInference(int port) {
        this("http://localhost:" + port);
    }

    public DefaultInference(String address) {
        ApiClient client = new ApiClient().setBasePath(address);
        this.api = new DefaultApi(client);
        LOG.debug("Inference API address: {}", address);
    }

    public void setAuthToken(String token) {
        api.getApiClient().addDefaultHeader("Authorization", "Bearer " + token);
    }

    @Override
    public Api apiDescription() throws ApiException {
        try {
            // Workaround for HTTPClient 5.4 requiring content-type for OPTIONS requests
            return Api.from(api.apiDescription(Map.of("Content-Type", "application/json")));
        } catch (org.apache.camel.component.torchserve.client.inference.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public Response ping() throws ApiException {
        try {
            return Response.from(api.ping());
        } catch (org.apache.camel.component.torchserve.client.inference.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public Object predictions(String modelName, Object body) throws ApiException {
        try {
            // /predictions/{model_name}
            return api.predictions_1(modelName, body);
        } catch (org.apache.camel.component.torchserve.client.inference.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public Object predictions(String modelName, String modelVersion, Object body) throws ApiException {
        try {
            return api.versionPredictions(modelName, modelVersion, body);
        } catch (org.apache.camel.component.torchserve.client.inference.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public Object explanations(String modelName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

}
