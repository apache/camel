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
 * This example will load a Blueprint .cfg file, and also override its property placeholders from this unit test
 * source code directly.
 * But having <code>update-strategy="none"</code> means that BP container won't be reloaded
 */
public class ConfigAdminNoReloadLoadConfigurationFileAndOverrideTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        // which blueprint XML file to use for this test
        return "org/apache/camel/test/blueprint/configadmin-no-reload-loadfileoverride.xml";
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
        // regular unit test method
        getMockEndpoint("mock:original").expectedBodiesReceived("Hello World", "Hey Hello WorldHey Hello World");
        getMockEndpoint("mock:extra").setExpectedMessageCount(0);

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

}
// END SNIPPET: e1
