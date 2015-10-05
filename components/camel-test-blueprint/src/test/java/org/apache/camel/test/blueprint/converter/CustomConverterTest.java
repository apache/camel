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
package org.apache.camel.test.blueprint.converter;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.camel.test.blueprint.Foo;
import org.junit.Test;

public class CustomConverterTest extends CamelBlueprintTestSupport {
    
    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/converter/CustomConverterTest.xml";
    }

    @Test
    public void testCustomConverter() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().isInstanceOf(Foo.class);

        template.sendBody("direct:start", "John,Doe");

        assertMockEndpointsSatisfied();

        Foo foo = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getIn().getBody(Foo.class);
        assertEquals("John", foo.getFirst());
        assertEquals("Doe", foo.getLast());
    }

}
