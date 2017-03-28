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
package org.apache.camel.itest.cdi;

import java.util.Properties;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.camel.CamelContext;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.component.properties.PropertiesComponent;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Verify {@link CdiCamelExtension} with custom properties.
 */
@RunWith(Arquillian.class)
public class PropertiesConfigurationTest {

    @Inject
    private CamelContext camelContext;

    @Produces
    @ApplicationScoped
    @Named("properties")
    private static PropertiesComponent propertiesComponent() {
        Properties configuration = new Properties();
        configuration.put("property", "value");
        PropertiesComponent component = new PropertiesComponent();
        component.setInitialProperties(configuration);
        component.setLocation("classpath:camel1.properties,classpath:camel2.properties");
        return component;
    }

    @Deployment
    public static JavaArchive createDeployment() {
        return Maven.configureResolver().workOffline()
            .loadPomFromFile("pom.xml")
            .resolve("org.apache.camel:camel-cdi")
            .withoutTransitivity()
            .asSingle(JavaArchive.class);
    }

    @Test
    public void checkContext() throws Exception {
        assertNotNull(camelContext);

        assertEquals("value1", camelContext.resolvePropertyPlaceholders("{{property1}}"));
        assertEquals("value2", camelContext.resolvePropertyPlaceholders("{{property2}}"));
        assertEquals("value1_value2", camelContext.resolvePropertyPlaceholders("{{property1}}_{{property2}}"));
    }
}
