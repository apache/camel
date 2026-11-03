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
package org.apache.camel.component.thrift.server;

import java.lang.reflect.Method;
import java.util.Arrays;

import javassist.util.proxy.MethodHandler;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.thrift.ThriftConstants;
import org.apache.camel.component.thrift.ThriftConsumer;
import org.apache.camel.component.thrift.ThriftEndpoint;
import org.apache.camel.component.thrift.ThriftUtils;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

/**
 * Thrift server methods invocation handler
 */
public class ThriftMethodHandler implements MethodHandler {
    private final ThriftConsumer consumer;

    public ThriftMethodHandler(ThriftConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        if (proceed == null) {
            // Detects async methods invocation as a last argument is instance of
            // {org.apache.thrift.async.AsyncMethodCallback}
            if (args.length > 0 && args[args.length - 1] instanceof AsyncMethodCallback) {
                AsyncMethodCallback callback = (AsyncMethodCallback) args[args.length - 1];
                Exchange exchange = consumer.createExchange(false);
                if (args.length >= 2) {
                    exchange.getIn().setBody(Arrays.asList(Arrays.copyOfRange(args, 0, args.length - 1)));
                } else {
                    exchange.getIn().setBody(null);
                }
                exchange.getIn().setHeader(ThriftConstants.THRIFT_METHOD_NAME_HEADER, thisMethod.getName());

                consumer.process(exchange, doneSync -> {
                    // complete the call exactly once: with the error of the exchange, or with its response
                    try {
                        Exception exception = exchange.getException();
                        if (exception != null) {
                            callback.onError(toClientException(thisMethod, exception));
                            return;
                        }

                        Message message = exchange.getMessage();
                        if (message == null) {
                            callback.onError(new TException("Unable process null message"));
                            return;
                        }

                        Class returnType = ThriftUtils.findMethodReturnType(args[args.length - 1].getClass(), "onComplete");
                        if (returnType == null) {
                            callback.onError(new TException("Unable to detect method return type"));
                            return;
                        }
                        Object reply;
                        try {
                            reply = toReply(thisMethod, message, returnType);
                        } catch (TApplicationException e) {
                            callback.onError(e);
                            return;
                        }
                        callback.onComplete(reply);
                    } finally {
                        consumer.releaseExchange(exchange, false);
                    }
                });
            } else {
                Object responseBody = null;
                Exchange exchange = consumer.createExchange(false);
                try {
                    exchange.getIn().setBody(Arrays.asList(args));
                    exchange.getIn().setHeader(ThriftConstants.THRIFT_METHOD_NAME_HEADER, thisMethod.getName());

                    consumer.getProcessor().process(exchange);
                    if (exchange.getException() != null) {
                        // let the server send the error to the client (a declared exception as such)
                        throw toClientException(thisMethod, exchange.getException());
                    }
                    responseBody = toReply(thisMethod, exchange.getMessage(), thisMethod.getReturnType());
                } finally {
                    consumer.releaseExchange(exchange, false);
                }
                return responseBody;
            }

            return null;
        } else {
            return proceed.invoke(self, args);
        }
    }

    /**
     * The reply of the exchange converted to the return type of the method. A void method has no reply; for any other
     * method a reply that is null, or that does not convert to the return type, fails the call: a primitive return type
     * must not get its default value, and the asynchronous server must not send a null result.
     */
    private static Object toReply(Method method, Message message, Class<?> returnType) throws TApplicationException {
        if (returnType == Void.TYPE || returnType == Void.class) {
            return null;
        }
        if (message.getBody() == null) {
            throw new TApplicationException(TApplicationException.MISSING_RESULT, "Return type requires not empty body");
        }
        try {
            return message.getMandatoryBody(returnType);
        } catch (InvalidPayloadException e) {
            TApplicationException answer = new TApplicationException(
                    TApplicationException.INTERNAL_ERROR,
                    "The reply of method " + method.getName() + " cannot be converted to its return type "
                                                          + returnType.getName());
            answer.initCause(e);
            throw answer;
        }
    }

    /**
     * The error to send to the client for a failed exchange. An exception that the Thrift method declares belongs to
     * the service contract and is sent as it is; any other one is replaced by a generic error unless muteException is
     * disabled.
     */
    private Exception toClientException(Method method, Exception exception) {
        if (!consumer.getConfiguration().isMuteException() || isDeclared(method, exception)) {
            return exception;
        }
        return new TApplicationException(
                TApplicationException.INTERNAL_ERROR, "Internal error processing " + method.getName());
    }

    private boolean isDeclared(Method method, Exception exception) {
        // the methods of the asynchronous interface only declare TException, so read the exceptions of the IDL from
        // the method of the synchronous interface (a Thrift service has no overloaded methods)
        ThriftEndpoint endpoint = (ThriftEndpoint) consumer.getEndpoint();
        Class<?> serverInterface = ThriftUtils.getServerInterface(endpoint.getServicePackage(), endpoint.getServiceName(),
                true, endpoint.getCamelContext());
        for (Method declaring : serverInterface.getMethods()) {
            if (declaring.getName().equals(method.getName())) {
                for (Class<?> declared : declaring.getExceptionTypes()) {
                    if (declared != TException.class && declared.isInstance(exception)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
