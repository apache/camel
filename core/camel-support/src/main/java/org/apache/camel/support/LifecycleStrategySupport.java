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
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.OnCamelContextEvent;
import org.apache.camel.spi.OnCamelContextInitialized;
import org.apache.camel.spi.OnCamelContextInitializing;
import org.apache.camel.spi.OnCamelContextStarted;
import org.apache.camel.spi.OnCamelContextStarting;
import org.apache.camel.spi.OnCamelContextStopped;
import org.apache.camel.spi.OnCamelContextStopping;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A useful base class for {@link LifecycleStrategy} implementations.
 */
public abstract class LifecycleStrategySupport implements LifecycleStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(LifecycleStrategySupport.class);

    // *******************************
    //
    // Helpers (adapters)
    //
    // ********************************

    public static LifecycleStrategy adapt(OnCamelContextEvent handler) {
        return new LifecycleStrategySupport() {
            @Override
            public void onContextInitializing(CamelContext context) throws VetoCamelContextStartException {
                if (handler instanceof OnCamelContextInitializing) {
                    ((OnCamelContextInitializing) handler).onContextInitializing(context);
                }
            }

            @Override
            public void onContextInitialized(CamelContext context) throws VetoCamelContextStartException {
                if (handler instanceof OnCamelContextInitialized) {
                    ((OnCamelContextInitialized) handler).onContextInitialized(context);
                }
            }

            @Override
            public void onContextStarting(CamelContext context) throws VetoCamelContextStartException {
                if (handler instanceof OnCamelContextStarting) {
                    ((OnCamelContextStarting) handler).onContextStarting(context);
                }
            }

            @Override
            public void onContextStarted(CamelContext context) {
                if (handler instanceof OnCamelContextStarted) {
                    ((OnCamelContextStarted) handler).onContextStarted(context);
                }
            }

            @Override
            public void onContextStopping(CamelContext context) {
                if (handler instanceof OnCamelContextStopping) {
                    ((OnCamelContextStopping) handler).onContextStopping(context);
                }
            }

            @Override
            public void onContextStopped(CamelContext context) {
                if (handler instanceof OnCamelContextStopped) {
                    ((OnCamelContextStopped) handler).onContextStopped(context);
                }
            }
        };
    }

    public static LifecycleStrategy adapt(OnCamelContextInitializing handler) {
        return new LifecycleStrategySupport() {
            @Override
            public void onContextInitializing(CamelContext context) throws VetoCamelContextStartException {
                handler.onContextInitializing(context);
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

    public static LifecycleStrategy adapt(OnCamelContextStarting handler) {
        return new LifecycleStrategySupport() {
            @Override
            public void onContextStarting(CamelContext context) throws VetoCamelContextStartException {
                handler.onContextStarting(context);
            }
        };
    }

    public static LifecycleStrategy adapt(OnCamelContextStarted handler) {
        return new LifecycleStrategySupport() {
            @Override
            public void onContextStarted(CamelContext context) {
                handler.onContextStarted(context);
            }
        };
    }

    public static LifecycleStrategy adapt(OnCamelContextStopping handler) {
        return new LifecycleStrategySupport() {
            @Override
            public void onContextStopping(CamelContext context) {
                handler.onContextStopping(context);
            }
        };
    }

    public static LifecycleStrategy adapt(OnCamelContextStopped handler) {
        return new LifecycleStrategySupport() {
            @Override
            public void onContextStopped(CamelContext context) {
                handler.onContextStopped(context);
            }
        };
    }

    // *******************************
    //
    // Helpers (functional)
    //
    // ********************************

    public static OnCamelContextInitializing onCamelContextInitializing(Consumer<CamelContext> consumer) {
        return consumer::accept;
    }

    public static OnCamelContextInitialized onCamelContextInitialized(Consumer<CamelContext> consumer) {
        return consumer::accept;
    }

    public static OnCamelContextStarting onCamelContextStarting(Consumer<CamelContext> consumer) {
        return consumer::accept;
    }

    public static OnCamelContextStarted onCamelContextStarted(Consumer<CamelContext> consumer) {
        return consumer::accept;
    }

    public static OnCamelContextStopping onCamelContextStopping(Consumer<CamelContext> consumer) {
        return consumer::accept;
    }

    public static OnCamelContextStopped onCamelContextStopped(Consumer<CamelContext> consumer) {
        return consumer::accept;
    }

    // *******************************
    //
    // Helpers
    //
    // ********************************

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
    public void onThreadPoolAdd(
            CamelContext camelContext, ThreadPoolExecutor threadPool, String id,
            String sourceId, String routeId, String threadPoolProfileId) {
        // noop
    }

    @Override
    public void onThreadPoolRemove(CamelContext camelContext, ThreadPoolExecutor threadPool) {
        // noop
    }

    protected static void doAutoWire(String name, String kind, Object target, CamelContext camelContext) {
        PropertyConfigurer pc = PluginHelper.getConfigurerResolver(camelContext)
                .resolvePropertyConfigurer(name + "-" + kind, camelContext);
        if (pc instanceof PropertyConfigurerGetter) {
            PropertyConfigurerGetter getter = (PropertyConfigurerGetter) pc;
            String[] names = getter.getAutowiredNames();
            if (names != null) {
                for (String option : names) {
                    // is there already a configured value?
                    Object value = getter.getOptionValue(target, option, true);
                    if (value == null) {
                        Class<?> type = getter.getOptionType(option, true);
                        if (type != null) {
                            Set<?> set = camelContext.getRegistry().findByType(type);
                            if (set.size() == 1) {
                                value = set.iterator().next();
                            }
                        }
                        if (value != null) {
                            boolean hit = pc.configure(camelContext, target, option, value, true);
                            if (hit) {
                                LOG.info(
                                        "Autowired property: {} on {}: {} as exactly one instance of type: {} ({}) found in the registry",
                                        option, kind, name, type.getName(), value.getClass().getName());
                            }
                        }
                    }
                }
            }
        }
    }
}
