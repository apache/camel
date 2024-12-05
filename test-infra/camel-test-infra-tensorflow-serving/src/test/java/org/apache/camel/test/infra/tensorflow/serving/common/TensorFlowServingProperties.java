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
package org.apache.camel.test.infra.tensorflow.serving.common;

public class TensorFlowServingProperties {
    public static final String TENSORFLOW_SERVING_GRPC_PORT = "tensorflow.serving.grpc.port";
    public static final String TENSORFLOW_SERVING_REST_PORT = "tensorflow.serving.rest.port";
    public static final String TENSORFLOW_SERVING_CONTAINER = "tensorflow.serving.container";
    public static final String TENSORFLOW_SERVING_CONTAINER_ARM64 = "tensorflow.serving.container.arm64";

    private TensorFlowServingProperties() {
    }
}
