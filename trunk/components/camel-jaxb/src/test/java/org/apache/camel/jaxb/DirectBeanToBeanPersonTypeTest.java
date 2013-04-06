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
package org.apache.camel.jaxb;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.foo.bar.PersonType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * PersonType has a ObjectFactory so JAXB can convert to it, but we should still route it as is
 */
public class DirectBeanToBeanPersonTypeTest extends CamelTestSupport {

    @Test
    public void testBeanToBean() throws Exception {
        getMockEndpoint("mock:person").expectedMessageCount(1);
        getMockEndpoint("mock:person").message(0).body().isInstanceOf(PersonType.class);

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .bean(MyPersonService.class, "createPerson")
                    .bean(MyPersonService.class, "sendPerson");
            }
        };
    }
}
