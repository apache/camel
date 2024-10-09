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
package org.apache.camel.component.torchserve.client.model;

public class RegisterOptions {

    /**
     * Name of model. This value will override modelName in MANIFEST.json if present. (optional)
     */
    private String modelName = null;
    /**
     * Inference handler entry-point. This value will override handler in MANIFEST.json if present. (optional)
     */
    private String handler = null;
    /**
     * Runtime for the model custom service code. This value will override runtime in MANIFEST.json if present.
     * (optional)
     */
    private String runtime = null;
    /**
     * Inference batch size, default: 1. (optional)
     */
    private Integer batchSize = null;
    /**
     * Maximum delay for batch aggregation, default: 100. (optional)
     */
    private Integer maxBatchDelay = null;
    /**
     * Maximum time, in seconds, the TorchServe waits for a response from the model inference code, default: 120.
     * (optional)
     */
    private Integer responseTimeout = null;
    /**
     * Maximum time, in seconds, the TorchServe waits for the model to startup/initialize, default: 120. (optional)
     */
    private Integer startupTimeout = null;
    /**
     * Number of initial workers, default: 0. (optional)
     */
    private Integer initialWorkers = null;
    /**
     * Decides whether creation of worker synchronous or not, default: false. (optional, default to false)
     */
    private Boolean synchronous = null;
    /**
     * Model mar file is S3 SSE KMS(server side encryption) enabled or not, default: false. (optional, default to false)
     */
    private Boolean s3SseKms = null;

    private RegisterOptions() {
    }

    public static RegisterOptions empty() {
        return new RegisterOptions();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getModelName() {
        return modelName;
    }

    public String getHandler() {
        return handler;
    }

    public String getRuntime() {
        return runtime;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public Integer getMaxBatchDelay() {
        return maxBatchDelay;
    }

    public Integer getResponseTimeout() {
        return responseTimeout;
    }

    public Integer getStartupTimeout() {
        return startupTimeout;
    }

    public Integer getInitialWorkers() {
        return initialWorkers;
    }

    public Boolean getSynchronous() {
        return synchronous;
    }

    public Boolean getS3SseKms() {
        return s3SseKms;
    }

    public static class Builder {

        private final RegisterOptions options = new RegisterOptions();

        public Builder modelName(String modelName) {
            options.modelName = modelName;
            return this;
        }

        public Builder handler(String handler) {
            options.handler = handler;
            return this;
        }

        public Builder runtime(String runtime) {
            options.runtime = runtime;
            return this;
        }

        public Builder batchSize(Integer batchSize) {
            options.batchSize = batchSize;
            return this;
        }

        public Builder maxBatchDelay(Integer maxBatchDelay) {
            options.maxBatchDelay = maxBatchDelay;
            return this;
        }

        public Builder responseTimeout(Integer responseTimeout) {
            options.responseTimeout = responseTimeout;
            return this;
        }

        public Builder startupTimeout(Integer startupTimeout) {
            options.startupTimeout = startupTimeout;
            return this;
        }

        public Builder initialWorkers(Integer initialWorkers) {
            options.initialWorkers = initialWorkers;
            return this;
        }

        public Builder synchronous(Boolean synchronous) {
            options.synchronous = synchronous;
            return this;
        }

        public Builder s3SseKms(Boolean s3SseKms) {
            options.s3SseKms = s3SseKms;
            return this;
        }

        public RegisterOptions build() {
            return options;
        }
    }
}
