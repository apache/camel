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
package org.apache.camel.parser.java;

import java.io.File;
import java.util.List;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.parser.RouteBuilderParser;
import org.apache.camel.parser.model.CamelNodeDetails;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RoasterJavaDslTwoRoutesTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RoasterJavaDslTwoRoutesTest.class);

    @Override
    public boolean isDumpRouteCoverage() {
        return true;
    }

    @Test
    void parseTree() throws Exception {
        JavaClassSource clazz = (JavaClassSource) Roaster
                .parse(new File("src/test/java/org/apache/camel/parser/java/TwoRoutesRouteBuilder.java"));

        List<CamelNodeDetails> list = RouteBuilderParser.parseRouteBuilderTree(clazz,
                "src/test/java/org/apache/camel/parser/java/TwoRoutesRouteBuilder.java", true);
        assertEquals(2, list.size());

        CamelNodeDetails details = list.get(0);
        CamelNodeDetails details2 = list.get(1);
        assertEquals("src/test/java/org/apache/camel/parser/java/TwoRoutesRouteBuilder.java", details.getFileName());
        assertEquals("src/test/java/org/apache/camel/parser/java/TwoRoutesRouteBuilder.java", details2.getFileName());

        assertEquals("foo", details.getRouteId());
        assertEquals("org.apache.camel.parser.java.TwoRoutesRouteBuilder", details.getClassName());
        assertEquals("configure", details.getMethodName());
        assertEquals("bar", details2.getRouteId());
        assertEquals("configure", details2.getMethodName());
        assertEquals("org.apache.camel.parser.java.TwoRoutesRouteBuilder", details2.getClassName());

        String tree = details.dump(0);
        LOG.info("\n{}", tree);

        String tree2 = details2.dump(0);
        LOG.info("\n{}", tree2);

        assertTrue(tree.contains("25\tfrom"));
        assertTrue(tree.contains("26\t  log"));
        assertTrue(tree.contains("27\t  to"));

        assertTrue(tree2.contains("29\tfrom"));
        assertTrue(tree2.contains("30\t  transform"));
        assertTrue(tree2.contains("31\t  to"));
    }

    @Test
    void testRouteCoverage() throws Exception {
        context.addRoutes(new TwoRoutesRouteBuilder());

        getMockEndpoint("mock:foo").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

}
