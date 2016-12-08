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

package org.apache.camel.component.snakeyaml;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Test;

public class SnakeYAMLBlueprintTypeFilterTest extends CamelBlueprintTestSupport {
    @Test
    public void testSafeConstructor() throws Exception {
        SnakeYAMLTypeFilterHelper.testSafeConstructor(template);
    }

    @Test
    public void testTypeConstructor() throws Exception {
        SnakeYAMLTypeFilterHelper.testTypeConstructor(template);
    }

    @Test
    public void testTypeConstructorFromDefinition() throws Exception {
        SnakeYAMLTypeFilterHelper.testTypeConstructorFromDefinition(template);
    }

    @Test
    public void testAllowAllConstructor() throws Exception {
        SnakeYAMLTypeFilterHelper.testAllowAllConstructor(template);
    }

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/blueprint-typefilter.xml";
    }
}
