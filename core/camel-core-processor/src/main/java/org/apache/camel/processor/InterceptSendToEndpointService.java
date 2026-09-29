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
package org.apache.camel.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.support.DefaultInterceptSendToEndpoint.Interceptor;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;

/**
 * A route service for the intercept send to endpoint EIP, which registers the interceptor of the route when the route
 * starts, and unregisters it when the route stops (or is removed).
 */
public class InterceptSendToEndpointService extends ServiceSupport {

    private final CamelContext camelContext;
    private final String matchUri;
    private final Interceptor interceptor;

    public InterceptSendToEndpointService(CamelContext camelContext, String matchUri, Interceptor interceptor) {
        this.camelContext = camelContext;
        this.matchUri = matchUri;
        this.interceptor = interceptor;
    }

    public Interceptor getInterceptor() {
        return interceptor;
    }

    @Override
    protected void doBuild() throws Exception {
        ServiceHelper.buildService(interceptor.before(), interceptor.after());
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(interceptor.before(), interceptor.after());
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(interceptor.before(), interceptor.after());
        InterceptSendToEndpointManager.getOrCreate(camelContext).register(matchUri, interceptor);
    }

    @Override
    protected void doStop() throws Exception {
        InterceptSendToEndpointManager.getOrCreate(camelContext).unregister(interceptor);
        ServiceHelper.stopService(interceptor.before(), interceptor.after());
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(interceptor.before(), interceptor.after());
    }
}
