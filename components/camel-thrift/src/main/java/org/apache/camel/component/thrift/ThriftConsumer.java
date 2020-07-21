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
package org.apache.camel.component.thrift;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.Proxy;
import javassist.util.proxy.ProxyFactory;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.thrift.server.ThriftHsHaServer;
import org.apache.camel.component.thrift.server.ThriftMethodHandler;
import org.apache.camel.component.thrift.server.ThriftThreadPoolServer;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.apache.thrift.TProcessor;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.transport.TZlibTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents Thrift server consumer implementation
 */
public class ThriftConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ThriftConsumer.class);

    private TNonblockingServerSocket asyncServerTransport;
    private TServerSocket syncServerTransport;
    private TServer server;
    private final ThriftConfiguration configuration;
    private final ThriftEndpoint endpoint;

    public ThriftConsumer(ThriftEndpoint endpoint, Processor processor, ThriftConfiguration configuration) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    public ThriftConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (server == null) {
            LOG.debug("Starting the Thrift server");
            initializeServer();
            server.serve();
            LOG.info("Thrift server started and listening on port: {}", asyncServerTransport == null ? syncServerTransport.getServerSocket().getLocalPort() : asyncServerTransport.getPort());
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (server != null) {
            LOG.debug("Terminating Thrift server");
            server.stop();
            if (ObjectHelper.isNotEmpty(asyncServerTransport)) {
                asyncServerTransport.close();
                asyncServerTransport = null;
            }
            if (ObjectHelper.isNotEmpty(syncServerTransport)) {
                syncServerTransport.close();
                syncServerTransport = null;
            }
            server.stop();
            server = null;
        }
        super.doStop();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void initializeServer() throws TTransportException, IOException {
        Class serverImplementationClass;
        Object serverImplementationInstance;
        Object serverProcessor;
        ProxyFactory serviceProxy = new ProxyFactory();
        MethodHandler methodHandler = new ThriftMethodHandler(endpoint, this);

        try {
            Class serverInterface = ThriftUtils.getServerInterface(endpoint.getServicePackage(), endpoint.getServiceName(), endpoint.isSynchronous(), endpoint.getCamelContext());
            serviceProxy.setInterfaces(new Class[] {serverInterface});
            serverImplementationClass = serviceProxy.createClass();
            serverImplementationInstance = serverImplementationClass.getConstructor().newInstance();
            ((Proxy)serverImplementationInstance).setHandler(methodHandler);

            serverProcessor = ThriftUtils.constructServerProcessor(endpoint.getServicePackage(), endpoint.getServiceName(), serverImplementationInstance, endpoint.isSynchronous(),
                                                                   endpoint.getCamelContext());
        } catch (IllegalArgumentException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            throw new IllegalArgumentException("Unable to create server implementation proxy service for " + configuration.getService());
        }

        if (configuration.getNegotiationType() == ThriftNegotiationType.SSL && endpoint.isSynchronous()) {
            ClassResolver classResolver = endpoint.getCamelContext().getClassResolver();
            SSLContextParameters sslParameters = configuration.getSslParameters();
            
            if (sslParameters == null) {
                throw new IllegalArgumentException("SSL parameters must be initialized if negotiation type is set to " + configuration.getNegotiationType());
            }

            ObjectHelper.notNull(sslParameters.getSecureSocketProtocol(), "Security protocol");
            ObjectHelper.notNull(sslParameters.getKeyManagers().getKeyStore().getResource(), "Keystore path");
            ObjectHelper.notNull(sslParameters.getKeyManagers().getKeyStore().getPassword(), "Keystore password");

            TSSLTransportFactory.TSSLTransportParameters sslParams;
            sslParams = new TSSLTransportFactory.TSSLTransportParameters(sslParameters.getSecureSocketProtocol(),
                                                     sslParameters.getCipherSuites() == null ? null
                                                     : sslParameters.getCipherSuites().getCipherSuite().stream().toArray(String[]::new));
            
            if (ObjectHelper.isNotEmpty(sslParameters.getKeyManagers().getKeyStore().getProvider()) && ObjectHelper.isNotEmpty(sslParameters.getKeyManagers().getKeyStore().getType())) {
                sslParams.setKeyStore(ResourceHelper.resolveResourceAsInputStream(classResolver, sslParameters.getKeyManagers().getKeyStore().getResource()),
                                      sslParameters.getKeyManagers().getKeyStore().getPassword(),
                                      sslParameters.getKeyManagers().getKeyStore().getProvider(),
                                      sslParameters.getKeyManagers().getKeyStore().getType());
            } else {
                sslParams.setKeyStore(ResourceHelper.resolveResourceAsInputStream(classResolver, sslParameters.getKeyManagers().getKeyStore().getResource()),
                                      sslParameters.getKeyManagers().getKeyStore().getPassword());
            }

            try {
                syncServerTransport = TSSLTransportFactory.getServerSocket(configuration.getPort(), configuration.getClientTimeout(), InetAddress.getByName(configuration.getHost()),
                                                                          sslParams);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Unknown host defined: " + configuration.getHost());
            }
            ThriftThreadPoolServer.Args args = new ThriftThreadPoolServer.Args(syncServerTransport);
            args.processor((TProcessor)serverProcessor);
            args.executorService(getEndpoint().getCamelContext().getExecutorServiceManager().newThreadPool(this, getEndpoint().getEndpointUri(), configuration.getPoolSize(),
                                                                                                           configuration.getMaxPoolSize()));
            args.startThreadPool(getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "start-" + getEndpoint().getEndpointUri()));
            args.context(endpoint.getCamelContext());

            server = new ThriftThreadPoolServer(args);
        } else if (configuration.getCompressionType() == ThriftCompressionType.ZLIB && endpoint.isSynchronous()) {
            if (ObjectHelper.isNotEmpty(configuration.getHost()) && ObjectHelper.isNotEmpty(configuration.getPort())) {
                LOG.debug("Building sync Thrift server on {}:{}", configuration.getHost(), configuration.getPort());
                syncServerTransport = new TServerSocket(new InetSocketAddress(configuration.getHost(), configuration.getPort()), configuration.getClientTimeout());
            } else if (ObjectHelper.isEmpty(configuration.getHost()) && ObjectHelper.isNotEmpty(configuration.getPort())) {
                LOG.debug("Building sync Thrift server on <any address>:{}", configuration.getPort());
                syncServerTransport = new TServerSocket(configuration.getPort(), configuration.getClientTimeout());
            } else {
                throw new IllegalArgumentException("No server start properties (host, port) specified");
            }
            
            ThriftThreadPoolServer.Args args = new ThriftThreadPoolServer.Args(syncServerTransport);
            args.processor((TProcessor)serverProcessor);
            args.transportFactory(new TZlibTransport.Factory());
            args.executorService(getEndpoint().getCamelContext().getExecutorServiceManager().newThreadPool(this, getEndpoint().getEndpointUri(), configuration.getPoolSize(),
                                                                                                           configuration.getMaxPoolSize()));
            args.startThreadPool(getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "start-" + getEndpoint().getEndpointUri()));
            args.context(endpoint.getCamelContext());
            
            server = new ThriftThreadPoolServer(args);
        } else {

            if (ObjectHelper.isNotEmpty(configuration.getHost()) && ObjectHelper.isNotEmpty(configuration.getPort())) {
                LOG.debug("Building Thrift server on {}:{}", configuration.getHost(), configuration.getPort());
                asyncServerTransport = new TNonblockingServerSocket(new InetSocketAddress(configuration.getHost(), configuration.getPort()), configuration.getClientTimeout());
            } else if (ObjectHelper.isEmpty(configuration.getHost()) && ObjectHelper.isNotEmpty(configuration.getPort())) {
                LOG.debug("Building Thrift server on <any address>:{}", configuration.getPort());
                asyncServerTransport = new TNonblockingServerSocket(configuration.getPort(), configuration.getClientTimeout());
            } else {
                throw new IllegalArgumentException("No server start properties (host, port) specified");
            }

            ThriftHsHaServer.Args args = new ThriftHsHaServer.Args(asyncServerTransport);
            args.processor((TProcessor)serverProcessor);
            args.executorService(getEndpoint().getCamelContext().getExecutorServiceManager().newThreadPool(this, getEndpoint().getEndpointUri(), configuration.getPoolSize(),
                                                                                                           configuration.getMaxPoolSize()));
            args.startThreadPool(getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "start-" + getEndpoint().getEndpointUri()));
            args.context(endpoint.getCamelContext());
            server = new ThriftHsHaServer(args);
        }
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        return doSend(exchange, callback);
    }

    private boolean doSend(Exchange exchange, AsyncCallback callback) {
        if (isRunAllowed()) {
            getAsyncProcessor().process(exchange, doneSync -> {
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
}
