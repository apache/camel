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

import java.util.Arrays;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.snakeyaml.model.TestPojo;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SnakeYAMLTypeFilterTest extends CamelTestSupport {
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
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // SafeConstructor
                SnakeYAMLDataFormat safeConstructorDf = new SnakeYAMLDataFormat();

                from("direct:safe-constructor")
                    .unmarshal(safeConstructorDf);

                // Type filter Constructor
                SnakeYAMLDataFormat typeConstructorDf = new SnakeYAMLDataFormat();
                typeConstructorDf.addTypeFilters(TypeFilters.types(TestPojo.class));

                from("direct:type-constructor")
                    .unmarshal(typeConstructorDf);

                // Type filter Constructor from string definitions
                SnakeYAMLDataFormat typeConstructorStrDf = new SnakeYAMLDataFormat();
                typeConstructorStrDf.setTypeFilterDefinitions(Arrays.asList(
                    "type:org.apache.camel.component.snakeyaml.model.TestPojo",
                    "regexp:org.apache.camel.component.snakeyaml.model.R.*"));

                from("direct:type-constructor-strdef")
                    .unmarshal(typeConstructorStrDf);

                // Allow all Constructor
                SnakeYAMLDataFormat allConstructorDf = new SnakeYAMLDataFormat();
                allConstructorDf.setAllowAnyType(true);

                from("direct:all-constructor")
                    .unmarshal(allConstructorDf)
                    .to("mock:all-constructor");
            }
        };
    }
}
