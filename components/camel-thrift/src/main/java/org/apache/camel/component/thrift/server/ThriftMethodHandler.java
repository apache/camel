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
import org.apache.camel.Message;
import org.apache.camel.component.thrift.ThriftConstants;
import org.apache.camel.component.thrift.ThriftConsumer;
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
                            callback.onError(exception);
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
                        callback.onComplete(message.getBody(returnType));
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
                        throw exchange.getException();
                    }
                    responseBody = exchange.getIn().getBody(thisMethod.getReturnType());
                } finally {
                    consumer.releaseExchange(exchange, false);
                }

                if (responseBody == null && !thisMethod.getReturnType().equals(Void.TYPE)) {
                    throw new TApplicationException("Return type requires not empty body");
                }
                return responseBody;
            }

            return null;
        } else {
            return proceed.invoke(self, args);
        }
    }
}
