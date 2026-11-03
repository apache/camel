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
package org.apache.camel.component.grpc.server;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.grpc.GrpcConstants;
import org.apache.camel.component.grpc.GrpcConsumer;
import org.apache.camel.component.grpc.GrpcConsumerStrategy;
import org.apache.camel.component.grpc.GrpcEndpoint;
import org.apache.camel.component.grpc.GrpcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles gRPC service method invocations
 */
public class GrpcMethodHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcMethodHandler.class);

    private static final String MUTED_DESCRIPTION = "Exchange processing failed";

    protected final GrpcConsumer consumer;
    // the response message type of each method of the service, by method name (empty when unknown)
    private final Map<String, Optional<Class<?>>> responseTypes = new ConcurrentHashMap<>();

    public GrpcMethodHandler(GrpcConsumer consumer) {
        this.consumer = consumer;
    }

    /**
     * This method deals with the unary and server streaming gRPC calls
     *
     * @param  body             The request object sent by the gRPC client to the server
     * @param  responseObserver The response stream observer
     * @param  methodName       The name of the method invoked using the stub.
     * @throws Exception        java.lang.Exception
     */
    public void handle(Object body, StreamObserver<Object> responseObserver, String methodName) throws Exception {
        Map<String, Object> grcpHeaders = populateGrpcHeaders(methodName);
        GrpcEndpoint endpoint = (GrpcEndpoint) consumer.getEndpoint();

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(body);
        exchange.getIn().setHeaders(grcpHeaders);

        if (endpoint.getConfiguration().isRouteControlledStreamObserver()) {
            exchange.setProperty(GrpcConstants.GRPC_RESPONSE_OBSERVER, responseObserver);
            invokeRoute(endpoint, exchange);
            return;
        }

        invokeRoute(endpoint, exchange);

        if (exchange.isFailed()) {
            responseObserver.onError(toStatusException(endpoint, exchange.getException()));
        } else if (sendResponses(endpoint, exchange, exchange.getIn().getBody(), getResponseType(methodName),
                responseObserver)) {
            responseObserver.onCompleted();
        }
    }

    /**
     * Sends the reply of the route to the client: each element of a List body, or the body itself, converted to the
     * response type of the method. When a message is not of the response type and cannot be converted to it, nothing is
     * sent and the call ends with an error, as the client would otherwise decode the bytes of another message type (for
     * example the unchanged request) as the response.
     *
     * @param  endpoint         the endpoint
     * @param  exchange         the exchange of the reply
     * @param  body             the reply of the route
     * @param  responseType     the response message type of the method, or null when unknown to send the body as is
     * @param  responseObserver the response stream observer
     * @return                  true if the reply was sent, false if the call ended with an error
     */
    static boolean sendResponses(
            GrpcEndpoint endpoint, Exchange exchange, Object body, Class<?> responseType,
            StreamObserver<Object> responseObserver) {
        List<?> messages = body instanceof List<?> list ? list : Collections.singletonList(body);
        List<Object> responses = new ArrayList<>(messages.size());
        for (Object message : messages) {
            // no body sends an empty message
            if (message == null || responseType == null || responseType.isInstance(message)) {
                responses.add(message);
                continue;
            }
            try {
                responses.add(exchange.getContext().getTypeConverter().mandatoryConvertTo(responseType, exchange, message));
            } catch (NoTypeConversionAvailableException | TypeConversionException e) {
                String reason = "The reply of the gRPC method must be a " + responseType.getName()
                                + " or a List of them, but was a " + message.getClass().getName();
                IllegalArgumentException cause = new IllegalArgumentException(reason, e);
                LOG.warn("Cannot send the reply of exchange {}: {}", exchange.getExchangeId(), cause.getMessage());
                responseObserver.onError(toStatusException(endpoint, cause));
                return false;
            }
        }
        responses.forEach(responseObserver::onNext);
        return true;
    }

    /**
     * The response message type of the method of the service, from the generic type of its response StreamObserver
     * parameter.
     *
     * @param  methodName the name of the method
     * @return            the response type, or null when it cannot be determined
     */
    Class<?> getResponseType(String methodName) {
        return responseTypes.computeIfAbsent(methodName, this::findResponseType).orElse(null);
    }

    private Optional<Class<?>> findResponseType(String methodName) {
        GrpcEndpoint endpoint = (GrpcEndpoint) consumer.getEndpoint();
        Class<?> implBase;
        try {
            implBase = GrpcUtils.constructGrpcImplBaseClass(endpoint.getServicePackage(), endpoint.getServiceName(),
                    endpoint.getCamelContext());
        } catch (IllegalArgumentException e) {
            LOG.debug("Cannot determine the response type of the gRPC method {}: {}", methodName, e.getMessage());
            return Optional.empty();
        }
        for (Method method : implBase.getMethods()) {
            Type[] parameterTypes = method.getGenericParameterTypes();
            if (method.getName().equals(methodName) && parameterTypes.length > 0
                    && parameterTypes[parameterTypes.length - 1] instanceof ParameterizedType observerType
                    && observerType.getRawType() == StreamObserver.class
                    && observerType.getActualTypeArguments()[0] instanceof Class<?> responseType) {
                return Optional.of(responseType);
            }
        }
        return Optional.empty();
    }

    /**
     * The error to send to the client when the exchange failed.
     */
    static StatusRuntimeException toStatusException(GrpcEndpoint endpoint, Exception cause) {
        // the description IS transmitted to the client, so it carries the route's exception message only when
        // the endpoint opted out of muting; the cause below stays local either way
        String description = endpoint.getConfiguration().isMuteException() ? MUTED_DESCRIPTION : cause.getMessage();
        return Status.INTERNAL
                .withDescription(description)
                // This can be attached to the Status locally, but NOT transmitted to the client!
                .withCause(cause)
                .asRuntimeException();
    }

    private void invokeRoute(GrpcEndpoint endpoint, Exchange exchange) throws Exception {
        if (endpoint.getConfiguration().isSynchronous()) {
            consumer.getProcessor().process(exchange);
        } else {
            consumer.getAsyncProcessor().process(exchange);
        }
    }

    /**
     * This method deals with the client streaming and bi-directional streaming gRPC calls
     *
     * @param  responseObserver The response stream observer
     * @param  methodName       The name of the method invoked using the stub.
     * @return                  Request stream observer
     */
    public StreamObserver<Object> handleForConsumerStrategy(StreamObserver<Object> responseObserver, String methodName) {
        Map<String, Object> grcpHeaders = populateGrpcHeaders(methodName);
        GrpcEndpoint endpoint = (GrpcEndpoint) consumer.getEndpoint();
        StreamObserver<Object> requestObserver;

        if (consumer.getConfiguration().getConsumerStrategy() == GrpcConsumerStrategy.AGGREGATION) {
            requestObserver = new GrpcRequestAggregationStreamObserver(endpoint, consumer, responseObserver, grcpHeaders);
        } else if (consumer.getConfiguration().getConsumerStrategy() == GrpcConsumerStrategy.PROPAGATION) {
            requestObserver = new GrpcRequestPropagationStreamObserver(endpoint, consumer, responseObserver, grcpHeaders);
        } else if (consumer.getConfiguration().getConsumerStrategy() == GrpcConsumerStrategy.DELEGATION) {
            requestObserver = new GrpcRequestDelegationStreamObserver(endpoint, consumer, responseObserver, grcpHeaders);
        } else {
            throw new IllegalArgumentException(
                    "gRPC processing strategy not implemented " + consumer.getConfiguration().getConsumerStrategy());
        }
        if (requestObserver instanceof GrpcRequestAbstractStreamObserver observer) {
            observer.setResponseType(getResponseType(methodName));
        }

        return requestObserver;
    }

    private Map<String, Object> populateGrpcHeaders(String methodName) {
        Map<String, Object> grpcHeaders = new HashMap<>();
        grpcHeaders.put(GrpcHeaderInterceptor.USER_AGENT_CONTEXT_KEY.toString(),
                GrpcHeaderInterceptor.USER_AGENT_CONTEXT_KEY.get());
        grpcHeaders.put(GrpcHeaderInterceptor.CONTENT_TYPE_CONTEXT_KEY.toString(),
                GrpcHeaderInterceptor.CONTENT_TYPE_CONTEXT_KEY.get());
        grpcHeaders.put(GrpcConstants.GRPC_METHOD_NAME_HEADER, methodName);
        return grpcHeaders;
    }
}
