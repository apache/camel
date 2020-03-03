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
import io.netty.handler.ssl.SslProvider;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.grpc.auth.jwt.JwtCallCredentials;
import org.apache.camel.component.grpc.auth.jwt.JwtHelper;
import org.apache.camel.component.grpc.client.GrpcExchangeForwarder;
import org.apache.camel.component.grpc.client.GrpcExchangeForwarderFactory;
import org.apache.camel.component.grpc.client.GrpcResponseAggregationStreamObserver;
import org.apache.camel.component.grpc.client.GrpcResponseRouterStreamObserver;
import org.apache.camel.spi.ClassResolver;
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
    private StreamObserver<Object> globalResponseObserver;

    public GrpcProducer(GrpcEndpoint endpoint, GrpcConfiguration configuration) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = configuration;

        if (configuration.getProducerStrategy() == GrpcProducerStrategy.STREAMING) {
            if (endpoint.isSynchronous()) {
                throw new IllegalStateException("Cannot use synchronous processing in streaming mode");
            } else if (configuration.getStreamRepliesTo() == null) {
                throw new IllegalStateException("The streamReplyTo property is mandatory when using the STREAMING mode");
            }
        }
        
        if (configuration.getAuthenticationType() == GrpcAuthType.GOOGLE && configuration.getNegotiationType() != NegotiationType.TLS) {
            throw new IllegalStateException("Google token-based authentication requires SSL/TLS negotiation mode");
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        StreamObserver<Object> streamObserver = this.globalResponseObserver;
        if (globalResponseObserver == null) {
            streamObserver = new GrpcResponseAggregationStreamObserver(exchange, callback);
        }

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
                ClassResolver classResolver = endpoint.getCamelContext().getClassResolver();
                
                Credentials creds = GoogleCredentials.fromStream(ResourceHelper.resolveResourceAsInputStream(classResolver, configuration.getServiceAccountResource()));
                callCreds = MoreCallCredentials.from(creds);
            } else if (configuration.getAuthenticationType() == GrpcAuthType.JWT) {
                ObjectHelper.notNull(configuration.getJwtSecret(), "jwtSecret");
                
                String jwtToken = JwtHelper.createJwtToken(configuration.getJwtAlgorithm(), configuration.getJwtSecret(), configuration.getJwtIssuer(), configuration.getJwtSubject());
                callCreds = new JwtCallCredentials(jwtToken);
            }
            
            if (endpoint.isSynchronous()) {
                LOG.debug("Getting synchronous method stub from channel");
                grpcStub = GrpcUtils.constructGrpcBlockingStub(endpoint.getServicePackage(), endpoint.getServiceName(), channel, callCreds, endpoint.getCamelContext());
            } else {
                LOG.debug("Getting asynchronous method stub from channel");
                grpcStub = GrpcUtils.constructGrpcAsyncStub(endpoint.getServicePackage(), endpoint.getServiceName(), channel, callCreds, endpoint.getCamelContext());
            }
            forwarder = GrpcExchangeForwarderFactory.createExchangeForwarder(configuration, grpcStub);

            if (configuration.getStreamRepliesTo() != null) {
                this.globalResponseObserver = new GrpcResponseRouterStreamObserver(configuration, getEndpoint());
            }

            if (this.globalResponseObserver != null) {
                ServiceHelper.startService(this.globalResponseObserver);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (this.globalResponseObserver != null) {
            ServiceHelper.stopService(this.globalResponseObserver);
        }
        if (channel != null) {
            forwarder.shutdown();
            forwarder = null;

            LOG.debug("Terminating channel to the remote gRPC server");
            channel.shutdown().shutdownNow();
            channel = null;
            grpcStub = null;
            globalResponseObserver = null;
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
            
            ClassResolver classResolver = endpoint.getCamelContext().getClassResolver();
            
            SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient()
                                                                 .sslProvider(SslProvider.OPENSSL)
                                                                 .keyManager(ResourceHelper.resolveResourceAsInputStream(classResolver, configuration.getKeyCertChainResource()), 
                                                                             ResourceHelper.resolveResourceAsInputStream(classResolver, configuration.getKeyResource()),
                                                                             configuration.getKeyPassword());
                               
            if (ObjectHelper.isNotEmpty(configuration.getTrustCertCollectionResource())) {
                sslContextBuilder = sslContextBuilder.trustManager(ResourceHelper.resolveResourceAsInputStream(classResolver, configuration.getTrustCertCollectionResource()));
            }
            
            channelBuilder = channelBuilder.sslContext(sslContextBuilder.build());
        }
         
        channel = channelBuilder.negotiationType(configuration.getNegotiationType())
                                .flowControlWindow(configuration.getFlowControlWindow())
                                .userAgent(configuration.getUserAgent())
                                .maxInboundMessageSize(configuration.getMaxMessageSize())
                                .build();
    }
}
