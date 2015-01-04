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
package org.apache.camel.component.avro;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.apache.avro.ipc.HttpServer;
import org.apache.avro.ipc.NettyServer;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.apache.avro.specific.SpecificData;
import org.apache.camel.Exchange;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.lang.StringUtils;
import org.mortbay.log.Log;

import static org.apache.camel.component.avro.AvroConstants.AVRO_HTTP_TRANSPORT;
import static org.apache.camel.component.avro.AvroConstants.AVRO_NETTY_TRANSPORT;


/**
 * This class holds server that listen to given protocol:host:port combination and dispatches messages to
 * different routes mapped.
 */
public class AvroListener {

    private ConcurrentMap<String, AvroConsumer> consumerRegistry = new ConcurrentHashMap<String, AvroConsumer>();
    private AvroConsumer defaultConsumer;
    private final Server server;

    public AvroListener(AvroEndpoint endpoint)  throws Exception {
        server = initAndStartServer(endpoint.getConfiguration());
    }

    /**
     * Initializes and starts http or netty server on basis of transport protocol from configuration.
     *
     *
     * @param configuration
     * @return Initialized and started server
     * @throws java.io.IOException
     */
    private Server initAndStartServer(AvroConfiguration configuration) throws Exception {
        SpecificResponder responder;
        Server server;

        if (configuration.isReflectionProtocol()) {
            responder = new AvroReflectResponder(configuration.getProtocol(), this);
        } else {
            responder = new AvroSpecificResponder(configuration.getProtocol(), this);
        }


        if (AVRO_HTTP_TRANSPORT.equalsIgnoreCase(configuration.getTransport().name())) {
            server = new HttpServer(responder, configuration.getPort());
        } else if (AVRO_NETTY_TRANSPORT.equalsIgnoreCase(configuration.getTransport().name())) {
            server = new NettyServer(responder, new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        } else {
            throw new IllegalArgumentException("Unknown transport " + configuration.getTransport());
        }

        server.start();

        return server;
    }

    /**
     * Registers consumer by appropriate message name as key in registry.
     *
     * @param messageName message name
     * @param consumer avro consumer
     * @throws AvroComponentException
     */
    public void register(String messageName, AvroConsumer consumer) throws AvroComponentException {
        if (messageName == null) {
            if (this.defaultConsumer != null) {
                throw new AvroComponentException("Default consumer already registered for uri: " + consumer.getEndpoint().getEndpointUri());
            }    
            this.defaultConsumer = consumer;
        } else {
            if (consumerRegistry.putIfAbsent(messageName, consumer) != null) {
                throw new AvroComponentException("Consumer already registered for message: " + messageName + " and uri: " + consumer.getEndpoint().getEndpointUri());
            }
        }
    }

    /**
     * Unregisters consumer by message name.
     * Stops server in case if all consumers are unregistered and default consumer is absent or stopped.
     *
     * @param messageName message name
     * @return true if all consumers are unregistered and defaultConsumer is absent or null.
     *         It means that this responder can be unregistered.
     */
    public boolean unregister(String messageName) {
        if (!StringUtils.isEmpty(messageName)) {
            if (consumerRegistry.remove(messageName) == null) {
                Log.warn("Consumer with message name " + messageName + " was already unregistered.");
            }
        } else {
            defaultConsumer = null;
        }

        if ((defaultConsumer == null) && (consumerRegistry.isEmpty())) {
            if (server != null) {
                server.close();
            }
            return true;
        }
        return false;
    }

    public Object respond(Protocol.Message message, Object request, SpecificData data) throws Exception {
        AvroConsumer consumer = this.defaultConsumer;
        if (this.consumerRegistry.containsKey(message.getName())) {
            consumer = this.consumerRegistry.get(message.getName());
        }

        if (consumer == null) {
            throw new AvroComponentException("No consumer defined for message: " + message.getName());
        }

        Object params = extractParams(message, request, consumer.getEndpoint().getConfiguration().isSingleParameter(), data);

        return processExchange(consumer, message, params);
    }

    /**
     * Extracts parameters from RPC call to List or converts to object of appropriate type
     * if only one parameter set.
     *
     * @param message Avro message
     * @param request Avro request
     * @param singleParameter Indicates that called method has single parameter
     * @param dataResolver Extracts type of parameters in call
     * @return Parameters of RPC method invocation
     */
    private static Object extractParams(Protocol.Message message, Object request, boolean singleParameter, SpecificData dataResolver) {

        if (singleParameter) {
            Schema.Field field = message.getRequest().getFields().get(0);
            return dataResolver.getField(request, field.name(), field.pos());
        } else {
            int i = 0;
            Object[] params =  new Object[message.getRequest().getFields().size()];
            for (Schema.Field param : message.getRequest().getFields()) {
                params[i] = dataResolver.getField(request, param.name(), param.pos());
                i++;
            }
            return params;
        }
    }

    /**
     * Creates exchange and processes it.
     *
     * @param consumer Holds processor and exception handler
     * @param message Message on which exchange is created
     * @param params Params of exchange
     * @return Response of exchange processing
     * @throws Exception
     */
    private static Object processExchange(AvroConsumer consumer, Protocol.Message message, Object params) throws Exception {
        Object response;
        Exchange exchange = consumer.getEndpoint().createExchange(message, params);

        try {
            consumer.getProcessor().process(exchange);
        } catch (Throwable e) {
            consumer.getExceptionHandler().handleException(e);
        }

        if (ExchangeHelper.isOutCapable(exchange)) {
            response = exchange.getOut().getBody();
        } else {
            response = null;
        }

        boolean failed = exchange.isFailed();
        if (failed) {
            if (exchange.getException() != null) {
                throw exchange.getException();
            } else {
                // failed and no exception, must be a fault
                throw new AvroComponentException("Camel processing error.");
            }
        }
        return response;
    }
}
