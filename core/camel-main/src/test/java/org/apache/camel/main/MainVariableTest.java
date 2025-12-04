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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.FileInputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MainVariableTest {

    Main main;

    @BeforeEach
    public void before() {
        main = new Main();
    }

    @AfterEach
    public void after() {
        main.stop();
    }

    @Test
    public void testMainVariableParameters() {
        main.addInitialProperty("camel.variable.global.greeting", "Random number");
        // global is default
        main.addInitialProperty("camel.variable.random", "999");
        main.addInitialProperty("camel.variable.gold", "true");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        assertEquals("Random number", context.getVariable("greeting"));
        assertEquals(999, context.getVariable("random"));
        assertEquals(Boolean.TRUE, context.getVariable("gold"));
    }

    @Test
    public void testMainVariableResource() throws Exception {
        main.addInitialProperty("camel.variable.random", "resource:classpath:random.json");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        String text = IOHelper.loadText(new FileInputStream("src/test/resources/random.json"));
        assertEquals(text, context.getVariable("random"));
    }

    @Test
    public void testMainVariableContext() {
        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        context.setVariable("global:greeting", "Random number");
        // global is default
        context.setVariable("random", 999);

        assertEquals("Random number", context.getVariable("greeting"));
        assertEquals(999, context.getVariable("random"));
    }

    @Test
    public void testMainVariableBean() {
        MyAddress adr = new MyAddress(90210, "somestreet 123");

        main.addInitialProperty("camel.variable.global.greeting", "Random number");
        // global is default
        main.addInitialProperty("camel.variable.random", "999");
        main.addInitialProperty("camel.variable.adr", "#bean:myAdr");
        main.addInitialProperty("camel.variable.myFloat", "#valueAs(float):1.23");

        main.addMainListener(new MainListenerSupport() {
            @Override
            public void beforeConfigure(BaseMainSupport main) {
                main.getCamelContext().getRegistry().bind("myAdr", adr);
            }
        });

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        assertEquals("Random number", context.getVariable("greeting"));
        assertEquals(999, context.getVariable("random"));
        assertEquals(1.23f, context.getVariable("myFloat"));
        assertSame(adr, context.getVariable("adr"));
    }
}
