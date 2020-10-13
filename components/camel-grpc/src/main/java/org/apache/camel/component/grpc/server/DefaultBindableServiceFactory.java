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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import org.apache.camel.component.grpc.GrpcConsumer;
import org.apache.camel.component.grpc.GrpcEndpoint;
import org.apache.camel.component.grpc.GrpcUtils;

/**
 * BindableServiceFactory implementation that uses Javassist to generate & proxy gRPC service classes
 */
public class DefaultBindableServiceFactory implements BindableServiceFactory {

    @Override
    public BindableService createBindableService(GrpcConsumer consumer) {
        GrpcEndpoint endpoint = (GrpcEndpoint) consumer.getEndpoint();
        ProxyFactory serviceProxy = new ProxyFactory();
        MethodHandler methodHandler = new MethodHandler() {
            private final GrpcMethodHandler grpcMethodHandler = new GrpcMethodHandler(consumer);

            @Override
            public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
                // Determines that the incoming parameters are transmitted in synchronous mode
                // Two incoming parameters and second is instance of the io.grpc.stub.StreamObserver
                if (args.length == 2 && args[1] instanceof StreamObserver) {
                    grpcMethodHandler.handle(args[0], (StreamObserver<Object>) args[1], thisMethod.getName());
                } else if (args.length == 1 && args[0] instanceof StreamObserver) {
                    // Single incoming parameter is instance of the io.grpc.stub.StreamObserver
                    return grpcMethodHandler.handleForConsumerStrategy((StreamObserver<Object>) args[0], thisMethod.getName());
                } else {
                    throw new IllegalArgumentException("Invalid to process gRPC method: " + thisMethod.getName());
                }
                return null;
            }
        };
        serviceProxy.setSuperclass(GrpcUtils.constructGrpcImplBaseClass(endpoint.getServicePackage(), endpoint.getServiceName(),
                endpoint.getCamelContext()));
        try {
            return (BindableService) serviceProxy.create(new Class<?>[0], new Object[0], methodHandler);
        } catch (NoSuchMethodException | IllegalArgumentException | InstantiationException | IllegalAccessException
                 | InvocationTargetException e) {
            throw new IllegalArgumentException(
                    "Unable to create bindable proxy service for " + endpoint.getConfiguration().getService());
        }
    }
}
