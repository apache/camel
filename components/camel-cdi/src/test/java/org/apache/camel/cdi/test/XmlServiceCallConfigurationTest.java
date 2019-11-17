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
package org.apache.camel.cdi.test;

import java.nio.file.Paths;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.ImportResource;
import org.apache.camel.model.Model;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.cloud.StaticServiceCallServiceDiscoveryConfiguration;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(Arquillian.class)
@ImportResource("imported-context.xml")
public class XmlServiceCallConfigurationTest {

    @Inject
    private CamelContext context;

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test Camel XML
            .addAsResource(
                Paths.get("src/test/resources/camel-context-service-call-configuration.xml").toFile(),
                "imported-context.xml")
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void testServiceDiscoveryConfiguration() {
        ServiceCallConfigurationDefinition conf1 = context.getExtension(Model.class).getServiceCallConfiguration("conf1");
        assertNotNull("No ServiceCallConfiguration (1)", conf1);
        assertNotNull("No ServiceDiscoveryConfiguration (1)", conf1.getServiceDiscoveryConfiguration());

        StaticServiceCallServiceDiscoveryConfiguration discovery1 = (StaticServiceCallServiceDiscoveryConfiguration)conf1.getServiceDiscoveryConfiguration();
        assertEquals(1, discovery1.getServers().size());
        assertEquals("localhost:9091", discovery1.getServers().get(0));

        ServiceCallConfigurationDefinition conf2 = context.getExtension(Model.class).getServiceCallConfiguration("conf2");
        assertNotNull("No ServiceCallConfiguration (2)", conf2);
        assertNotNull("No ServiceDiscoveryConfiguration (2)", conf2.getServiceDiscoveryConfiguration());

        StaticServiceCallServiceDiscoveryConfiguration discovery2 = (StaticServiceCallServiceDiscoveryConfiguration)conf2.getServiceDiscoveryConfiguration();
        assertEquals(2, discovery2.getServers().size());
        assertEquals("localhost:9092", discovery2.getServers().get(0));
        assertEquals("localhost:9093,localhost:9094", discovery2.getServers().get(1));
    }
}
