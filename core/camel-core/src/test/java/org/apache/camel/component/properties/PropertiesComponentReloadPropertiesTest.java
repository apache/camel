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
package org.apache.camel.component.properties;

import java.io.File;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertiesComponentReloadPropertiesTest extends ContextTestSupport {

    private String name;
    private String name2;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testReloadProperties() throws Exception {
        context.start();

        org.apache.camel.spi.PropertiesComponent pc = context.getPropertiesComponent();
        Properties prop = pc.loadProperties();

        assertNotNull(prop);
        assertEquals(2, prop.size());
        assertEquals("10", prop.getProperty("myQueueSize"));
        assertEquals("Moes", prop.getProperty("bar"));

        IOHelper.writeText("myQueueSize = 20\nsay=cheese", new File(name));
        IOHelper.writeText("bar = Jacks", new File(name2));

        // reload all
        boolean reloaded = pc.reloadProperties(null);
        assertTrue(reloaded);
        prop = pc.loadProperties();

        assertNotNull(prop);
        assertEquals(3, prop.size());
        assertEquals("20", prop.getProperty("myQueueSize"));
        assertEquals("cheese", prop.getProperty("say"));
        assertEquals("Jacks", prop.getProperty("bar"));
    }

    @Test
    public void testReloadPropertiesPattern() throws Exception {
        context.start();

        org.apache.camel.spi.PropertiesComponent pc = context.getPropertiesComponent();
        Properties prop = pc.loadProperties();

        assertNotNull(prop);
        assertEquals(2, prop.size());
        assertEquals("10", prop.getProperty("myQueueSize"));
        assertEquals("Moes", prop.getProperty("bar"));

        IOHelper.writeText("myQueueSize = 20\nsay=cheese", new File(name));
        IOHelper.writeText("bar = Jacks", new File(name2));

        // reload only one file
        boolean reloaded = pc.reloadProperties("file:" + name);
        assertTrue(reloaded);
        prop = pc.loadProperties();

        assertNotNull(prop);
        assertEquals(3, prop.size());
        assertEquals("20", prop.getProperty("myQueueSize"));
        assertEquals("cheese", prop.getProperty("say"));
        // should use old value as not reloaded
        assertEquals("Moes", prop.getProperty("bar"));
    }

    @Test
    public void testReloadNotMatch() throws Exception {
        context.start();

        org.apache.camel.spi.PropertiesComponent pc = context.getPropertiesComponent();
        Properties prop = pc.loadProperties();

        assertNotNull(prop);
        assertEquals(2, prop.size());
        assertEquals("10", prop.getProperty("myQueueSize"));
        assertEquals("Moes", prop.getProperty("bar"));

        // this files does not exist
        boolean reloaded = pc.reloadProperties("file:foo.properties");
        assertFalse(reloaded);
        prop = pc.loadProperties();

        // properties unchanged
        assertNotNull(prop);
        assertEquals(2, prop.size());
        assertEquals("10", prop.getProperty("myQueueSize"));
        assertEquals("Moes", prop.getProperty("bar"));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        name = fileUri() + "/myreload.properties";
        name = name.substring(5);
        IOHelper.writeText("myQueueSize = 10", new File(name));

        name2 = fileUri() + "/myreload2.properties";
        name2 = name2.substring(5);
        IOHelper.writeText("bar = Moes", new File(name2));

        context.getPropertiesComponent().setLocation("file:" + name + ",file:" + name2);
        return context;
    }

}
