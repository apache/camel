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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MainGlobalOptionsTest {

    @Test
    public void testMain() throws Exception {
        Main main = new Main();

        main.addInitialProperty("camel.main.global-options[foo]", "123");
        main.addInitialProperty("camel.main.global-options[bar]", "true");
        main.addInitialProperty("camel.main.globalOptions[baz]", "999");

        main.configure()
                .withGlobalOption("cheese", "Gauda")
                .withGlobalOption("drink", "Wine");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        assertEquals(6, context.getGlobalOptions().size());
        assertEquals("123", context.getGlobalOptions().get("foo"));
        assertEquals("true", context.getGlobalOptions().get("bar"));
        assertEquals("Gauda", context.getGlobalOptions().get("cheese"));
        assertEquals("Wine", context.getGlobalOptions().get("drink"));
        assertEquals("999", context.getGlobalOptions().get("baz"));
        assertEquals("org.apache.camel.main.Main", context.getGlobalOptions().get("CamelMainClass"));

        main.stop();
    }

}
