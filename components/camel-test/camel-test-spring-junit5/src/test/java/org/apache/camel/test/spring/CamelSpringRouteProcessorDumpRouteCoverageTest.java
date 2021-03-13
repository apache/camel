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
package org.apache.camel.test.spring;

import java.io.File;

import org.apache.camel.management.JmxManagementStrategy;
import org.apache.camel.test.junit5.TestSupport;
import org.apache.camel.test.spring.junit5.EnableRouteCoverage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableRouteCoverage
public class CamelSpringRouteProcessorDumpRouteCoverageTest extends CamelSpringPlainTest {

    @BeforeAll
    public static void prepareFiles() throws Exception {
        TestSupport.deleteDirectory("target/camel-route-coverage");
    }

    @Override
    @Test
    public void testJmx() {
        // JMX is enabled with route coverage
        assertEquals(JmxManagementStrategy.class, camelContext.getManagementStrategy().getClass());
    }

    @Override
    public void testRouteCoverage() {
        camelContext.stop();

        // there should be files
        String[] names = new File("target/camel-route-coverage").list();
        assertNotNull(names);
        assertTrue(names.length > 0);
    }

}
