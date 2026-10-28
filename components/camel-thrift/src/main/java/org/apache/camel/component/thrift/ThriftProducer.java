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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.thrift.client.AsyncClientMethodCallback;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.apache.thrift.TException;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents asynchronous and synchronous Thrift producer implementations
 */
public class ThriftProducer extends DefaultAsyncProducer implements AsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ThriftProducer.class);

    protected final ThriftConfiguration configuration;
    protected final ThriftEndpoint endpoint;
    private TTransport syncTransport;
    private TNonblockingTransport asyncTransport;
    private TAsyncClientManager asyncClientManager;
    private volatile Object thriftClient;
    // the callbacks of the asynchronous calls that have not completed yet, failed when this producer stops
    private final Set<PendingCallback> pendingCallbacks = ConcurrentHashMap.newKeySet();

    public ThriftProducer(ThriftEndpoint endpoint, ThriftConfiguration configuration) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message message = exchange.getIn();

        PendingCallback pending = new PendingCallback(exchange, callback);
        pendingCallbacks.add(pending);
        // read after the registration: doStop clears the client before it fails the pending calls
        Object client = thriftClient;
        if (client == null) {
            // the producer is stopped
            pending.stopped();
            return false;
        }
        try {
            ThriftUtils.invokeAsyncMethod(client, configuration.getMethod(), message.getBody(), pending);
        } catch (Exception e) {
            // the call was not made
            if (!pending.discard()) {
                // a stop of the producer completed the exchange meanwhile, with done(false)
                return false;
            }
            if (e.getCause() instanceof TException) {
                exchange.setException(e.getCause());
            } else {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }
        if (client != thriftClient) {
            // the producer stopped meanwhile, and may have started again with a new client: the stop may have failed
            // the pending calls before this one was registered, and the selector thread of the stopped client manager,
            // which may still be ending, accepts the call without ever completing it
            pending.stopped();
        }

        return false;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getIn();
        try {
            Object outBody = ThriftUtils.invokeSyncMethod(thriftClient, configuration.getMethod(), message.getBody());
            exchange.getMessage().setBody(outBody);
        } catch (Exception e) {
            if (e.getCause() instanceof TException) {
                exchange.setException(e.getCause());
            } else {
                throw new Exception(e);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (configuration.getNegotiationType() == ThriftNegotiationType.SASL) {
            throw new IllegalArgumentException("The SASL negotiation type is not supported");
        }
        if (configuration.getNegotiationType() == ThriftNegotiationType.SSL) {
            if (!endpoint.isSynchronous()) {
                throw new IllegalArgumentException("The SSL negotiation type requires to set syncronous communication mode");
            }

            if (syncTransport == null) {
                initializeSslTransport();
                LOG.info("Getting synchronous secured client implementation");
                thriftClient = ThriftUtils.constructClientInstance(endpoint.getServicePackage(), endpoint.getServiceName(),
                        syncTransport, configuration.getExchangeProtocol(),
                        configuration.getNegotiationType(), configuration.getCompressionType(),
                        endpoint.getCamelContext());
            }
        } else if (endpoint.isSynchronous()) {
            if (syncTransport == null) {
                initializeSyncTransport();
                LOG.info("Getting synchronous client implementation");
                thriftClient = ThriftUtils.constructClientInstance(endpoint.getServicePackage(), endpoint.getServiceName(),
                        syncTransport, configuration.getExchangeProtocol(),
                        configuration.getNegotiationType(), configuration.getCompressionType(),
                        endpoint.getCamelContext());
            }
        } else {
            if (asyncTransport == null) {
                initializeAsyncTransport();
                LOG.info("Getting asynchronous client implementation");
                asyncClientManager = new TAsyncClientManager();
                thriftClient = ThriftUtils.constructAsyncClientInstance(endpoint.getServicePackage(), endpoint.getServiceName(),
                        asyncTransport,
                        configuration.getExchangeProtocol(), endpoint.getCamelContext(), asyncClientManager);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        try {
            if (syncTransport != null) {
                LOG.debug("Terminating synchronous transport the remote Thrift server");
                syncTransport.close();
                syncTransport = null;
            } else if (asyncTransport != null) {
                // before the pending calls are failed: process() fails a call whose client was cleared or replaced
                thriftClient = null;
                LOG.debug("Terminating asynchronous transport the remote Thrift server");
                asyncTransport.close();
                asyncTransport = null;
            }
        } finally {
            if (asyncClientManager != null) {
                // stops the selector thread and closes the selector of the async client
                asyncClientManager.stop();
                asyncClientManager = null;
            }
            // after the stop, so that a call registered meanwhile is failed too
            failPendingCallbacks();
        }
        super.doStop();
    }

    /**
     * Fails the exchanges of the asynchronous calls that have not completed: the stopped selector thread does not
     * complete them. A call that completes meanwhile is completed once, and a continuation that throws does not skip
     * the other calls.
     */
    private void failPendingCallbacks() {
        for (PendingCallback pending : pendingCallbacks) {
            try {
                pending.stopped();
            } catch (Exception e) {
                LOG.warn("Error completing the exchange of a pending call of method {} when stopping the Thrift producer",
                        configuration.getMethod(), e);
            }
        }
    }

    /**
     * The number of asynchronous calls that have not completed yet (for tests).
     */
    int getPendingCallbackCount() {
        return pendingCallbacks.size();
    }

    /**
     * Completes the exchange of an asynchronous call once: with the response or error of the call, or with an error
     * when the producer stops first.
     */
    private final class PendingCallback extends AsyncClientMethodCallback {
        private final Exchange exchange;
        private final AtomicBoolean done = new AtomicBoolean();

        PendingCallback(Exchange exchange, AsyncCallback callback) {
            super(exchange, callback);
            this.exchange = exchange;
        }

        @Override
        public void onComplete(Object response) {
            if (finish()) {
                super.onComplete(response);
            }
        }

        @Override
        public void onError(Exception exception) {
            if (finish()) {
                super.onError(exception);
            }
        }

        void stopped() {
            if (finish()) {
                super.onError(new CamelExchangeException(
                        "The Thrift producer was stopped before the call of method " + configuration.getMethod()
                                                         + " completed",
                        exchange));
            }
        }

        boolean discard() {
            return finish();
        }

        private boolean finish() {
            if (done.compareAndSet(false, true)) {
                pendingCallbacks.remove(this);
                return true;
            }
            return false;
        }
    }

    protected void initializeSyncTransport() throws TTransportException {
        if (!ObjectHelper.isEmpty(configuration.getHost()) && !ObjectHelper.isEmpty(configuration.getPort())) {
            LOG.info("Creating transport to the remote Thrift server {}:{}", configuration.getHost(), configuration.getPort());
            syncTransport = new TSocket(configuration.getHost(), configuration.getPort());
        } else {
            throw new IllegalArgumentException("No connection properties (host, port) specified");
        }
        syncTransport.open();
    }

    protected void initializeAsyncTransport() throws IOException, TTransportException {
        if (!ObjectHelper.isEmpty(configuration.getHost()) && !ObjectHelper.isEmpty(configuration.getPort())) {
            LOG.info("Creating transport to the remote Thrift server {}:{}", configuration.getHost(), configuration.getPort());
            asyncTransport = new TNonblockingSocket(configuration.getHost(), configuration.getPort());
        } else {
            throw new IllegalArgumentException("No connection properties (host, port) specified");
        }
    }

    protected void initializeSslTransport() throws TTransportException, IOException {
        if (!ObjectHelper.isEmpty(configuration.getHost()) && !ObjectHelper.isEmpty(configuration.getPort())) {
            SSLContextParameters sslParameters = configuration.getSslParameters();
            if (sslParameters == null) {
                throw new IllegalArgumentException(
                        "SSL parameters must be initialized if negotiation type is set to "
                                                   + configuration.getNegotiationType());
            }

            ObjectHelper.notNull(sslParameters.getSecureSocketProtocol(), "Security protocol");
            ObjectHelper.notNull(sslParameters.getTrustManagers().getKeyStore().getResource(), "Trust store path");
            ObjectHelper.notNull(sslParameters.getTrustManagers().getKeyStore().getPassword(), "Trust store password");

            LOG.info("Creating secured transport to the remote Thrift server {}:{}", configuration.getHost(),
                    configuration.getPort());

            TSSLTransportFactory.TSSLTransportParameters sslParams;

            sslParams = new TSSLTransportFactory.TSSLTransportParameters(
                    sslParameters.getSecureSocketProtocol(),
                    sslParameters.getCipherSuites() == null
                            ? null
                            : sslParameters.getCipherSuites().getCipherSuite().stream().toArray(String[]::new));

            if (ObjectHelper.isNotEmpty(sslParameters.getTrustManagers().getProvider())
                    && ObjectHelper.isNotEmpty(sslParameters.getTrustManagers().getKeyStore().getType())) {
                sslParams.setTrustStore(
                        ResourceHelper.resolveResourceAsInputStream(endpoint.getCamelContext(),
                                sslParameters.getTrustManagers().getKeyStore().getResource()),
                        sslParameters.getTrustManagers().getKeyStore().getPassword(),
                        sslParameters.getTrustManagers().getProvider(),
                        sslParameters.getTrustManagers().getKeyStore().getType());
            } else {
                sslParams.setTrustStore(sslParameters.getTrustManagers().getKeyStore().getResource(),
                        sslParameters.getTrustManagers().getKeyStore().getPassword());
            }

            syncTransport = TSSLTransportFactory.getClientSocket(configuration.getHost(), configuration.getPort(),
                    configuration.getClientTimeout(), sslParams);
        } else {
            throw new IllegalArgumentException("No connection properties (host, port) specified");
        }
    }
}
