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

    /**
     * @deprecated Never read by the component. The gRPC channel is built once when the endpoint is initialised, so it
     *             cannot be redirected per exchange. Use the {@code target} endpoint option instead, or route to a
     *             different endpoint with {@code toD} when the destination varies per message.
     */
    @Metadata(description = "The gRPC target of the model server. The component does not read this header.",
              javaType = "String",
              deprecationNote = "Never read by the component. The gRPC channel is built once when the endpoint is"
                                + " initialised, so it cannot be redirected per exchange. Use the target endpoint option.")
    @Deprecated
    String TARGET = "CamelTensorFlowServingTarget";

    /**
     * @deprecated Never read by the component. The gRPC channel is built once when the endpoint is initialised, so it
     *             cannot be re-authenticated per exchange. Use the {@code credentials} endpoint option instead.
     */
    @Metadata(description = "The credentials of the gRPC channel. The component does not read this header.",
              javaType = "io.grpc.ChannelCredentials",
              deprecationNote = "Never read by the component. The gRPC channel is built once when the endpoint is"
                                + " initialised, so it cannot be re-authenticated per exchange. Use the credentials"
                                + " endpoint option.")
    @Deprecated
    String CREDENTIALS = "CamelTensorFlowServingCredentials";

    @Metadata(description = "The name of the model (servable) to call. It replaces the modelName option, and the model_spec"
                            + " of a request in the body replaces it.",
              javaType = "String")
    String MODEL_NAME = "CamelTensorFlowServingModelName";

    @Metadata(description = "The version number of the model to use. It replaces the modelVersion option. A version label, if"
                            + " one is set, is used instead of it.",
              javaType = "long")
    String MODEL_VERSION = "CamelTensorFlowServingModelVersion";

    @Metadata(description = "The label of the model version to use, for example stable or canary. It replaces the"
                            + " modelVersionLabel option.",
              javaType = "String")
    String MODEL_VERSION_LABEL = "CamelTensorFlowServingModelVersionLabel";

    @Metadata(description = "The name of the signature (SignatureDef) of the model to evaluate. It replaces the signatureName"
                            + " option.",
              javaType = "String")
    String SIGNATURE_NAME = "CamelTensorFlowServingSignatureName";

}
