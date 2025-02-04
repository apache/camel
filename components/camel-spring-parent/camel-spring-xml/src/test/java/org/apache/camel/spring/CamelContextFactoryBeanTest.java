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
package org.apache.camel.spring;

import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spring.xml.CamelContextFactoryBean;
import org.apache.camel.support.DefaultUuidGenerator;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.SimpleUuidGenerator;
import org.apache.camel.xml.jaxb.DefaultModelJAXBContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelContextFactoryBeanTest {

    private CamelContextFactoryBean factory;

    @BeforeEach
    public void setUp() throws Exception {
        factory = new CamelContextFactoryBean();
        factory.setId("camelContext");
    }

    @Test
    public void testGetDefaultUuidGenerator() throws Exception {
        factory.setApplicationContext(new StaticApplicationContext());
        factory.afterPropertiesSet();

        UuidGenerator uuidGenerator = factory.getContext().getUuidGenerator();

        assertTrue(uuidGenerator instanceof DefaultUuidGenerator);
    }

    @Test
    public void testGetCustomUuidGenerator() throws Exception {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton("uuidGenerator", SimpleUuidGenerator.class);
        factory.setApplicationContext(applicationContext);
        factory.afterPropertiesSet();

        UuidGenerator uuidGenerator = factory.getContext().getUuidGenerator();

        assertTrue(uuidGenerator instanceof SimpleUuidGenerator);
    }

    @Test
    public void testCustomModelJAXBContextFactory() throws Exception {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton("customModelJAXBContextFactory", CustomModelJAXBContextFactory.class);
        factory.setApplicationContext(applicationContext);
        factory.afterPropertiesSet();

        ModelJAXBContextFactory modelJAXBContextFactory
                = PluginHelper.getModelJAXBContextFactory(factory.getContext());

        assertTrue(modelJAXBContextFactory instanceof CustomModelJAXBContextFactory);
    }

    private static class CustomModelJAXBContextFactory extends DefaultModelJAXBContextFactory {
        // Do nothing here
    }
}
