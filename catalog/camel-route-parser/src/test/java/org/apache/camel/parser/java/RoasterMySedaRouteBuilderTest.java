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

import org.apache.camel.parser.RouteBuilderParser;
import org.apache.camel.parser.model.CamelEndpointDetails;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoasterMySedaRouteBuilderTest {

    private static final Logger LOG = LoggerFactory.getLogger(RoasterMySedaRouteBuilderTest.class);

    @Test
    public void parse() throws Exception {
        JavaClassSource clazz = (JavaClassSource) Roaster.parse(new File("src/test/java/org/apache/camel/parser/java/MySedaRouteBuilder.java"));

        List<CamelEndpointDetails> details = new ArrayList<>();
        RouteBuilderParser.parseRouteBuilderEndpoints(clazz, ".", "src/test/java/org/apache/camel/parser/java/MySedaRouteBuilder.java", details);
        LOG.info("{}", details);

        Assert.assertEquals(7, details.size());
        Assert.assertEquals("32", details.get(1).getLineNumber());
        Assert.assertEquals("seda:foo", details.get(1).getEndpointUri());
        Assert.assertTrue(details.get(1).isConsumerOnly());
        Assert.assertFalse(details.get(1).isProducerOnly());
        Assert.assertEquals("35", details.get(2).getLineNumber());
        Assert.assertEquals("seda:bar", details.get(2).getEndpointUri());
        Assert.assertTrue(details.get(2).isConsumerOnly());
        Assert.assertFalse(details.get(2).isProducerOnly());
    }

}
