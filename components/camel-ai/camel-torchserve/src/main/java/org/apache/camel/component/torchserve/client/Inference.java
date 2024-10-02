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

import org.apache.camel.component.torchserve.client.model.Api;
import org.apache.camel.component.torchserve.client.model.ApiException;
import org.apache.camel.component.torchserve.client.model.Response;

/**
 * Inference API
 */
public interface Inference {

    /**
     * Get openapi description.
     */
    Api apiDescription() throws ApiException;

    /**
     * Get TorchServe status.
     */
    Response ping() throws ApiException;

    /**
     * Predictions entry point to get inference using default model version.
     */
    Object predictions(String modelName, Object body) throws ApiException;

    /**
     * Predictions entry point to get inference using specific model version.
     */
    Object predictions(String modelName, String modelVersion, Object body) throws ApiException;

    /**
     * Not supported yet.
     */
    Object explanations(String modelName) throws ApiException;

}
