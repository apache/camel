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
package org.apache.camel.component.tensorflow.serving;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel TensorFlow Serving component.
 */
public interface TensorFlowServingConstants {

    @Metadata(description = "The target of the client. See: https://grpc.github.io/grpc-java/javadoc/io/grpc/Grpc.html#newChannelBuilder(java.lang.String,io.grpc.ChannelCredentials)",
              javaType = "String")
    String TARGET = "CamelTensorFlowServingTarget";

    @Metadata(description = "The credentials of the client.", javaType = "io.grpc.ChannelCredentials")
    String CREDENTIALS = "CamelTensorFlowServingCredentials";

    @Metadata(description = "Required servable name.", javaType = "String")
    String MODEL_NAME = "CamelTensorFlowServingModelName";

    @Metadata(description = "Optional choice of which version of the model to use. Use this specific version number.",
              javaType = "long")
    String MODEL_VERSION = "CamelTensorFlowServingModelVersion";

    @Metadata(description = "Optional choice of which version of the model to use. Use the version associated with the given label.",
              javaType = "String")
    String MODEL_VERSION_LABEL = "CamelTensorFlowServingModelVersionLabel";

    @Metadata(description = "A named signature to evaluate. If unspecified, the default signature will be used.",
              javaType = "String")
    String SIGNATURE_NAME = "CamelTensorFlowServingSignatureName";

}
