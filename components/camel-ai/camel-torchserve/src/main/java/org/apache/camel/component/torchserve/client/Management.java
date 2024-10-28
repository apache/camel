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
package org.apache.camel.component.torchserve.client;

import java.util.List;

import org.apache.camel.component.torchserve.client.model.Api;
import org.apache.camel.component.torchserve.client.model.ApiException;
import org.apache.camel.component.torchserve.client.model.ModelDetail;
import org.apache.camel.component.torchserve.client.model.ModelList;
import org.apache.camel.component.torchserve.client.model.RegisterOptions;
import org.apache.camel.component.torchserve.client.model.Response;
import org.apache.camel.component.torchserve.client.model.ScaleWorkerOptions;
import org.apache.camel.component.torchserve.client.model.UnregisterOptions;

/**
 * Management API
 */
public interface Management {

    /**
     * Register a new model in TorchServe.
     */
    Response registerModel(String url, RegisterOptions options) throws ApiException;

    /**
     * Configure number of workers for a default version of a model. This is an asynchronous call by default. Caller
     * need to call describeModel to check if the model workers has been changed.
     */
    Response setAutoScale(String modelName, ScaleWorkerOptions options) throws ApiException;

    /**
     * Configure number of workers for a specified version of a model. This is an asynchronous call by default. Caller
     * need to call describeModel to check if the model workers has been changed.
     */
    Response setAutoScale(String modelName, String modelVersion, ScaleWorkerOptions options) throws ApiException;

    /**
     * Provides detailed information about the default version of a model.
     */
    List<ModelDetail> describeModel(String modelName) throws ApiException;

    /**
     * Provides detailed information about the specified version of a model. If "all" is specified as version, returns
     * the details about all the versions of the model.
     */
    List<ModelDetail> describeModel(String modelName, String modelVersion) throws ApiException;

    /**
     * Unregister the default version of a model from TorchServe if it is the only version available. This is an
     * asynchronous call by default. Caller can call listModels to confirm model is unregistered.
     */
    Response unregisterModel(String modelName, UnregisterOptions options) throws ApiException;

    /**
     * Unregister the specified version of a model from TorchServe. This is an asynchronous call by default. Caller can
     * call listModels to confirm model is unregistered.
     */
    Response unregisterModel(String modelName, String modelVersion, UnregisterOptions options) throws ApiException;

    /**
     * List registered models in TorchServe.
     */
    ModelList listModels(Integer limit, String nextPageToken) throws ApiException;

    /**
     * Set default version of a model.
     */
    Response setDefault(String modelName, String modelVersion) throws ApiException;

    /**
     * Get openapi description.
     */
    Api apiDescription() throws ApiException;

    /**
     * Not supported yet.
     */
    Object token(String type) throws ApiException;

}
