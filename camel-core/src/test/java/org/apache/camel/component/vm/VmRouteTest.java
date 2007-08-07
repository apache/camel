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
package org.apache.camel.component.vm;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelTemplate;
import org.apache.camel.TestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ServiceHelper;

/**
 * @version $Revision: 520220 $
 */
public class VmRouteTest extends TestSupport {
    private CamelContext context1 = new DefaultCamelContext();
    private CamelContext context2 = new DefaultCamelContext();
    private CamelTemplate template = new CamelTemplate(context1);
    private Object expectedBody = "<hello>world!</hello>";

    public void testSedaQueue() throws Exception {
        MockEndpoint result = context2.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived(expectedBody);

        template.sendBody("vm:test.a", expectedBody);

        result.assertIsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        context1.addRoutes(new RouteBuilder() {
            public void configure() {
                from("vm:test.a").to("vm:test.b");
            }
        });

        context2.addRoutes(new RouteBuilder() {
            public void configure() {
                from("vm:test.b").to("mock:result");
            }
        });

        ServiceHelper.startServices(context1, context2);
    }

    @Override
    protected void tearDown() throws Exception {
        ServiceHelper.stopServices(context2, context1);
        super.tearDown();
    }
}