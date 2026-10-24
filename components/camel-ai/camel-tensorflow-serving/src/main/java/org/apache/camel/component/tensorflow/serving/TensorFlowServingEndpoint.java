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

import java.util.concurrent.TimeUnit;

import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import tensorflow.serving.ModelServiceGrpc;
import tensorflow.serving.PredictionServiceGrpc;

@UriEndpoint(firstVersion = "4.10.0", scheme = "tensorflow-serving", title = "TensorFlow Serving",
             syntax = "tensorflow-serving:api", producerOnly = true,
             category = { Category.AI }, headersClass = TensorFlowServingConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.POJO,
                                  byOption = {
                                          @BodyType.When(option = "api", value = "model-status", carriers = Carrier.ANY),
                                          @BodyType.When(option = "api", value = "model-metadata", carriers = Carrier.ANY) },
                                  description = "The protobuf request of the api, or its Builder: a PredictRequest for"
                                                + " predict, a ClassificationRequest or RegressionRequest, or the Input of"
                                                + " one, for classify and regress. Another body, such as text, JSON or"
                                                + " bytes, is not read: an empty request is sent and the server rejects it."
                                                + " model-status and model-metadata need no body, and read a"
                                                + " GetModelStatusRequest or GetModelMetadataRequest when it is one. The"
                                                + " model comes from the CamelTensorFlowServingModelName header, or else"
                                                + " the modelName option"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   byOption = {
                                           @BodyType.When(option = "api", value = "model-status", carriers = Carrier.POJO,
                                                          javaType = "tensorflow.serving.GetModelStatus$GetModelStatusResponse"),
                                           @BodyType.When(option = "api", value = "model-metadata", carriers = Carrier.POJO,
                                                          javaType = "tensorflow.serving.GetModelMetadata$GetModelMetadataResponse"),
                                           @BodyType.When(option = "api", value = "classify", carriers = Carrier.POJO,
                                                          javaType = "tensorflow.serving.Classification$ClassificationResponse"),
                                           @BodyType.When(option = "api", value = "regress", carriers = Carrier.POJO,
                                                          javaType = "tensorflow.serving.RegressionOuterClass$RegressionResponse"),
                                           @BodyType.When(option = "api", value = "predict", carriers = Carrier.POJO,
                                                          javaType = "tensorflow.serving.Predict$PredictResponse") },
                                   description = "The protobuf response of the api, such as a PredictResponse with the"
                                                 + " output tensors by name"))
public class TensorFlowServingEndpoint extends DefaultEndpoint {

    @UriPath(enums = "model-status,model-metadata,classify,regress,predict", description = "The TensorFlow Serving API")
    @Metadata(required = true)
    private final String api;

    @UriParam
    private TensorFlowServingConfiguration configuration;

    private ManagedChannel channel;
    private ModelServiceGrpc.ModelServiceBlockingStub modelService;
    private PredictionServiceGrpc.PredictionServiceBlockingStub predictionService;

    public TensorFlowServingEndpoint(String uri, TensorFlowServingComponent component, String path,
                                     TensorFlowServingConfiguration configuration) {
        super(uri, component);
        this.api = path;
        this.configuration = configuration;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        ChannelCredentials credentials = configuration.getCredentials() != null
                ? configuration.getCredentials()
                : InsecureChannelCredentials.create();
        channel = Grpc.newChannelBuilder(configuration.getTarget(), credentials).build();
        modelService = ModelServiceGrpc.newBlockingStub(channel);
        predictionService = PredictionServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();

        // Close the channel if it was created (doInit may have failed before assigning it)
        if (channel != null) {
            channel.shutdown();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Override
    public Producer createProducer() {
        return new TensorFlowServingProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    public String getApi() {
        return api;
    }

    public TensorFlowServingConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(TensorFlowServingConfiguration configuration) {
        this.configuration = configuration;
    }

    public ModelServiceGrpc.ModelServiceBlockingStub getModelService() {
        return modelService;
    }

    public PredictionServiceGrpc.PredictionServiceBlockingStub getPredictionService() {
        return predictionService;
    }
}
