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
package org.apache.camel.component.grpc;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContextBuilder;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.grpc.auth.jwt.JwtCallCredentials;
import org.apache.camel.component.grpc.auth.jwt.JwtHelper;
import org.apache.camel.component.grpc.client.*;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents asynchronous and synchronous gRPC producer implementations.
 */
public class GrpcProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcProducer.class);

    protected final GrpcConfiguration configuration;
    protected final GrpcEndpoint endpoint;
    private ManagedChannel channel;
    private Object grpcStub;
    private GrpcExchangeForwarder forwarder;
    private GrpcStreamObserverFactory streamObserverFactory;

    public GrpcProducer(GrpcEndpoint endpoint, GrpcConfiguration configuration) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = configuration;

        if (configuration.getProducerStrategy() == GrpcProducerStrategy.STREAMING) {
            if (configuration.isSynchronous()) {
                throw new IllegalStateException("Cannot use synchronous processing in streaming mode");
            } else if (configuration.getStreamRepliesTo() == null) {
                throw new IllegalStateException("The streamReplyTo property is mandatory when using the STREAMING mode");
            }
        }

        if (configuration.getAuthenticationType() == GrpcAuthType.GOOGLE
                && configuration.getNegotiationType() != NegotiationType.TLS) {
            throw new IllegalStateException("Google token-based authentication requires SSL/TLS negotiation mode");
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        StreamObserver<Object> streamObserver = streamObserverFactory.getStreamObserver(exchange, callback);
        return forwarder.forward(exchange, streamObserver, callback);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        forwarder.forward(exchange);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (channel == null) {
            CallCredentials callCreds = null;
            initializeChannel();

            if (configuration.getAuthenticationType() == GrpcAuthType.GOOGLE) {
                ObjectHelper.notNull(configuration.getKeyCertChainResource(), "serviceAccountResource");

                Credentials creds = GoogleCredentials.fromStream(
                        ResourceHelper.resolveResourceAsInputStream(endpoint.getCamelContext(),
                                configuration.getServiceAccountResource()));
                callCreds = MoreCallCredentials.from(creds);
            } else if (configuration.getAuthenticationType() == GrpcAuthType.JWT) {
                ObjectHelper.notNull(configuration.getJwtSecret(), "jwtSecret");

                String jwtToken = JwtHelper.createJwtToken(configuration.getJwtAlgorithm(), configuration.getJwtSecret(),
                        configuration.getJwtIssuer(), configuration.getJwtSubject());
                callCreds = new JwtCallCredentials(jwtToken);
            }

            if (configuration.isSynchronous()) {
                LOG.debug("Getting synchronous method stub from channel");
                grpcStub = GrpcUtils.constructGrpcBlockingStub(endpoint.getServicePackage(), endpoint.getServiceName(), channel,
                        callCreds, endpoint.getCamelContext());
            } else {
                LOG.debug("Getting asynchronous method stub from channel");
                grpcStub = GrpcUtils.constructGrpcAsyncStub(endpoint.getServicePackage(), endpoint.getServiceName(), channel,
                        callCreds, endpoint.getCamelContext());
            }
            forwarder = GrpcExchangeForwarderFactory.createExchangeForwarder(configuration, grpcStub);

            streamObserverFactory = new GrpcStreamObserverFactory(getEndpoint(), configuration);
            ServiceHelper.startService(streamObserverFactory);
        }
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(streamObserverFactory);
        if (channel != null) {
            forwarder.shutdown();
            forwarder = null;

            LOG.debug("Terminating channel to the remote gRPC server");
            channel.shutdown().shutdownNow();
            channel = null;
            grpcStub = null;
            streamObserverFactory = null;
        }
        super.doStop();
    }

    protected void initializeChannel() throws Exception {
        NettyChannelBuilder channelBuilder;

        if (!ObjectHelper.isEmpty(configuration.getHost()) && !ObjectHelper.isEmpty(configuration.getPort())) {
            LOG.info("Creating channel to the remote gRPC server {}:{}", configuration.getHost(), configuration.getPort());
            channelBuilder = NettyChannelBuilder.forAddress(configuration.getHost(), configuration.getPort());
        } else {
            throw new IllegalArgumentException("No connection properties (host or port) specified");
        }
        if (configuration.getNegotiationType() == NegotiationType.TLS) {
            ObjectHelper.notNull(configuration.getKeyCertChainResource(), "keyCertChainResource");
            ObjectHelper.notNull(configuration.getKeyResource(), "keyResource");

            SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient()
                    .keyManager(
                            ResourceHelper.resolveResourceAsInputStream(endpoint.getCamelContext(),
                                    configuration.getKeyCertChainResource()),
                            ResourceHelper.resolveResourceAsInputStream(endpoint.getCamelContext(),
                                    configuration.getKeyResource()),
                            configuration.getKeyPassword());

            if (ObjectHelper.isNotEmpty(configuration.getTrustCertCollectionResource())) {
                sslContextBuilder
                        = sslContextBuilder.trustManager(ResourceHelper.resolveResourceAsInputStream(endpoint.getCamelContext(),
                                configuration.getTrustCertCollectionResource()));
            }

            channelBuilder = channelBuilder.sslContext(GrpcSslContexts.configure(sslContextBuilder).build());
        }

        channel = channelBuilder.negotiationType(configuration.getNegotiationType())
                .flowControlWindow(configuration.getFlowControlWindow())
                .userAgent(configuration.getUserAgent())
                .maxInboundMessageSize(configuration.getMaxMessageSize())
                .intercept(configuration.getClientInterceptors())
                .build();
    }
}
