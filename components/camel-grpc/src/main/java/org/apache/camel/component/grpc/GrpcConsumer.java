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

import java.net.InetSocketAddress;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.grpc.auth.jwt.JwtServerInterceptor;
import org.apache.camel.component.grpc.server.BindableServiceFactory;
import org.apache.camel.component.grpc.server.DefaultBindableServiceFactory;
import org.apache.camel.component.grpc.server.GrpcHeaderInterceptor;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.grpc.GrpcConstants.GRPC_BINDABLE_SERVICE_FACTORY_NAME;

/**
 * Represents gRPC server consumer implementation
 */
public class GrpcConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcConsumer.class);

    protected final GrpcConfiguration configuration;
    protected final GrpcEndpoint endpoint;

    private Server server;
    private BindableServiceFactory factory;

    public GrpcConsumer(GrpcEndpoint endpoint, Processor processor, GrpcConfiguration configuration) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    public GrpcConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (server == null) {
            LOG.info("Starting the gRPC server");
            initializeServer();
            server.start();
            LOG.info("gRPC server started and listening on port: {}", server.getPort());
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (server != null) {
            LOG.debug("Terminating gRPC server");
            server.shutdown().shutdownNow();
            server = null;
        }
        super.doStop();
    }

    protected void initializeServer() throws Exception {
        NettyServerBuilder serverBuilder;
        BindableService bindableService = getBindableServiceFactory().createBindableService(this);
        ServerInterceptor headerInterceptor = new GrpcHeaderInterceptor();

        if (!ObjectHelper.isEmpty(configuration.getHost()) && !ObjectHelper.isEmpty(configuration.getPort())) {
            LOG.debug("Building gRPC server on {}:{}", configuration.getHost(), configuration.getPort());
            serverBuilder
                    = NettyServerBuilder.forAddress(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        } else {
            throw new IllegalArgumentException("No server start properties (host, port) specified");
        }

        if (configuration.isRouteControlledStreamObserver()
                && configuration.getConsumerStrategy() == GrpcConsumerStrategy.AGGREGATION) {
            throw new IllegalArgumentException(
                    "Consumer strategy AGGREGATION and routeControlledStreamObserver are not compatible. Set the consumer strategy to PROPAGATION or DELEGATION");
        }

        if (configuration.getNegotiationType() == NegotiationType.TLS) {
            ObjectHelper.notNull(configuration.getKeyCertChainResource(), "keyCertChainResource");
            ObjectHelper.notNull(configuration.getKeyResource(), "keyResource");

            SslContextBuilder sslContextBuilder
                    = SslContextBuilder
                            .forServer(
                                    ResourceHelper.resolveResourceAsInputStream(endpoint.getCamelContext(),
                                            configuration.getKeyCertChainResource()),
                                    ResourceHelper.resolveResourceAsInputStream(endpoint.getCamelContext(),
                                            configuration.getKeyResource()),
                                    configuration.getKeyPassword())
                            .clientAuth(ClientAuth.REQUIRE);

            if (ObjectHelper.isNotEmpty(configuration.getTrustCertCollectionResource())) {
                sslContextBuilder
                        = sslContextBuilder.trustManager(ResourceHelper.resolveResourceAsInputStream(endpoint.getCamelContext(),
                                configuration.getTrustCertCollectionResource()));
            }

            serverBuilder = serverBuilder.sslContext(GrpcSslContexts.configure(sslContextBuilder).build());
        }

        if (configuration.getAuthenticationType() == GrpcAuthType.JWT) {
            ObjectHelper.notNull(configuration.getJwtSecret(), "jwtSecret");

            serverBuilder = serverBuilder.intercept(new JwtServerInterceptor(
                    configuration.getJwtAlgorithm(), configuration.getJwtSecret(),
                    configuration.getJwtIssuer(), configuration.getJwtSubject()));
        }

        for (ServerInterceptor si : configuration.getServerInterceptors()) {
            serverBuilder.intercept(si);
        }

        server = serverBuilder.addService(ServerInterceptors.intercept(bindableService, headerInterceptor))
                .maxInboundMessageSize(configuration.getMaxMessageSize())
                .flowControlWindow(configuration.getFlowControlWindow())
                .maxConcurrentCallsPerConnection(configuration.getMaxConcurrentCallsPerConnection())
                .build();
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        exchange.getIn().setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT);
        return doSend(exchange, callback);
    }

    public void onCompleted(Exchange exchange) {
        if (configuration.isForwardOnCompleted()) {
            exchange.getIn().setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED);
            doSend(exchange, done -> {
            });
        }
    }

    public void onError(Exchange exchange, Throwable error) {
        if (configuration.isForwardOnError()) {
            exchange.getIn().setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR);
            exchange.getIn().setBody(error);

            doSend(exchange, done -> {
            });
        }
    }

    private boolean doSend(Exchange exchange, AsyncCallback callback) {
        if (this.isRunAllowed()) {
            this.getAsyncProcessor().process(exchange, doneSync -> {
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                }
                callback.done(doneSync);
            });
            return false;
        } else {
            LOG.warn("Consumer not ready to process exchanges. The exchange {} will be discarded", exchange);
            callback.done(true);
            return true;
        }
    }

    private BindableServiceFactory getBindableServiceFactory() {
        CamelContext context = endpoint.getCamelContext();
        if (this.factory == null) {
            // Try to resolve from the registry
            BindableServiceFactory bindableServiceFactory
                    = CamelContextHelper.lookup(context, GRPC_BINDABLE_SERVICE_FACTORY_NAME, BindableServiceFactory.class);
            if (bindableServiceFactory != null) {
                this.factory = bindableServiceFactory;
            } else {
                // Fallback to the default implementation if an alternative is not available
                this.factory = new DefaultBindableServiceFactory();
            }
        }
        return this.factory;
    }
}
