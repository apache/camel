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
 * A test showing that if Blueprint XML contains property placeholders, some property source has to be defined.
 */
public class ConfigAdminInitialConfigurationUsingPropertiesTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/configadmin-endpoint-no-defaults.xml";
    }

    @Override
    protected String setConfigAdminInitialConfiguration(Properties props) {
        props.put("greeting", "Bye");
        props.put("destination", "mock:result");
        return "my-placeholders";
    }

    @Test
    public void testConfigAdmin() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        template.sendBody("direct:start", "World");
        assertMockEndpointsSatisfied();
    }

}
