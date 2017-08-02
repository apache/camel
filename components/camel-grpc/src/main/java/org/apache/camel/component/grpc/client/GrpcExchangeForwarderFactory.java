/**
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
package org.apache.camel.component.grpc.client;

import org.apache.camel.component.grpc.GrpcConfiguration;
import org.apache.camel.component.grpc.GrpcProducerStrategy;

/**
 * Creates the correct forwarder according to the configuration.
 */
public final class GrpcExchangeForwarderFactory {

    private GrpcExchangeForwarderFactory() {
    }

    public static GrpcExchangeForwarder createExchangeForwarder(GrpcConfiguration configuration, Object grpcStub) {
        if (configuration.getProducerStrategy() == GrpcProducerStrategy.SIMPLE) {
            return new GrpcSimpleExchangeForwarder(configuration, grpcStub);
        } else if (configuration.getProducerStrategy() == GrpcProducerStrategy.STREAMING) {
            return new GrpcStreamingExchangeForwarder(configuration, grpcStub);
        } else {
            throw new IllegalStateException("Unsupported producer strategy: " + configuration.getProducerStrategy());
        }
    }

}
