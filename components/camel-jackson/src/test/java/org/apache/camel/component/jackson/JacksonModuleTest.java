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
package org.apache.camel.component.jackson;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JacksonModuleTest extends CamelTestSupport {

    @Test
    public void testCustomModule() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).isEqualTo("{\"my-name\":\"Camel\",\"my-country\":\"Denmark\"}");

        TestOtherPojo pojo = new TestOtherPojo();
        pojo.setName("Camel");
        pojo.setCountry("Denmark");

        template.sendBody("direct:marshal", pojo);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                JacksonDataFormat format = new JacksonDataFormat();
                format.setInclude("NON_NULL");
                format.setModuleClassNames("org.apache.camel.component.jackson.MyModule");

                from("direct:marshal").marshal(format).to("mock:marshal");
            }
        };
    }

}
