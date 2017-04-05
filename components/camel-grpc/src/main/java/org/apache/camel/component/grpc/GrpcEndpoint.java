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
package org.apache.camel.component.grpc;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.SynchronousDelegateProducer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * The gRPC component is using for calling remote procedures via HTTP/2
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "grpc", title = "gRPC", syntax = "grpc:service", producerOnly = true, label = "rpc")
public class GrpcEndpoint extends DefaultEndpoint {
    @UriParam
    protected final GrpcConfiguration configuration;

    public GrpcEndpoint(String uri, GrpcComponent component, GrpcConfiguration config) throws Exception {
        super(uri, component);
        this.configuration = config;
    }

    public Producer createProducer() throws Exception {
        GrpcProducer producer = new GrpcProducer(this, configuration);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(producer);
        } else {
            return producer;
        }
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from a gRPC endpoint: " + getEndpointUri());
    }

    public boolean isSingleton() {
        return true;
    }
}
