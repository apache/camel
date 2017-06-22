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
package org.apache.camel.component.thrift;

import java.io.IOException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.thrift.client.AsyncClientMethodCallback;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents asynchronous and synchronous Thrift producer implementations
 */
public class ThriftProducer extends DefaultProducer implements AsyncProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ThriftProducer.class);

    protected final ThriftConfiguration configuration;
    protected final ThriftEndpoint endpoint;
    private TProtocol protocol;
    private TTransport syncTransport;
    private TNonblockingTransport asyncTransport;
    private Object thriftClient;

    public ThriftProducer(ThriftEndpoint endpoint, ThriftConfiguration configuration) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message message = exchange.getIn();

        try {
            ThriftUtils.invokeAsyncMethod(thriftClient, configuration.getMethod(), message.getBody(), new AsyncClientMethodCallback(exchange, callback));
        } catch (Exception e) {
            if (e.getCause() instanceof TException) {
                exchange.setException(e.getCause());
            } else {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }

        return false;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getIn();
        try {
            Object outBody = ThriftUtils.invokeSyncMethod(thriftClient, configuration.getMethod(), message.getBody());
            exchange.getOut().setBody(outBody);
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
        if (endpoint.isSynchronous()) {
            if (syncTransport == null) {
                initializeSyncTransport();
                LOG.info("Getting synchronous client implementation");
                thriftClient = ThriftUtils.constructClientInstance(endpoint.getServicePackage(), endpoint.getServiceName(), protocol, endpoint.getCamelContext());
            }
        } else {
            if (asyncTransport == null) {
                initializeAsyncTransport();
                LOG.info("Getting asynchronous client implementation");
                thriftClient = ThriftUtils.constructAsyncClientInstance(endpoint.getServicePackage(), endpoint.getServiceName(), asyncTransport, endpoint.getCamelContext());
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (syncTransport != null) {
            LOG.debug("Terminating synchronous transport the remote Thrift server");
            syncTransport.close();
            syncTransport = null;
            protocol = null;
        } else if (asyncTransport != null) {
            LOG.debug("Terminating asynchronous transport the remote Thrift server");
            asyncTransport.close();
            asyncTransport = null;
        }
        super.doStop();
    }
    
    protected void initializeSyncTransport() throws TTransportException {
        if (!ObjectHelper.isEmpty(configuration.getHost()) && !ObjectHelper.isEmpty(configuration.getPort())) {
            LOG.info("Creating transport to the remote Thrift server {}:{}", configuration.getHost(), configuration.getPort());
            syncTransport = new TSocket(configuration.getHost(), configuration.getPort());
        } else {
            throw new IllegalArgumentException("No connection properties (host, port) specified");
        }
        syncTransport.open();
        protocol = new TBinaryProtocol(new TFramedTransport(syncTransport));
    }
    
    protected void initializeAsyncTransport() throws IOException, TTransportException {
        if (!ObjectHelper.isEmpty(configuration.getHost()) && !ObjectHelper.isEmpty(configuration.getPort())) {
            LOG.info("Creating transport to the remote Thrift server {}:{}", configuration.getHost(), configuration.getPort());
            asyncTransport = new TNonblockingSocket(configuration.getHost(), configuration.getPort());
        } else {
            throw new IllegalArgumentException("No connection properties (host, port) specified");
        }
    }
    
}
