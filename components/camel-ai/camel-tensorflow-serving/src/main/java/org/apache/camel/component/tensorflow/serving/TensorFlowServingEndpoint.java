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

import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
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

        // Close the channel
        channel.shutdown();
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
