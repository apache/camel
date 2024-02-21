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
package org.apache.camel.spring.config;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.TestSupport;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class XmlConfigTestSupport extends TestSupport {
    protected static final Logger LOG = LoggerFactory.getLogger(XmlConfigTestSupport.class);

    protected void assertValidContext(CamelContext context) {
        assertNotNull(context, "No context found!");

        List<RouteDefinition> routes = ((ModelCamelContext) context).getRouteDefinitions();
        LOG.debug("Found routes: {}", routes);

        assertEquals(1, routes.size(), "One Route should be found");

        for (RouteDefinition route : routes) {
            FromDefinition fromType = route.getInput();
            assertEquals("seda:test.a", fromType.getUri(), "from URI");

            List<?> outputs = route.getOutputs();
            assertEquals(1, outputs.size(), "Number of outputs");
        }
    }
}
