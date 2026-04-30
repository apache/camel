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

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class TensorFlowServingServiceFactory {

    private static class SingletonTensorFlowServingService extends SingletonService<TensorFlowServingService>
            implements TensorFlowServingService {
        public SingletonTensorFlowServingService(TensorFlowServingService service, String name) {
            super(service, name);
        }

        @Override
        public int grpcPort() {
            return getService().grpcPort();
        }

        @Override
        public int restPort() {
            return getService().restPort();
        }
    }

    private TensorFlowServingServiceFactory() {
    }

    public static SimpleTestServiceBuilder<TensorFlowServingService> builder() {
        return new SimpleTestServiceBuilder<>("tensorflow-serving");
    }

    public static TensorFlowServingService createService() {
        return builder()
                .addLocalMapping(TensorFlowServingLocalContainerService::new)
                .addRemoteMapping(TensorFlowServingRemoteService::new)
                .build();
    }

    public static TensorFlowServingService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final TensorFlowServingService INSTANCE;
        static {
            SimpleTestServiceBuilder<TensorFlowServingService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonTensorFlowServingService(
                            new TensorFlowServingLocalContainerService(),
                            "tensorflow-serving"))
                    .addRemoteMapping(TensorFlowServingRemoteService::new);
            INSTANCE = instance.build();
        }
    }
}
