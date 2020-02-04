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
package org.apache.camel.component.jbpm.server;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.component.jbpm.JBPMConstants;
import org.apache.camel.component.jbpm.config.CamelContextBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.jbpm.services.api.service.ServiceRegistry;
import org.kie.internal.runtime.manager.InternalRuntimeManager;
import org.kie.internal.runtime.manager.RuntimeManagerRegistry;
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

    protected DefaultCamelContext camelContext;

    protected boolean managedCamel;

    protected Map<String, DefaultCamelContext> camelContexts = new HashMap<>();

    protected CamelContextBuilder camelContextBuilder;

    public CamelKieServerExtension() {
        this.managedCamel = true;
        this.camelContextBuilder = discoverCamelContextBuilder();
    }

    public CamelKieServerExtension(CamelContextBuilder camelContextBuilder) {
        this.managedCamel = true;
        this.camelContextBuilder = camelContextBuilder;
    }

    public CamelKieServerExtension(DefaultCamelContext camelContext) {
        this.camelContext = camelContext;
        this.managedCamel = false;
        this.camelContextBuilder = discoverCamelContextBuilder();
    }

    public CamelKieServerExtension(DefaultCamelContext camelContext, CamelContextBuilder camelContextBuilder) {
        this.camelContext = camelContext;
        this.managedCamel = false;
        this.camelContextBuilder = camelContextBuilder;
    }

    @Override
    public boolean isInitialized() {
        return camelContext != null;
    }

    @Override
    public boolean isActive() {
        return !DISABLED;
    }

    @Override
    public void init(KieServerImpl kieServer, KieServerRegistry registry) {
        if (this.managedCamel && this.camelContext == null) {
            this.camelContext = (DefaultCamelContext)buildGlobalContext();
            this.camelContext.setName("KIE Server Camel context");

            try (InputStream is = this.getClass().getResourceAsStream("/global-camel-routes.xml")) {
                if (is != null) {
                    ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
                    RoutesDefinition routes = (RoutesDefinition) ecc.getXMLRoutesDefinitionLoader().loadRoutesDefinition(camelContext, is);
                    camelContext.addRouteDefinitions(routes.getRoutes());
                }
            } catch (Exception e) {
                LOGGER.error("Error while adding Camel context for KIE Server", e);
            }
        }

        ServiceRegistry.get().register(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY, this.camelContext);
    }

    @Override
    public void destroy(KieServerImpl kieServer, KieServerRegistry registry) {
        ServiceRegistry.get().remove("GlobalCamelService");

        if (this.managedCamel && this.camelContext != null) {
            try {
                this.camelContext.stop();
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

                DefaultCamelContext context = (DefaultCamelContext)buildDeploymentContext(id, classloader);
                context.setName("KIE Server Camel context for container " + kieContainerInstance.getContainerId());

                ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
                RoutesDefinition routes = (RoutesDefinition) ecc.getXMLRoutesDefinitionLoader().loadRoutesDefinition(context, is);
                annotateKJarRoutes(routes, id);
                context.addRouteDefinitions(routes.getRoutes());
                
                context.start();
                camelContexts.put(id, context);

                ServiceRegistry.get().register(id + JBPMConstants.DEPLOYMENT_CAMEL_CONTEXT_SERVICE_KEY_POSTFIX, context);

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
        if (this.managedCamel && this.camelContext != null && !this.camelContext.isStarted()) {
            try {
                this.camelContext.start();
            } catch (Exception e) {
                LOGGER.error("Failed at start Camel context", e);
            }
        }
    }

    @Override
    public String toString() {
        return EXTENSION_NAME + " KIE Server extension";
    }

    public DefaultCamelContext getCamelContext() {
        return camelContext;
    }

    public CamelContextBuilder getCamelContextBuilder() {
        return camelContextBuilder;
    }

    protected void annotateKJarRoutes(RoutesDefinition routes, String deploymentId) {
        for (RouteDefinition route : routes.getRoutes()) {
            FromDefinition from = route.getInput();
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
        }
    }

    protected CamelContext buildGlobalContext() {
        if (camelContextBuilder != null) {
            return camelContextBuilder.buildCamelContext();
        }

        return new CamelContextBuilder() {
        }.buildCamelContext();
    }

    protected CamelContext buildDeploymentContext(String identifier, ClassLoader classloader) {
       
        
        InternalRuntimeManager runtimeManager = (InternalRuntimeManager)RuntimeManagerRegistry.get().getManager(identifier);

        if (runtimeManager != null) {

            CamelContextBuilder deploymentContextBuilder = (CamelContextBuilder)runtimeManager.getEnvironment().getEnvironment().get(JBPMConstants.CAMEL_CONTEXT_BUILDER_KEY);
            if (deploymentContextBuilder != null) {
                return deploymentContextBuilder.buildCamelContext();
            }
        }
        CamelContext camelContext = new CamelContextBuilder() { 
        }.buildCamelContext();       
        camelContext.setApplicationContextClassLoader(classloader);
        return camelContext;
    }

    protected CamelContextBuilder discoverCamelContextBuilder() {

        ServiceLoader<CamelContextBuilder> builders = ServiceLoader.load(CamelContextBuilder.class);
        Iterator<CamelContextBuilder> it = builders.iterator();
        if (it.hasNext()) {
            return it.next();
        }

        return null;
    }
}
