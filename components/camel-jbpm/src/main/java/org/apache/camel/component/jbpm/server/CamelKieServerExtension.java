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

package org.apache.camel.component.jbpm.server;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.jbpm.services.api.service.ServiceRegistry;
import org.kie.server.services.api.KieContainerInstance;
import org.kie.server.services.api.KieServerExtension;
import org.kie.server.services.api.KieServerRegistry;
import org.kie.server.services.api.SupportedTransports;
import org.kie.server.services.impl.KieServerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelKieServerExtension implements KieServerExtension {
    public static final String EXTENSION_NAME = "Camel";

    private static final Logger LOGGER = LoggerFactory.getLogger(CamelKieServerExtension.class);

    private static final Boolean DISABLED = Boolean.parseBoolean(System.getProperty("org.camel.server.ext.disabled", "false"));

    protected DefaultCamelContext camel;

    protected boolean managedCamel;

    protected Map<String, DefaultCamelContext> camelContexts = new HashMap<>();

    public CamelKieServerExtension() {
        this.managedCamel = true;
    }

    public CamelKieServerExtension(DefaultCamelContext camel) {
        this.camel = camel;
        this.managedCamel = false;
    }

    @Override
    public boolean isInitialized() {
        return camel != null;
    }

    @Override
    public boolean isActive() {
        return !DISABLED;
    }

    @Override
    public void init(KieServerImpl kieServer, KieServerRegistry registry) {
        if (this.managedCamel && this.camel == null) {
            this.camel = new DefaultCamelContext();
            this.camel.setName("KIE Server Camel context");

            try (InputStream is = this.getClass().getResourceAsStream("/global-camel-routes.xml")) {
                if (is != null) {

                    RoutesDefinition routes = camel.loadRoutesDefinition(is);
                    camel.addRouteDefinitions(routes.getRoutes());
                }
            } catch (Exception e) {
                LOGGER.error("Error while adding Camel context for KIE Server", e);
            }
        }

        ServiceRegistry.get().register("GlobalCamelService", this.camel);
    }

    @Override
    public void destroy(KieServerImpl kieServer, KieServerRegistry registry) {
        ServiceRegistry.get().remove("GlobalCamelService");

        if (this.managedCamel && this.camel != null) {
            try {
                this.camel.stop();
            } catch (Exception e) {
                LOGGER.error("Failed at stopping KIE Server extension {}", EXTENSION_NAME);
            }
        }
    }

    @Override
    public void createContainer(String id, KieContainerInstance kieContainerInstance, Map<String, Object> parameters) {

        ClassLoader classloader = kieContainerInstance.getKieContainer().getClassLoader();
        try (InputStream is = classloader.getResourceAsStream("camel-routes.xml")) {
            if (is != null) {

                DefaultCamelContext context = new DefaultCamelContext();
                context.setName("KIE Server Camel context for container " + kieContainerInstance.getContainerId());

                RoutesDefinition routes = context.loadRoutesDefinition(is);
                annotateKJarRoutes(routes, id);
                context.addRouteDefinitions(routes.getRoutes());
                context.start();
                camelContexts.put(id, context);

                ServiceRegistry.get().register(id + "_CamelService", context);

            }
        } catch (Exception e) {
            LOGGER.error("Error while adding Camel context for {}", kieContainerInstance.getContainerId(), e);
        }
    }

    @Override
    public void updateContainer(String id, KieContainerInstance kieContainerInstance, Map<String, Object> parameters) {
        disposeContainer(id, kieContainerInstance, parameters);
        createContainer(id, kieContainerInstance, parameters);
    }

    @Override
    public boolean isUpdateContainerAllowed(String id, KieContainerInstance kieContainerInstance, Map<String, Object> parameters) {
        return true;
    }

    @Override
    public void disposeContainer(String id, KieContainerInstance kieContainerInstance, Map<String, Object> parameters) {
        DefaultCamelContext context = camelContexts.get(id);

        if (context != null) {

            ServiceRegistry.get().remove(id + "_CamelService");
            try {
                context.stop();
            } catch (Exception e) {
                LOGGER.error("Error while removing Camel context for container {}", id, e);
            }
        }
    }

    @Override
    public List<Object> getAppComponents(SupportedTransports type) {
        return Collections.emptyList();
    }

    @Override
    public <T> T getAppComponents(Class<T> serviceType) {
        return null;
    }

    @Override
    public String getImplementedCapability() {
        return "Integration";
    }

    @Override
    public List<Object> getServices() {
        return Collections.emptyList();
    }

    @Override
    public String getExtensionName() {
        return EXTENSION_NAME;
    }

    @Override
    public Integer getStartOrder() {
        return 50;
    }

    @Override
    public void serverStarted() {
        if (this.managedCamel && this.camel != null && !this.camel.isStarted()) {
            try {
                this.camel.start();
            } catch (Exception e) {
                LOGGER.error("Failed at start Camel context", e);
            }
        }
    }

    @Override
    public String toString() {
        return EXTENSION_NAME + " KIE Server extension";
    }

    protected void annotateKJarRoutes(RoutesDefinition routes, String deploymentId) {
        for (RouteDefinition route : routes.getRoutes()) {

            for (FromDefinition from : route.getInputs()) {

                if (from.getUri().startsWith("jbpm:events") && !from.getUri().contains("deploymentId")) {
                    StringBuilder uri = new StringBuilder(from.getUri());

                    String[] split = from.getUri().split("\\?");
                    if (split.length == 1) {
                        // no query given
                        uri.append("?");
                    } else {
                        // already query params exist
                        uri.append("&");
                    }
                    uri.append("deploymentId=").append(deploymentId);
                    from.setUri(uri.toString());
                }

                System.out.println(from.getUri());
            }
        }
    }
}
