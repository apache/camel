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

import java.io.InputStream;
import java.time.Duration;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.grpc.auth.jwt.JwtCallCredentials;
import org.apache.camel.component.grpc.auth.jwt.JwtTokenSupplier;
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
                ObjectHelper.notNull(configuration.getServiceAccountResource(), "serviceAccountResource");

                Credentials creds = GoogleCredentials.fromStream(
                        ResourceHelper.resolveResourceAsInputStream(endpoint.getCamelContext(),
                                configuration.getServiceAccountResource()));
                callCreds = MoreCallCredentials.from(creds);
            } else if (configuration.getAuthenticationType() == GrpcAuthType.JWT) {
                ObjectHelper.notNull(configuration.getJwtSecret(), "jwtSecret");

                JwtTokenSupplier jwtTokenSupplier = new JwtTokenSupplier(
                        configuration.getJwtAlgorithm(), configuration.getJwtSecret(),
                        configuration.getJwtIssuer(), configuration.getJwtSubject(),
                        Duration.ofMillis(configuration.getJwtTokenLifetime()));
                // create the first token now, so a wrong configuration fails at startup
                jwtTokenSupplier.get();
                callCreds = new JwtCallCredentials(jwtTokenSupplier);
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

        if (ObjectHelper.isNotEmpty(configuration.getHost()) && configuration.getPort() > 0) {
            LOG.info("Creating channel to the remote gRPC server {}:{}", configuration.getHost(), configuration.getPort());
            channelBuilder = NettyChannelBuilder.forAddress(configuration.getHost(), configuration.getPort());
        } else {
            throw new IllegalArgumentException("No connection properties (host or port) specified");
        }
        if (configuration.getNegotiationType() == NegotiationType.TLS) {
            channelBuilder = channelBuilder.sslContext(createSslContext());
        }

        channel = channelBuilder.negotiationType(configuration.getNegotiationType())
                .flowControlWindow(configuration.getFlowControlWindow())
                .userAgent(configuration.getUserAgent())
                .maxInboundMessageSize(configuration.getMaxMessageSize())
                .intercept(configuration.getClientInterceptors())
                .build();
    }

    /**
     * Creates the client SSL context. The server is always verified, with trustCertCollectionResource when set, else
     * with the default trust store of the JVM. The client authenticates with its own certificate (mutual TLS) only when
     * both keyCertChainResource and keyResource are set.
     */
    private SslContext createSslContext() throws Exception {
        boolean hasCertChain = ObjectHelper.isNotEmpty(configuration.getKeyCertChainResource());
        boolean hasKey = ObjectHelper.isNotEmpty(configuration.getKeyResource());
        if (hasCertChain != hasKey) {
            throw new IllegalArgumentException(
                    "Both keyCertChainResource and keyResource must be set to authenticate the client with its own"
                                               + " certificate (mutual TLS), or none of them to only verify the server, but "
                                               + (hasCertChain ? "keyResource" : "keyCertChainResource") + " is missing");
        }

        SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient();
        if (hasCertChain) {
            try (InputStream certChain = ResourceHelper.resolveMandatoryResourceAsInputStream(endpoint.getCamelContext(),
                    configuration.getKeyCertChainResource());
                 InputStream key = ResourceHelper.resolveMandatoryResourceAsInputStream(endpoint.getCamelContext(),
                         configuration.getKeyResource())) {
                sslContextBuilder.keyManager(certChain, key, configuration.getKeyPassword());
            }
        }
        if (ObjectHelper.isNotEmpty(configuration.getTrustCertCollectionResource())) {
            try (InputStream trustCerts = ResourceHelper.resolveMandatoryResourceAsInputStream(endpoint.getCamelContext(),
                    configuration.getTrustCertCollectionResource())) {
                sslContextBuilder.trustManager(trustCerts);
            }
        }
        return GrpcSslContexts.configure(sslContextBuilder).build();
    }
}
