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
package org.apache.camel.processor.aggregator;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ServiceHelper;
import org.junit.After;
import org.junit.Before;

/**
 * @version
 */
public abstract class AbstractDistributedTest extends ContextTestSupport {

    protected CamelContext context2;
    protected ProducerTemplate template2;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        context.setUseMDCLogging(true);

        context2 = new DefaultCamelContext();
        context2.setUseMDCLogging(true);
        template2 = context2.createProducerTemplate();
        ServiceHelper.startServices(template2, context2);

        // add routes after CamelContext has been started
        context2.addRoutes(createRouteBuilder2());
    }

    @After
    public void tearDown() throws Exception {
        ServiceHelper.stopAndShutdownServices(context2, template2);

        super.tearDown();
    }

    protected MockEndpoint getMockEndpoint2(String uri) {
        return context2.getEndpoint(uri, MockEndpoint.class);
    }

    protected RouteBuilder createRouteBuilder2() throws Exception {
        return createRouteBuilder();
    }
}
