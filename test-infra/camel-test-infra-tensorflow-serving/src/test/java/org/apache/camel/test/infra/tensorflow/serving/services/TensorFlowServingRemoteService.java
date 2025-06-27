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
package org.apache.camel.test.infra.tensorflow.serving.services;

import org.apache.camel.test.infra.tensorflow.serving.common.TensorFlowServingProperties;

public class TensorFlowServingRemoteService implements TensorFlowServingService {

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {
        // NO-OP
    }

    @Override
    public int grpcPort() {
        String value = System.getProperty(TensorFlowServingProperties.TENSORFLOW_SERVING_GRPC_PORT, "8500");
        return Integer.parseInt(value);
    }

    @Override
    public int restPort() {
        String value = System.getProperty(TensorFlowServingProperties.TENSORFLOW_SERVING_REST_PORT, "8501");
        return Integer.parseInt(value);
    }
}
