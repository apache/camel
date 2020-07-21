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
package org.apache.camel.support;

import java.util.Collection;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.OnCamelContextEvent;
import org.apache.camel.spi.OnCamelContextInitialized;
import org.apache.camel.spi.OnCamelContextStart;
import org.apache.camel.spi.OnCamelContextStop;

/**
 * A useful base class for {@link LifecycleStrategy} implementations.
 */
public abstract class LifecycleStrategySupport implements LifecycleStrategy {

    @Override
    public void onContextStart(CamelContext context) throws VetoCamelContextStartException {
        // noop
    }

    @Override
    public void onContextStop(CamelContext context) {
        // noop
    }

    @Override
    public void onComponentAdd(String name, Component component) {
        // noop
    }

    @Override
    public void onComponentRemove(String name, Component component) {
        // noop
    }

    @Override
    public void onEndpointAdd(Endpoint endpoint) {
        // noop
    }

    @Override
    public void onEndpointRemove(Endpoint endpoint) {
        // noop
    }

    @Override
    public void onServiceAdd(CamelContext context, Service service, org.apache.camel.Route route) {
        // noop
    }

    @Override
    public void onServiceRemove(CamelContext context, Service service, org.apache.camel.Route route) {
        // noop
    }

    @Override
    public void onRoutesAdd(Collection<org.apache.camel.Route> routes) {
        // noop
    }

    @Override
    public void onRoutesRemove(Collection<org.apache.camel.Route> routes) {
        // noop
    }

    @Override
    public void onRouteContextCreate(Route route) {
        // noop
    }

    @Override
    public void onErrorHandlerAdd(Route route, Processor errorHandler, ErrorHandlerFactory errorHandlerBuilder) {
        // noop
    }

    @Override
    public void onErrorHandlerRemove(Route route, Processor errorHandler, ErrorHandlerFactory errorHandlerBuilder) {
        // noop
    }

    @Override
    public void onThreadPoolAdd(CamelContext camelContext, ThreadPoolExecutor threadPool, String id,
                                String sourceId, String routeId, String threadPoolProfileId) {
        // noop
    }

    @Override
    public void onThreadPoolRemove(CamelContext camelContext, ThreadPoolExecutor threadPool) {
        // noop
    }


    public static LifecycleStrategy adapt(OnCamelContextEvent handler) {
        return new LifecycleStrategySupport() {
            @Override
            public void onContextInitialized(CamelContext context) throws VetoCamelContextStartException {
                if (handler instanceof OnCamelContextInitialized) {
                    ((OnCamelContextInitialized) handler).onContextInitialized(context);
                }
            }
            @Override
            public void onContextStart(CamelContext context) throws VetoCamelContextStartException {
                if (handler instanceof OnCamelContextStart) {
                    ((OnCamelContextStart) handler).onContextStart(context);
                }
            }
            @Override
            public void onContextStop(CamelContext context) {
                if (handler instanceof OnCamelContextStop) {
                    ((OnCamelContextStop) handler).onContextStop(context);
                }
            }
        };
    }

    public static LifecycleStrategy adapt(OnCamelContextInitialized handler) {
        return new LifecycleStrategySupport() {
            @Override
            public void onContextInitialized(CamelContext context) throws VetoCamelContextStartException {
                handler.onContextInitialized(context);
            }
        };
    }

    public static LifecycleStrategy adapt(OnCamelContextStart handler) {
        return new LifecycleStrategySupport() {
            @Override
            public void onContextStart(CamelContext context) throws VetoCamelContextStartException {
                handler.onContextStart(context);
            }
        };
    }

    public static LifecycleStrategy adapt(OnCamelContextStop handler) {
        return new LifecycleStrategySupport() {
            @Override
            public void onContextStop(CamelContext context) {
                handler.onContextStop(context);
            }
        };
    }

    public static OnCamelContextInitialized onCamelContextInitialized(Consumer<CamelContext> consumer) {
        return consumer::accept;
    }

    public static OnCamelContextStart onCamelContextStart(Consumer<CamelContext> consumer) {
        return consumer::accept;
    }

    public static OnCamelContextStop onCamelContextStop(Consumer<CamelContext> consumer) {
        return consumer::accept;
    }
}
