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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.StartupConditionStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainRoutesAndModelEdgeCasesTest {

    @TempDir
    Path dir;

    @Test
    public void testStartupConditionCamelCaseKeys() {
        Main main = new Main();
        main.addInitialProperty("camel.startupCondition.enabled", "true");
        main.addInitialProperty("camel.startupCondition.timeout", "100");
        main.addInitialProperty("camel.startupCondition.fileExists", dir.resolve("no-such-file").toString());
        main.addInitialProperty("camel.startupCondition.onTimeout", "fail");
        try {
            assertThrows(Exception.class, main::start);
        } finally {
            main.stop();
        }
    }

    @Test
    public void testStartupConditionLowerCaseKeys() {
        Main main = new Main();
        main.addInitialProperty("camel.startupcondition.enabled", "true");
        main.addInitialProperty("camel.startupcondition.timeout", "100");
        main.addInitialProperty("camel.startupcondition.fileExists", dir.toString());
        main.start();
        try {
            StartupConditionStrategy scs = main.getCamelContext().getCamelContextExtension()
                    .getContextPlugin(StartupConditionStrategy.class);
            assertTrue(scs.isEnabled());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testRoutesIncludePatternWithSpaces() throws Exception {
        Files.writeString(dir.resolve("a.xml"), "<routes/>");
        Files.writeString(dir.resolve("b.xml"), "<routes/>");

        CamelContext context = new DefaultCamelContext();
        DefaultRoutesCollector collector = new DefaultRoutesCollector();
        Collection<Resource> found = collector.findRouteResourcesFromDirectory(context, null,
                "file:" + dir.resolve("a.xml") + ", file:" + dir.resolve("b.xml"));
        assertEquals(2, found.size());
    }

    @Test
    public void testGlobalVariableWithDotInName() {
        Main main = new Main();
        main.addInitialProperty("camel.variable.global.foo.bar", "1");
        main.start();
        try {
            CamelContext context = main.getCamelContext();
            assertEquals(1, context.getVariable("foo.bar"));
            assertNull(context.getVariable("foo:bar"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testInvalidRouteTemplateProperty() {
        Main main = new Main();
        main.addInitialProperty("camel.routeTemplate.foo", "bar");
        try {
            Exception e = assertThrows(Exception.class, main::start);
            Throwable t = e;
            boolean found = false;
            while (t != null) {
                if (t.getMessage() != null && t.getMessage().contains("Invalid route template property")) {
                    found = true;
                }
                t = t.getCause();
            }
            assertTrue(found, e.getMessage());
        } finally {
            main.stop();
        }
    }
}
