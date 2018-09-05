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

// START SNIPPET: e1
/**
 * This example will load a Blueprint .cfg file (which will initialize configadmin), and also override its property
 * placeholders from this unit test source code directly (the change will reload blueprint container).
 */
public class ConfigAdminLoadConfigurationFileAndOverrideTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        // which blueprint XML file to use for this test
        return "org/apache/camel/test/blueprint/configadmin-loadfileoverride.xml";
    }

    @Override
    protected String[] loadConfigAdminConfigurationFile() {
        // which .cfg file to use, and the name of the persistence-id
        return new String[]{"src/test/resources/etc/stuff.cfg", "stuff"};
    }

    @Override
    protected String useOverridePropertiesWithConfigAdmin(Dictionary props) throws Exception {
        // override / add extra properties
        props.put("destination", "mock:extra");

        // return the persistence-id to use
        return "stuff";
    }

    @Test
    public void testConfigAdmin() throws Exception {
        // mock:original comes from <cm:default-properties>/<cm:property name="destination" value="mock:original" />
        getMockEndpoint("mock:original").setExpectedMessageCount(0);
        // mock:result comes from loadConfigAdminConfigurationFile()
        getMockEndpoint("mock:result").setExpectedMessageCount(0);
        // mock:extra comes from useOverridePropertiesWithConfigAdmin()
        getMockEndpoint("mock:extra").expectedBodiesReceived("Bye World", "Yay Bye WorldYay Bye World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

}
// END SNIPPET: e1
