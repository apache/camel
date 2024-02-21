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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportOptionalValueTest {

    @Test
    public void testOptionalValue() throws Exception {
        CamelContext context = new DefaultCamelContext();

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);
        bi.setExtendedStatistics(true);

        Properties prop = new Properties();
        prop.setProperty("barOne", "Jack");
        prop.setProperty("luckyNumber", "42");
        prop.setProperty("barTwo", "Murphy");
        context.getPropertiesComponent().setInitialProperties(prop);

        context.start();

        MySecondFoo target = new MySecondFoo();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withProperty("bars[0]", "#class:" + MySecondBar.class.getName())
                .withProperty("bars[0].names[0]", "{{?barOne:John}}")
                .withProperty("bars[0].names[1]", "{{?barTwo}}")
                .withRemoveParameters(false).bind();

        assertEquals(1, target.getBars().size());
        assertEquals(2, target.getBars().get(0).getNames().size());
        assertEquals("Jack", target.getBars().get(0).getNames().get(0));
        assertEquals("Murphy", target.getBars().get(0).getNames().get(1));

        // now update values optional
        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withProperty("bars[0].names[0]", "{{?unknown:John}}")
                .withProperty("bars[0].names[1]", "{{?unknown}}")
                .withRemoveParameters(false).bind();

        assertEquals(1, target.getBars().size());
        assertEquals(2, target.getBars().get(0).getNames().size());
        // will use default value
        assertEquals("John", target.getBars().get(0).getNames().get(0));
        // will not change existing value
        assertEquals("Murphy", target.getBars().get(0).getNames().get(1));

        // will auto detect generated configurer so no reflection in use
        assertEquals(0, bi.getInvokedCounter());

        context.stop();
    }

}
