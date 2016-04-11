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

import org.junit.Test;

/**
 *
 */
public class ConfigAdminLoadConfigurationFileNotFoundTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/configadmin-loadfile.xml";
    }

    @Override
    public void setUp() throws Exception {
        try {
            super.setUp();
            fail("Should throw IllegalArgumentException, because the config file wasn't found");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("../../src/test/resources/etc/stuff.cfg"));
        }
    }

    // START SNIPPET: e1
    @Override
    protected String[] loadConfigAdminConfigurationFile() {
        // String[0] = tell Camel the path of the .cfg file to use for OSGi ConfigAdmin in the blueprint XML file
        //  this file should exist
        // String[1] = tell Camel the persistence-id of the cm:property-placeholder in the blueprint XML file
        return new String[]{"../../src/test/resources/etc/stuff.cfg", "stuff"};
    }
    // END SNIPPET: e1

    @Test
    public void test() throws Exception {
        // irrelevant
    }

}
