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

import java.util.List;

import org.apache.camel.component.torchserve.client.Management;
import org.apache.camel.component.torchserve.client.management.api.DefaultApi;
import org.apache.camel.component.torchserve.client.management.invoker.ApiClient;
import org.apache.camel.component.torchserve.client.model.Api;
import org.apache.camel.component.torchserve.client.model.ApiException;
import org.apache.camel.component.torchserve.client.model.ModelDetail;
import org.apache.camel.component.torchserve.client.model.ModelList;
import org.apache.camel.component.torchserve.client.model.RegisterOptions;
import org.apache.camel.component.torchserve.client.model.Response;
import org.apache.camel.component.torchserve.client.model.ScaleWorkerOptions;
import org.apache.camel.component.torchserve.client.model.UnregisterOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultManagement implements Management {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultManagement.class);

    private final DefaultApi api;

    public DefaultManagement() {
        this(8081);
    }

    public DefaultManagement(int port) {
        this("http://localhost:" + port);
    }

    public DefaultManagement(String address) {
        ApiClient client = new ApiClient().setBasePath(address);
        this.api = new DefaultApi(client);
        LOG.debug("Management API address: {}", address);
    }

    public void setAuthToken(String token) {
        api.getApiClient().addDefaultHeader("Authorization", "Bearer " + token);
    }

    @Override
    public Response registerModel(String url, RegisterOptions options) throws ApiException {
        try {
            return Response.from(api.registerModel(url, null,
                    options.getModelName(),
                    options.getHandler(),
                    options.getRuntime(),
                    options.getBatchSize(),
                    options.getMaxBatchDelay(),
                    options.getResponseTimeout(),
                    options.getInitialWorkers(),
                    options.getSynchronous(),
                    options.getS3SseKms()));
        } catch (org.apache.camel.component.torchserve.client.management.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public Response setAutoScale(String modelName, ScaleWorkerOptions options) throws ApiException {
        try {
            return Response.from(api.setAutoScale(modelName,
                    options.getMinWorker(),
                    options.getMaxWorker(),
                    options.getNumberGpu(),
                    options.getSynchronous(),
                    options.getTimeout()));
        } catch (org.apache.camel.component.torchserve.client.management.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public Response setAutoScale(String modelName, String modelVersion, ScaleWorkerOptions options) throws ApiException {
        try {
            return Response.from(api.versionSetAutoScale(modelName, modelVersion,
                    options.getMinWorker(),
                    options.getMaxWorker(),
                    options.getNumberGpu(),
                    options.getSynchronous(),
                    options.getTimeout()));
        } catch (org.apache.camel.component.torchserve.client.management.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public List<ModelDetail> describeModel(String modelName) throws ApiException {
        try {
            return ModelDetail.from(api.describeModel(modelName));
        } catch (org.apache.camel.component.torchserve.client.management.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public List<ModelDetail> describeModel(String modelName, String modelVersion) throws ApiException {
        try {
            return ModelDetail.from(api.versionDescribeModel(modelName, modelVersion));
        } catch (org.apache.camel.component.torchserve.client.management.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public Response unregisterModel(String modelName, UnregisterOptions options) throws ApiException {
        try {
            return Response.from(api.unregisterModel(modelName,
                    options.getSynchronous(),
                    options.getTimeout()));
        } catch (org.apache.camel.component.torchserve.client.management.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public Response unregisterModel(String modelName, String modelVersion, UnregisterOptions options)
            throws ApiException {
        try {
            return Response.from(api.versionUnregisterModel(modelName, modelVersion,
                    options.getSynchronous(),
                    options.getTimeout()));
        } catch (org.apache.camel.component.torchserve.client.management.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public ModelList listModels(Integer limit, String nextPageToken) throws ApiException {
        try {
            return ModelList.from(api.listModels(limit, nextPageToken));
        } catch (org.apache.camel.component.torchserve.client.management.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public Response setDefault(String modelName, String modelVersion) throws ApiException {
        try {
            return Response.from(api.setDefault(modelName, modelVersion));
        } catch (org.apache.camel.component.torchserve.client.management.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public Api apiDescription() throws ApiException {
        try {
            return Api.from(api.apiDescription());
        } catch (org.apache.camel.component.torchserve.client.management.invoker.ApiException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public Object token(String type) throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }
}
