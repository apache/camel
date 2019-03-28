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

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.internal.GrpcUtil;
import org.apache.camel.Exchange;
import org.apache.camel.component.grpc.GrpcConstants;

/**
 * gRPC server header interceptor
 */
public class GrpcHeaderInterceptor implements ServerInterceptor {
    public static final Context.Key<String> USER_AGENT_CONTEXT_KEY = Context.key(GrpcConstants.GRPC_USER_AGENT_HEADER);
    public static final Context.Key<String> CONTENT_TYPE_CONTEXT_KEY = Context.key(Exchange.CONTENT_TYPE);

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata requestHeaders, ServerCallHandler<ReqT, RespT> next) {
        Context context = Context.current()
            .withValue(USER_AGENT_CONTEXT_KEY, requestHeaders.get(GrpcUtil.USER_AGENT_KEY))
            .withValue(CONTENT_TYPE_CONTEXT_KEY, requestHeaders.get(GrpcUtil.CONTENT_TYPE_KEY));

        return Contexts.interceptCall(context, call, requestHeaders, next);
    }
}
