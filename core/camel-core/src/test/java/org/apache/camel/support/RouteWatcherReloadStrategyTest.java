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
package org.apache.camel.support;

import java.io.File;
import java.util.Arrays;

import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RouteWatcherReloadStrategyTest extends ContextTestSupport {

    @Test
    public void testBasePathExact() throws Exception {
        RouteWatcherReloadStrategy strategy = new RouteWatcherReloadStrategy("./src/test/resources");
        strategy.setPattern("log4j2.properties");
        strategy.setCamelContext(context);
        strategy.doStart();

        assertNotNull(strategy.getFileFilter());
        File folder = new File("./src/test/resources");
        assertTrue(folder.isDirectory());

        File[] fs = folder.listFiles(strategy.getFileFilter());
        assertNotNull(fs);
        assertEquals(1, fs.length);
        assertEquals("log4j2.properties", fs[0].getName());
    }

    @Test
    public void testBasePathWildcardExtension() throws Exception {
        RouteWatcherReloadStrategy strategy = new RouteWatcherReloadStrategy("./src/test/resources");
        strategy.setPattern("*.properties");
        strategy.setCamelContext(context);
        strategy.doStart();

        assertNotNull(strategy.getFileFilter());
        File folder = new File("./src/test/resources");
        assertTrue(folder.isDirectory());

        File[] fs = folder.listFiles(strategy.getFileFilter());
        assertNotNull(fs);
        assertTrue(fs.length >= 5);
        assertTrue(Arrays.stream(fs).anyMatch(f -> f.getName().equals("log4j2.properties")));
    }

    @Test
    public void testBasePathFullWildcard() throws Exception {
        RouteWatcherReloadStrategy strategy = new RouteWatcherReloadStrategy("./src/test/resources");
        strategy.setPattern("*");
        strategy.setCamelContext(context);
        strategy.doStart();

        assertNotNull(strategy.getFileFilter());
        File folder = new File("./src/test/resources");
        assertTrue(folder.isDirectory());

        File[] fs = folder.listFiles(strategy.getFileFilter());
        assertNotNull(fs);
        assertTrue(fs.length >= 9);
        assertTrue(Arrays.stream(fs).anyMatch(f -> f.getName().equals("log4j2.properties")));
    }

    @Test
    public void testNullPattern() throws Exception {
        RouteWatcherReloadStrategy strategy = new RouteWatcherReloadStrategy("./src/test/resources/org/apache/camel/model");
        strategy.setPattern(null);
        strategy.setCamelContext(context);
        strategy.doStart();

        assertNotNull(strategy.getFileFilter());
        File folder = new File("./src/test/resources/org/apache/camel/model");
        assertTrue(folder.isDirectory());

        File[] fs = folder.listFiles(strategy.getFileFilter());
        assertNotNull(fs);
        assertTrue(fs.length >= 40, String.valueOf(fs.length));
        // null goes back to default
        assertEquals("*.yaml,*.xml", strategy.getPattern());
    }

}
