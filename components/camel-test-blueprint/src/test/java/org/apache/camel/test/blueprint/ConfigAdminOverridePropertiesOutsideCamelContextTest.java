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

import java.util.Dictionary;

import org.junit.Test;

/**
 *
 */
public class ConfigAdminOverridePropertiesOutsideCamelContextTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/configadmin-outside.xml";
    }

    // START SNIPPET: e1
    @Override
    protected String useOverridePropertiesWithConfigAdmin(Dictionary props) {
        // add the properties we want to override
        props.put("greeting", "Bye");

        // return the PID of the config-admin we are using in the blueprint xml file
        return "my-placeholders";
    }
    // END SNIPPET: e1

    @Test
    public void testConfigAdmin() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

}
