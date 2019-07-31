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

import java.util.HashMap;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.component.jbpm.JBPMConstants;
import org.apache.camel.component.jbpm.config.CamelContextBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.jbpm.services.api.service.ServiceRegistry;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.KieServices;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.runtime.manager.InternalRuntimeManager;
import org.kie.internal.runtime.manager.RuntimeEnvironment;
import org.kie.internal.runtime.manager.RuntimeManagerRegistry;
import org.kie.server.services.api.KieContainerInstance;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CamelKieServerExtensionTest {
    @Mock
    InternalRuntimeManager runtimeManager;

    @Mock
    RuntimeEnvironment runtimeEnvironment;

    @Mock
    private KieContainerInstance kieContainerInstance;

    @Mock
    private KieContainer kieContainer;
    
    private String identifier = "test";

    @After
    public void cleanup() {
        RuntimeManagerRegistry.get().remove(identifier);
    }

    @Test
    public void testInit() {
        CamelKieServerExtension extension = new CamelKieServerExtension();
        extension.init(null, null);
        DefaultCamelContext globalCamelContext = (DefaultCamelContext)ServiceRegistry.get().service(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY);
        List<RouteDefinition> globalRestDefinitions = globalCamelContext.getRouteDefinitions();
        assertThat(globalRestDefinitions.size(), equalTo(1));
        assertThat(globalCamelContext.getRouteDefinition("unitTestRoute"), is(notNullValue()));
    }

    @Test
    public void testCreateContainer() {
        CamelKieServerExtension extension = new CamelKieServerExtension();
        final String containerId = "testContainer";

        when(kieContainerInstance.getKieContainer()).thenReturn(kieContainer);
        when(kieContainer.getClassLoader()).thenReturn(this.getClass().getClassLoader());

        extension.createContainer(containerId, kieContainerInstance, new HashMap<String, Object>());

        DefaultCamelContext camelContext = (DefaultCamelContext)ServiceRegistry.get().service("testContainer" + JBPMConstants.DEPLOYMENT_CAMEL_CONTEXT_SERVICE_KEY_POSTFIX);
        List<RouteDefinition> restDefinitions = camelContext.getRouteDefinitions();
        assertThat(restDefinitions.size(), equalTo(1));

        assertThat(camelContext.getRoute("unitTestRoute"), is(notNullValue()));
    }

    @Test
    public void testDefaultSetup() {

        CamelKieServerExtension extension = new CamelKieServerExtension();

        assertNull(extension.getCamelContextBuilder());
    }

    @Test
    public void testDefaultSetupCustomDiscovery() {

        CamelKieServerExtension extension = new CamelKieServerExtension() {

            @Override
            protected CamelContextBuilder discoverCamelContextBuilder() {
                return new CamelContextBuilder() {

                    @Override
                    public CamelContext buildCamelContext() {
                        // for test purpose return simply null as camel context
                        return null;
                    }

                };
            }

        };

        assertNotNull(extension.getCamelContextBuilder());
        assertNull(extension.getCamelContextBuilder().buildCamelContext());
    }

    @Test
    public void testBuildGlobalCamelContext() throws Exception {

        CamelKieServerExtension extension = new CamelKieServerExtension();
        CamelContext context = extension.buildGlobalContext();
        assertNotNull(context);

        context.stop();
    }

    @Test
    public void testBuildGlobalCamelContextCustomBuilder() throws Exception {

        CamelKieServerExtension extension = new CamelKieServerExtension(new CamelContextBuilder() {

            @Override
            public CamelContext buildCamelContext() {
                // for test purpose return simply null as camel context
                return null;
            }

        });
        CamelContext context = extension.buildGlobalContext();
        assertNull(context);
    }

    @Test
    public void testBuildDeploymentCamelContext() throws Exception {

        when(runtimeManager.getIdentifier()).thenReturn(identifier);
        when(runtimeManager.getEnvironment()).thenReturn(runtimeEnvironment);

        Environment environment = KieServices.get().newEnvironment();
        when(runtimeEnvironment.getEnvironment()).thenReturn(environment);

        RuntimeManagerRegistry.get().register(runtimeManager);

        CamelKieServerExtension extension = new CamelKieServerExtension();
        CamelContext context = extension.buildDeploymentContext(identifier, this.getClass().getClassLoader());
        assertNotNull(context);

        context.stop();
    }

    @Test
    public void testBuildDeploymentCamelContextCustomBuilder() throws Exception {

        when(runtimeManager.getIdentifier()).thenReturn(identifier);
        when(runtimeManager.getEnvironment()).thenReturn(runtimeEnvironment);

        Environment environment = KieServices.get().newEnvironment();
        environment.set(JBPMConstants.CAMEL_CONTEXT_BUILDER_KEY, new CamelContextBuilder() {

            @Override
            public CamelContext buildCamelContext() {
                // for test purpose return simply null as camel context
                return null;
            }

        });
        when(runtimeEnvironment.getEnvironment()).thenReturn(environment);

        RuntimeManagerRegistry.get().register(runtimeManager);

        CamelKieServerExtension extension = new CamelKieServerExtension();
        CamelContext context = extension.buildDeploymentContext(identifier, this.getClass().getClassLoader());
        assertNull(context);

    }
}
