/**
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
package org.apache.camel.parser.java;

import java.io.File;
import java.util.List;

import org.apache.camel.parser.RouteBuilderParser;
import org.apache.camel.parser.model.CamelNodeDetails;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoasterJavaDslTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RoasterJavaDslTest.class);

    @Override
    public boolean isDumpRouteCoverage() {
        return true;
    }

    @Test
    public void parseTree() throws Exception {
        JavaClassSource clazz = (JavaClassSource) Roaster.parse(new File("src/test/java/org/apache/camel/parser/java/MyJavaDslRouteBuilder.java"));

        List<CamelNodeDetails> list = RouteBuilderParser.parseRouteBuilderTree(clazz, ".",
            "src/test/java/org/apache/camel/parser/java/MyJavaDslRouteBuilder.java", true);
        assertEquals(1, list.size());
        CamelNodeDetails details = list.get(0);
        assertEquals("src/test/java/org/apache/camel/parser/java/MyJavaDslRouteBuilder.java", details.getFileName());
        assertEquals("bar", details.getRouteId());
        assertEquals("configure", details.getMethodName());
        assertEquals("org.apache.camel.parser.java.MyJavaDslRouteBuilder", details.getClassName());

        String tree = details.dump(0);
        LOG.info("\n" + tree);

        assertTrue(tree.contains("28\tfrom"));
        assertTrue(tree.contains("29\t  log"));
        assertTrue(tree.contains("30\t  setHeader"));
        assertTrue(tree.contains("31\t  choice"));
        assertTrue(tree.contains("33\t    to"));
        assertTrue(tree.contains("34\t    toD"));
        assertTrue(tree.contains("36\t    toD"));
        assertTrue(tree.contains("38\t    log"));
        assertTrue(tree.contains("40\t  to"));
    }

    @Test
    public void testRouteCoverage() throws Exception {
        context.addRoutes(new MyJavaDslRouteBuilder());

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
