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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.parser.ParserResult;
import org.apache.camel.parser.RouteBuilderParser;
import org.apache.camel.parser.helper.CamelJavaParserHelper;
import org.apache.camel.parser.model.CamelEndpointDetails;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoasterEndpointInjectTest {

    private static final Logger LOG = LoggerFactory.getLogger(RoasterEndpointInjectTest.class);

    @Test
    public void parse() throws Exception {
        JavaClassSource clazz = (JavaClassSource) Roaster.parse(new File("src/test/java/org/apache/camel/parser/java/MyCdiRouteBuilder.java"));
        MethodSource<JavaClassSource> method = CamelJavaParserHelper.findConfigureMethod(clazz);

        List<CamelEndpointDetails> details = new ArrayList<>();
        RouteBuilderParser.parseRouteBuilderEndpoints(clazz, ".", "src/test/java/org/apache/camel/parser/java/MyCdiRouteBuilder.java", details);
        LOG.info("{}", details);

        Assert.assertEquals("timer:foo?period=4999", details.get(0).getEndpointUri());
        Assert.assertEquals("28", details.get(0).getLineNumber());

        Assert.assertEquals("log:a", details.get(1).getEndpointUri());
        Assert.assertEquals("32", details.get(1).getLineNumber());

        Assert.assertEquals("netty4-http:http:someserver:80/hello", details.get(2).getEndpointUri());
        Assert.assertEquals("36", details.get(2).getLineNumber());

        List<ParserResult> list = CamelJavaParserHelper.parseCamelConsumerUris(method, true, true);
        for (ParserResult result : list) {
            LOG.info("Consumer: " + result.getElement());
        }
        Assert.assertEquals("timer:foo?period=4999", list.get(0).getElement());

        list = CamelJavaParserHelper.parseCamelProducerUris(method, true, true);
        for (ParserResult result : list) {
            LOG.info("Producer: " + result.getElement());
        }
        Assert.assertEquals(2, list.size());

        Assert.assertEquals(5, details.size());
        Assert.assertEquals("log:a", details.get(3).getEndpointUri());
    }

}
