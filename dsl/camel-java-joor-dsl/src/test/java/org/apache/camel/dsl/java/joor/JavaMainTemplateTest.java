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
package org.apache.camel.dsl.java.joor;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.java.joor.support.MockRestConsumerFactory;
import org.apache.camel.main.Main;
import org.apache.camel.model.ModelCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JavaMainTemplateTest {

    @Test
    public void testMainRoutesCollector() {
        // will load XML from target/classes when testing
        doTestMain(
                "org/apache/camel/main/java/MyRoutesWithTemplate.java",
                null);
    }

    @Test
    public void testMainRoutesCollectorScan() {
        // will load XML from target/classes when testing
        doTestMain(
                "org/apache/camel/main/java/MyRoutesWithTempl*.java",
                null);
    }

    @Test
    public void testMainRoutesCollectorScanTwo() {
        // will load XML from target/classes when testing
        doTestMain(
                "org/apache/camel/main/java/MyRoutesWithTempl*.java",
                null);
    }

    @Test
    public void testMainRoutesCollectorScanWildcardDirClasspathPath() {
        // will load XML from target/classes when testing
        doTestMain(
                "org/apache/camel/main/**/MyRoutesWithTempl*.java",
                null);
    }

    @Test
    public void testMainRoutesCollectorScanClasspathPrefix() {
        // will load XML from target/classes when testing
        doTestMain(
                "classpath:org/apache/camel/main/java/MyRoutesWithTempl*.java",
                null);
    }

    protected void doTestMain(String includes, String excludes) {
        Main main = new Main();
        main.bind("restConsumerFactory", new MockRestConsumerFactory());
        main.configure().withRoutesIncludePattern(includes);
        main.configure().withRoutesExcludePattern(excludes);
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);
        assertEquals(0, camelContext.getRoutes().size());
        assertEquals(1, ((ModelCamelContext) camelContext).getRouteTemplateDefinitions().size());

        main.stop();
    }
}
