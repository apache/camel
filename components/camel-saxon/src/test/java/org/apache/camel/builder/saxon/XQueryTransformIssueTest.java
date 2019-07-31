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
package org.apache.camel.builder.saxon;

import java.io.FileInputStream;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.Test;

public class XQueryTransformIssueTest extends CamelTestSupport {

    @Test
    public void testTransform() throws Exception {
        String data = IOHelper.loadText(new FileInputStream("src/test/resources/myinput.xml"));

        getMockEndpoint("mock:result").expectedBodiesReceived("123TestConcat");

        template.sendBody("direct:start", data);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .transform().xquery("concat(/Envelope/Body/getEmployee/EmpId/text(),\"TestConcat\")", String.class)
                    .to("log:info")
                    .to("mock:result");
            }
        };
    }
}
