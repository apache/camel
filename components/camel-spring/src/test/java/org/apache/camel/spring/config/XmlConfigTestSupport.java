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
package org.apache.camel.spring.config;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.TestSupport;
import org.apache.camel.model.FromType;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.RouteType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class XmlConfigTestSupport extends TestSupport {
    protected static final transient Log LOG = LogFactory.getLog(CamelContextFactoryBeanTest.class);

    protected void assertValidContext(CamelContext context) {
        assertNotNull("No context found!", context);

        List<RouteType> routes = context.getRouteDefinitions();
        LOG.debug("Found routes: " + routes);

        assertEquals("One Route should be found", 1, routes.size());

        for (RouteType route : routes) {
            List<FromType> inputs = route.getInputs();
            assertEquals("Number of inputs", 1, inputs.size());
            FromType fromType = inputs.get(0);
            assertEquals("from URI", "seda:test.a", fromType.getEndpoint().getEndpointUri());

            List<ProcessorType<?>> outputs = route.getOutputs();
            assertEquals("Number of outputs", 1, outputs.size());
        }
    }
}
