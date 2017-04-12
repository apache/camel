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

import java.util.ArrayList;
import java.util.List;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Represents asynchronous and synchronous gRPC producer implementations.
 */
public class GrpcProducer extends DefaultProducer implements AsyncProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcProducer.class);

    protected final GrpcConfiguration configuration;
    protected final GrpcEndpoint endpoint;
    private ManagedChannel channel;
    private Object grpcStub;

    public GrpcProducer(GrpcEndpoint endpoint, GrpcConfiguration configuration) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message message = exchange.getIn();

        StreamObserver<Object> asyncHandler = new StreamObserver<Object>() {

            @SuppressWarnings("unchecked")
            @Override
            public void onNext(Object response) {
                final Object currentBody = exchange.getOut().getBody();
                List<Object> returnBody = new ArrayList<Object>();
                if (currentBody instanceof List) {
                    returnBody = (List<Object>)currentBody;
                }
                returnBody.add(response);
                exchange.getOut().setBody(returnBody);
            }

            @Override
            public void onError(Throwable t) {
                exchange.setException(t);
                callback.done(false);
            }

            @Override
            public void onCompleted() {
                exchange.getOut().setHeaders(exchange.getIn().getHeaders());
                callback.done(false);
            }
        };
        try {
            GrpcUtils.invokeAsyncMethod(grpcStub, configuration.getMethod(), message.getBody(), asyncHandler);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
        return false;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getIn();
        Object outBody = GrpcUtils.invokeSyncMethod(grpcStub, configuration.getMethod(), message.getBody());
        exchange.getOut().setBody(outBody);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (channel == null) {
            initializeChannel();
            if (endpoint.isSynchronous()) {
                LOG.info("Getting synchronous method stub from channel");
                grpcStub = GrpcUtils.constructGrpcBlockingStub(configuration.getServicePackage(), configuration.getServiceName(), channel);
            } else {
                LOG.info("Getting asynchronous method stub from channel");
                grpcStub = GrpcUtils.constructGrpcAsyncStub(configuration.getServicePackage(), configuration.getServiceName(), channel);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (channel == null) {
            LOG.trace("Terminating channel to the remote gRPC server " + channel);
            channel.shutdown().shutdownNow();
            channel = null;
            grpcStub = null;
        }
        super.doStop();
    }

    protected void initializeChannel() {
        if (!StringUtils.isEmpty(configuration.getHost()) && !StringUtils.isEmpty(configuration.getPort())) {
            LOG.info("Creating channel to the remote gRPC server " + configuration.getHost() + ":" + configuration.getPort());
            channel = ManagedChannelBuilder.forAddress(configuration.getHost(), configuration.getPort()).usePlaintext(configuration.getUsePlainText()).build();
        } else if (!StringUtils.isEmpty(configuration.getTarget())) {
            LOG.info("Creating channel to the remote gRPC server " + configuration.getTarget());
            channel = ManagedChannelBuilder.forTarget(configuration.getTarget()).usePlaintext(configuration.getUsePlainText()).build();
        } else {
            throw new IllegalArgumentException("No connection properties (host, port or target) specified");
        }
    }
}
