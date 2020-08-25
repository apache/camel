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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.parser.ParserResult;
import org.apache.camel.parser.RouteBuilderParser;
import org.apache.camel.parser.helper.CamelJavaParserHelper;
import org.apache.camel.parser.model.CamelEndpointDetails;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoasterEndpointInjectTest {

    private static final Logger LOG = LoggerFactory.getLogger(RoasterEndpointInjectTest.class);

    @Test
    void parse() throws Exception {
        JavaClassSource clazz = (JavaClassSource) Roaster
                .parse(new File("src/test/java/org/apache/camel/parser/java/MyCdiRouteBuilder.java"));
        MethodSource<JavaClassSource> method = CamelJavaParserHelper.findConfigureMethod(clazz);

        List<CamelEndpointDetails> details = new ArrayList<>();
        RouteBuilderParser.parseRouteBuilderEndpoints(clazz, ".",
                "src/test/java/org/apache/camel/parser/java/MyCdiRouteBuilder.java", details);
        LOG.info("{}", details);

        assertEquals("timer:foo?period=4999", details.get(0).getEndpointUri());
        assertEquals("28", details.get(0).getLineNumber());
        assertEquals("28", details.get(0).getLineNumberEnd());
        assertEquals(1061, details.get(0).getAbsolutePosition());
        assertEquals(10, details.get(0).getLinePosition());

        assertEquals("log:a", details.get(1).getEndpointUri());
        assertEquals("32", details.get(1).getLineNumber());
        assertEquals("32", details.get(1).getLineNumberEnd());
        assertEquals(1144, details.get(1).getAbsolutePosition());
        assertEquals(10, details.get(1).getLinePosition());

        assertEquals("netty-http:http:someserver:80/hello", details.get(2).getEndpointUri());
        assertEquals("36", details.get(2).getLineNumber());
        assertEquals("36", details.get(2).getLineNumberEnd());
        assertEquals(1202, details.get(2).getAbsolutePosition());
        assertEquals(10, details.get(2).getLinePosition());

        // spans 2 lines
        assertEquals("mock:foo?retainFirst=1", details.get(6).getEndpointUri());
        assertEquals("45", details.get(6).getLineNumber());
        assertEquals("46", details.get(6).getLineNumberEnd());
        assertEquals(1454, details.get(6).getAbsolutePosition());
        assertEquals(21, details.get(6).getLinePosition());

        List<ParserResult> list = CamelJavaParserHelper.parseCamelConsumerUris(method, true, true);
        for (ParserResult result : list) {
            LOG.info("Consumer: " + result.getElement());
        }
        assertEquals("timer:foo?period=4999", list.get(0).getElement());

        list = CamelJavaParserHelper.parseCamelProducerUris(method, true, true);
        for (ParserResult result : list) {
            LOG.info("Producer: " + result.getElement());
        }
        assertEquals(3, list.size());

        assertEquals(7, details.size());
        assertEquals("log:a", details.get(4).getEndpointUri());
    }

}
