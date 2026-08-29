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
package org.apache.camel.main.console;

import org.apache.camel.CamelContext;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.console.DefaultDevConsoleRegistry;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DependencyDownloaderConsole reports on a running {@link org.apache.camel.main.download.MavenDependencyDownloader}
 * service; spinning up a real one is out of scope for this test, so it only verifies the console's basic shape when no
 * downloader is registered.
 */
public class DependencyDownloaderConsoleTest {

    private CamelContext camelContext;

    @BeforeEach
    void setUp() {
        camelContext = new DefaultCamelContext();
        camelContext.setDevConsole(true);
        DefaultDevConsoleRegistry registry = new DefaultDevConsoleRegistry(camelContext);
        registry.register(new DependencyDownloaderConsole());
        camelContext.getCamelContextExtension().addContextPlugin(DevConsoleRegistry.class, registry);
        camelContext.start();
    }

    @AfterEach
    void tearDown() {
        camelContext.stop();
    }

    @Test
    void testDependencyDownloaderConsoleNoDownloader() {
        DevConsole con = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("dependency-downloader");
        assertNotNull(con);
        assertEquals("camel-jbang", con.getGroup());

        String text = (String) con.call(DevConsole.MediaType.TEXT);
        assertNotNull(text);

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }
}
