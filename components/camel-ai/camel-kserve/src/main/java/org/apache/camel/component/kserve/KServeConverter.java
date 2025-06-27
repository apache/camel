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
package org.apache.camel.component.kserve;

import inference.GrpcPredictV2;
import org.apache.camel.Converter;

/**
 * Converter methods to convert from / to KServe types.
 */
@Converter(generateLoader = true)
public class KServeConverter {

    @Converter
    public static GrpcPredictV2.ModelInferRequest toModelInferRequest(
            GrpcPredictV2.ModelInferRequest.Builder builder) {
        return builder.build();
    }

    @Converter
    public static GrpcPredictV2.ModelReadyRequest toModelReadyRequest(
            GrpcPredictV2.ModelReadyRequest.Builder builder) {
        return builder.build();
    }

    @Converter
    public static GrpcPredictV2.ModelMetadataRequest toModelMetadataRequest(
            GrpcPredictV2.ModelMetadataRequest.Builder builder) {
        return builder.build();
    }

    @Converter
    public static GrpcPredictV2.ServerReadyRequest toServerReadyRequest(
            GrpcPredictV2.ServerReadyRequest.Builder builder) {
        return builder.build();
    }

    @Converter
    public static GrpcPredictV2.ServerLiveRequest toServerLiveRequest(
            GrpcPredictV2.ServerLiveRequest.Builder builder) {
        return builder.build();
    }

    @Converter
    public static GrpcPredictV2.ServerMetadataRequest toServerMetadataRequest(
            GrpcPredictV2.ServerMetadataRequest.Builder builder) {
        return builder.build();
    }
}
