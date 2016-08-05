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
 * This is the first of two tests which will load a Blueprint .cfg file (which will initialize configadmin), containing
 * multiple property placeholders and also override its property placeholders directly (the change will reload blueprint
 * container).
 */
public class ConfigAdminLoadMultiConfigurationFileAndOverrideTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        // which blueprint XML file to use for this test
        return "org/apache/camel/test/blueprint/configadmin-loadmultifileoverride.xml";
    }

    @Override
    protected String[] loadConfigAdminConfigurationFile() {
        // which .cfg files to use, and their corresponding persistence-ids
        return new String[]{"src/test/resources/etc/stuff.cfg", "stuff", "src/test/resources/etc/otherstuff.cfg", "otherstuff"};
    }

    @Override
    protected String useOverridePropertiesWithConfigAdmin(Dictionary properties) throws Exception {
        // override / add extra properties
        properties.put("arrive", "mock:otherExtra");

        // return the persistence-id to use
        return "otherstuff";
    }

    @Test
    public void testConfigAdminWithMultiplePids() throws Exception {
        // mock:otherOriginal comes from <cm:default-properties>/<cm:property name="arrive" value="mock:otherOriginal" />
        getMockEndpoint("mock:otherOriginal").setExpectedMessageCount(0);
        // mock:result comes from loadConfigAdminConfigurationFile()
        getMockEndpoint("mock:otherResult").setExpectedMessageCount(0);
        // mock:extra comes from useOverridePropertiesWithConfigAdmin()
        getMockEndpoint("mock:otherExtra").expectedBodiesReceived("Adieu World", "tiens! Adieu Worldtiens! Adieu World");

        template.sendBody("direct:otherStart", "World");

        assertMockEndpointsSatisfied();
    }
}
