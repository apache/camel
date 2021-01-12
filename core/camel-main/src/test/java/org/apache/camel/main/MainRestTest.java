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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.RestConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainRestTest {

    @Test
    public void testMain() throws Exception {
        Main main = new Main();

        main.addInitialProperty("camel.rest.bindingMode", "json");
        main.addInitialProperty("camel.rest.apiContextPath", "bar");

        main.configure()
                .rest()
                .withComponent("servlet")
                .withContextPath("foo")
                .withUseXForwardHeaders(true)
                .withPort(1234)
                .end();

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        RestConfiguration def = context.getRestConfiguration();
        assertNotNull(def);

        assertEquals(1234, def.getPort());
        assertTrue(def.isUseXForwardHeaders());
        assertEquals("foo", def.getContextPath());
        assertEquals("bar", def.getApiContextPath());
        assertEquals("servlet", def.getComponent());
        assertEquals(RestConfiguration.RestBindingMode.json, def.getBindingMode());

        main.stop();
    }

}
