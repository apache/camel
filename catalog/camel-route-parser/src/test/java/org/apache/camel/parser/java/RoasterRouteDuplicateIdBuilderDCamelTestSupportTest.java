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

import org.apache.camel.parser.ParserResult;
import org.apache.camel.parser.helper.CamelJavaParserHelper;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoasterRouteDuplicateIdBuilderDCamelTestSupportTest {

    private static final Logger LOG = LoggerFactory.getLogger(RoasterRouteDuplicateIdBuilderDCamelTestSupportTest.class);

    @Test
    void parse() throws Exception {
        JavaClassSource clazz = (JavaClassSource) Roaster
                .parse(new File("src/test/java/org/apache/camel/parser/java/MyRouteDuplicateIdTest.java"));
        MethodSource<JavaClassSource> method = CamelJavaParserHelper.findConfigureMethod(clazz);

        List<ParserResult> list = CamelJavaParserHelper.parseCamelConsumerUris(method, true, false);
        for (ParserResult result : list) {
            LOG.info("Consumer: {}", result.getElement());
        }
        assertEquals(3, list.size());

        list = CamelJavaParserHelper.parseCamelRouteIds(method);
        for (ParserResult result : list) {
            LOG.info("Route id: {}", result.getElement());
        }
        assertEquals(3, list.size());
        assertEquals("foo", list.get(0).getElement());
        assertEquals("bar", list.get(1).getElement());
        assertEquals("foo", list.get(2).getElement());
    }

}
