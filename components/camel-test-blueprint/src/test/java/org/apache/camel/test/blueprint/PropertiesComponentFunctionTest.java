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
package org.apache.camel.test.blueprint;

import org.apache.camel.component.properties.PropertiesFunction;
import org.junit.Test;

public class PropertiesComponentFunctionTest extends CamelBlueprintTestSupport {

    public static final class MyFunction implements PropertiesFunction {

        @Override
        public String getName() {
            return "beer";
        }

        @Override
        public String apply(String remainder) {
            return "mock:" + remainder.toLowerCase();
        }
    }

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/PropertiesComponentFunctionTest.xml";
    }

    @Test
    public void testFunction() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

}

