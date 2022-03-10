/*
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
import java.util.Collection;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.snakeyaml.model.TestPojo;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.junit5.params.Parameter;
import org.apache.camel.test.junit5.params.Parameterized;
import org.apache.camel.test.junit5.params.Parameters;
import org.apache.camel.test.junit5.params.Test;
import org.yaml.snakeyaml.nodes.Tag;

import static org.apache.camel.component.snakeyaml.SnakeYAMLTestHelper.createClassTagDataFormat;
import static org.apache.camel.component.snakeyaml.SnakeYAMLTestHelper.createDataFormat;
import static org.apache.camel.component.snakeyaml.SnakeYAMLTestHelper.createPrettyFlowDataFormat;
import static org.apache.camel.component.snakeyaml.SnakeYAMLTestHelper.createTestMap;
import static org.apache.camel.component.snakeyaml.SnakeYAMLTestHelper.createTestPojo;

@Parameterized
public class SnakeYAMLTest extends CamelTestSupport {

    @Parameter
    private SnakeYAMLDataFormat format;
    @Parameter(1)
    private Object body;
    @Parameter(2)
    private String expected;

    @Parameters
    public static Collection yamlCases() {
        return Arrays.asList(new Object[][] {
                {
                        createDataFormat(null),
                        createTestMap(),
                        "{name: Camel}"
                },
                {
                        createDataFormat(TestPojo.class),
                        createTestPojo(),
                        "!!org.apache.camel.component.snakeyaml.model.TestPojo {name: Camel}"
                },
                {
                        createPrettyFlowDataFormat(TestPojo.class, true),
                        createTestPojo(),
                        "!!org.apache.camel.component.snakeyaml.model.TestPojo {\n  name: Camel\n}"
                },
                {
                        createClassTagDataFormat(TestPojo.class, new Tag("!tpojo")),
                        createTestPojo(),
                        "!tpojo {name: Camel}"
                }
        });
    }

    @Test
    public void testMarshalAndUnmarshal() throws Exception {
        SnakeYAMLTestHelper.marshalAndUnmarshal(
                context(),
                body,
                "mock:reverse",
                "direct:in",
                "direct:back",
                expected);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in")
                        .marshal(format);
                from("direct:back")
                        .unmarshal(format)
                        .to("mock:reverse");
            }
        };
    }
}
