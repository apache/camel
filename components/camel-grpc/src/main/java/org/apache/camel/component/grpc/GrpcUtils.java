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

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import org.springframework.util.ReflectionUtils;

/**
 * GrpcUtils helpers are working with dynamic methods via Spring reflection
 * utilities
 */
public final class GrpcUtils {

    private GrpcUtils() {
    }

    public static Object constructGrpcAsyncStub(String packageName, String serviceName, Channel channel) {
        return constructGrpcStubClass(packageName, serviceName, GrpcConstants.GRPC_SERVICE_ASYNC_STUB_METHOD, channel);
    }

    public static Object constructGrpcBlockingStub(String packageName, String serviceName, Channel channel) {
        return constructGrpcStubClass(packageName, serviceName, GrpcConstants.GRPC_SERVICE_SYNC_STUB_METHOD, channel);
    }

    /**
     * Get gRPC stub class instance depends on the invocation style
     * newBlockingStub - for sync style
     * newStub - for async style
     * newFutureStub - for ListenableFuture-style (not implemented yet)
     */
    @SuppressWarnings({"rawtypes"})
    private static Object constructGrpcStubClass(String packageName, String serviceName, String stubMethod, Channel channel) {
        Class[] paramChannel = new Class[1];
        paramChannel[0] = Channel.class;
        Object grpcBlockingStub = null;

        String serviceClassName = packageName + "." + serviceName + GrpcConstants.GRPC_SERVICE_CLASS_PREFIX;
        try {
            Class grpcServiceClass = Class.forName(serviceClassName);
            Method grpcBlockingMethod = ReflectionUtils.findMethod(grpcServiceClass, stubMethod, paramChannel);
            if (grpcBlockingMethod == null) {
                throw new IllegalArgumentException("gRPC service method not found: " + serviceClassName + "." + GrpcConstants.GRPC_SERVICE_SYNC_STUB_METHOD);
            }
            grpcBlockingStub = ReflectionUtils.invokeMethod(grpcBlockingMethod, grpcServiceClass, channel);

        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("gRPC service class not found: " + serviceClassName);
        }
        return grpcBlockingStub;
    }

    @SuppressWarnings("rawtypes")
    public static void invokeAsyncMethod(Object asyncStubClass, String invokeMethod, Object request, StreamObserver asyncHandler) {
        Class[] paramMethod = new Class[2];
        paramMethod[0] = request.getClass();
        paramMethod[1] = StreamObserver.class;

        Method method = ReflectionUtils.findMethod(asyncStubClass.getClass(), invokeMethod, paramMethod);
        if (method == null) {
            throw new IllegalArgumentException("gRPC service method not found: " + invokeMethod + " with parameter: " + request.getClass().getName());
        }
        ReflectionUtils.invokeMethod(method, asyncStubClass, request, asyncHandler);
    }

    @SuppressWarnings("rawtypes")
    public static Object invokeSyncMethod(Object blockingStubClass, String invokeMethod, Object request) {
        Class[] paramMethod = new Class[1];
        paramMethod[0] = request.getClass();
        Object responseObject = null;

        Method method = ReflectionUtils.findMethod(blockingStubClass.getClass(), invokeMethod, paramMethod);
        if (method == null) {
            throw new IllegalArgumentException("gRPC service method not found: " + invokeMethod + " with parameter: " + request.getClass().getName());
        }
        responseObject = ReflectionUtils.invokeMethod(method, blockingStubClass, request);
        return responseObject;
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
