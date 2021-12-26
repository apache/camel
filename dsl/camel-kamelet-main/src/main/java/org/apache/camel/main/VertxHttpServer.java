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
package org.apache.camel.main;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StartupListener;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.util.ReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To setup vertx http server in the running Camel application
 */
public final class VertxHttpServer {

    private static final Logger LOG = LoggerFactory.getLogger(VertxHttpServer.class);

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private VertxHttpServer() {
    }

    public static void registerServer(CamelContext camelContext) {
        if (REGISTERED.compareAndSet(false, true)) {
            doRegisterServer(camelContext, 8080);
        }
    }

    public static void registerServer(CamelContext camelContext, int port) {
        if (REGISTERED.compareAndSet(false, true)) {
            doRegisterServer(camelContext, port);
        }
    }

    private static void doRegisterServer(CamelContext camelContext, int port) {
        try {
            // must load via the classloader set on camel context that will have the classes on its classpath
            Class<?> clazz = camelContext.getClassResolver()
                    .resolveMandatoryClass(
                            "org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration");
            Object config = clazz.getConstructors()[0].newInstance();
            camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection()
                    .setProperty(camelContext, config, "port", port);

            clazz = camelContext.getClassResolver()
                    .resolveMandatoryClass(
                            "org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer");
            Object server = clazz.getConstructors()[0].newInstance(config);

            camelContext.addService(server);

            // after camel is started then add event notifier
            camelContext.addStartupListener(new StartupListener() {
                @Override
                public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
                    camelContext.getManagementStrategy().addEventNotifier(new SimpleEventNotifierSupport() {

                        private volatile Component phc;
                        private volatile Method method;
                        private Set<String> last;

                        @Override
                        public boolean isEnabled(CamelEvent event) {
                            return event instanceof CamelEvent.CamelContextStartedEvent
                                    || event instanceof CamelEvent.RouteReloadedEvent;
                        }

                        @Override
                        public void notify(CamelEvent event) throws Exception {
                            if (method == null) {
                                phc = camelContext.getComponent("platform-http", Component.class);
                                method = ReflectionHelper.findMethod(phc.getClass(), "getHttpEndpoints");
                            }

                            // when reloading then there may be more routes in the same batch, so we only want
                            // to log the summary at the end
                            if (event instanceof CamelEvent.RouteReloadedEvent) {
                                CamelEvent.RouteReloadedEvent re = (CamelEvent.RouteReloadedEvent) event;
                                if (re.getIndex() < re.getTotal()) {
                                    return;
                                }
                            }

                            Set<String> endpoints = (Set<String>) ObjectHelper.invokeMethodSafe(method, phc);
                            if (endpoints.isEmpty()) {
                                return;
                            }

                            // log only if changed
                            if (last == null || last.size() != endpoints.size() || !last.containsAll(endpoints)) {
                                LOG.info("HTTP endpoints summary");
                                for (String u : endpoints) {
                                    LOG.info("    http://0.0.0.0:" + port + u);
                                }
                            }

                            // use a defensive copy of last known endpoints
                            last = new HashSet<>(endpoints);
                        }
                    });
                }
            });

        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }

}
