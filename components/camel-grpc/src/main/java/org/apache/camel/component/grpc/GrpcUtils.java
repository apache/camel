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
package org.apache.camel.component.grpc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import org.apache.camel.CamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;

/**
 * GrpcUtils helpers are working with dynamic methods via Camel and 
 * Java reflection utilities
 */
public final class GrpcUtils {

    private GrpcUtils() {
    }

    public static Object constructGrpcAsyncStub(String packageName, String serviceName, Channel channel, final CamelContext context) {
        return constructGrpcStubClass(packageName, serviceName, GrpcConstants.GRPC_SERVICE_ASYNC_STUB_METHOD, channel, context);
    }

    public static Object constructGrpcBlockingStub(String packageName, String serviceName, Channel channel, final CamelContext context) {
        return constructGrpcStubClass(packageName, serviceName, GrpcConstants.GRPC_SERVICE_SYNC_STUB_METHOD, channel, context);
    }

    /**
     * Get gRPC stub class instance depends on the invocation style
     * newBlockingStub - for sync style
     * newStub - for async style
     * newFutureStub - for ListenableFuture-style (not implemented yet)
     */
    @SuppressWarnings({"rawtypes"})
    private static Object constructGrpcStubClass(String packageName, String serviceName, String stubMethod, Channel channel, final CamelContext context) {
        Class[] paramChannel = new Class[1];
        paramChannel[0] = Channel.class;
        Object grpcBlockingStub = null;

        String serviceClassName = packageName + "." + serviceName + GrpcConstants.GRPC_SERVICE_CLASS_POSTFIX;
        try {
            Class grpcServiceClass = context.getClassResolver().resolveMandatoryClass(serviceClassName);
            Method grpcBlockingMethod = ReflectionHelper.findMethod(grpcServiceClass, stubMethod, paramChannel);
            if (grpcBlockingMethod == null) {
                throw new IllegalArgumentException("gRPC service method not found: " + serviceClassName + "." + stubMethod);
            }
            grpcBlockingStub = ObjectHelper.invokeMethod(grpcBlockingMethod, grpcServiceClass, channel);

        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("gRPC service class not found: " + serviceClassName);
        }
        return grpcBlockingStub;
    }

    @SuppressWarnings("rawtypes")
    public static Class constructGrpcImplBaseClass(String packageName, String serviceName, final CamelContext context) {
        Class grpcServerImpl;

        String serverBaseImpl = packageName + "." + serviceName + GrpcConstants.GRPC_SERVICE_CLASS_POSTFIX + "$" + serviceName + GrpcConstants.GRPC_SERVER_IMPL_POSTFIX;
        try {
            grpcServerImpl = context.getClassResolver().resolveMandatoryClass(serverBaseImpl);

        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("gRPC server base class not found: " + serverBaseImpl);
        }
        return grpcServerImpl;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void invokeAsyncMethod(Object asyncStubClass, String invokeMethod, Object request, StreamObserver responseObserver) {
        Class[] paramMethod = null;

        Method method = ReflectionHelper.findMethod(asyncStubClass.getClass(), invokeMethod, paramMethod);
        if (method == null) {
            throw new IllegalArgumentException("gRPC service method not found: " + asyncStubClass.getClass().getName() + "." + invokeMethod);
        }
        if (method.getReturnType().equals(StreamObserver.class)) {
            StreamObserver<Object> requestObserver = (StreamObserver<Object>)ObjectHelper.invokeMethod(method, asyncStubClass, responseObserver);
            if (request instanceof List) {
                List<Object> requestList = (List<Object>)request;
                requestList.forEach((requestItem) -> {
                    requestObserver.onNext(requestItem);
                });
            } else {
                requestObserver.onNext(request);
            }
            requestObserver.onCompleted();
        } else {
            ObjectHelper.invokeMethod(method, asyncStubClass, request, responseObserver);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static StreamObserver<Object> invokeAsyncMethodStreaming(Object asyncStubClass, String invokeMethod, StreamObserver<?> responseObserver) {
        Class[] paramMethod = null;
        Method method = ReflectionHelper.findMethod(asyncStubClass.getClass(), invokeMethod, paramMethod);
        if (method == null) {
            throw new IllegalArgumentException("gRPC service method not found: " + asyncStubClass.getClass().getName() + "." + invokeMethod);
        }
        if (!StreamObserver.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalArgumentException("gRPC service method does not declare an input of type stream (cannot be used in streaming mode): "
                    + asyncStubClass.getClass().getName() + "." + invokeMethod);
        }

        return  (StreamObserver<Object>) ObjectHelper.invokeMethod(method, asyncStubClass, responseObserver);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object invokeSyncMethod(Object blockingStubClass, String invokeMethod, Object request) {
        Class[] paramMethod = null;

        Method method = ReflectionHelper.findMethod(blockingStubClass.getClass(), invokeMethod, paramMethod);
        if (method == null) {
            throw new IllegalArgumentException("gRPC service method not found: " + blockingStubClass.getClass().getName() + "." + invokeMethod);
        }
        if (method.getReturnType().equals(Iterator.class)) {
            Iterator<Object> responseObjects = (Iterator<Object>)ObjectHelper.invokeMethod(method, blockingStubClass, request);
            List<Object> objectList = new ArrayList<Object>();
            while (responseObjects.hasNext()) {
                objectList.add(responseObjects.next());
            }
            return objectList;
        } else {
            return ObjectHelper.invokeMethod(method, blockingStubClass, request);
        }
    }

    /**
     * Migrated MixedLower function from the gRPC converting plugin source code
     * (https://github.com/grpc/grpc-java/blob/master/compiler/src/java_plugin/cpp/java_generator.cpp)
     *
     * - decapitalize the first letter
     * - remove embedded underscores & capitalize the following letter
     */
    public static String convertMethod2CamelCase(final String method) {
        StringBuilder sb = new StringBuilder(method.length());
        sb.append(method.substring(0, 1).toLowerCase());
        Boolean afterUnderscore = false;
        for (int i = 1; i < method.length(); i++) {
            if (method.charAt(i) == '_') {
                afterUnderscore = true;
            } else {
                sb.append(afterUnderscore ? Character.toUpperCase(method.charAt(i)) : method.charAt(i));
                afterUnderscore = false;
            }
        }
        return sb.toString();
    }
}
