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

import static org.hamcrest.CoreMatchers.equalTo;

/**
 * A test showing that Blueprint XML property placeholders work correctly with
 * augmented property names and fallback behavior.
 */
public class BlueprintAugmentedPropertiesNoFallbackTest extends CamelBlueprintTestSupport {

    @Override
    public void setUp() throws Exception {
        try {
            super.setUp();
            fail("Should fail, because Blueprint XML uses property placeholders, but we didn't resolve the placeholder");
        } catch (Exception e) {
            assertThat(e.getCause().getCause().getCause().getMessage(), equalTo("Property with key [TESTYYY.source] not found in properties from text: {{source}}"));
        }
    }

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/augmented-properties-no-fallback.xml";
    }

    @Override
    protected String[] loadConfigAdminConfigurationFile() {
        return new String[] {"src/test/resources/etc/augmented.no.fallback.cfg", "augmented.no.fallback"};
    }

    @Test
    public void testFallbackToUnaugmentedProperty() throws Exception {
    }

}
