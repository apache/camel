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

import java.util.Properties;

import org.junit.Test;

/**
 *
 */
public class ConfigAdminOverridePropertiesTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/configadmin.xml";
    }

    // START SNIPPET: e1
    // override this method to provide our custom properties we use in this unit test
    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties extra = new Properties();
        extra.put("destination", "mock:extra");
        extra.put("greeting", "Bye");
        return extra;
    }
    // END SNIPPET: e1

    @Test
    public void testConfigAdmin() throws Exception {
        getMockEndpoint("mock:extra").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

}
