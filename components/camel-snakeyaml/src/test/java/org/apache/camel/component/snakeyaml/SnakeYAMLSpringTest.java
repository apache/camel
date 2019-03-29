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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext
public class SnakeYAMLSpringTest extends CamelSpringTestSupport {
    @Test
    public void testMarshalAndUnmarshalMap() throws Exception {
        Map<String, String> in = new HashMap<>();
        in.put("name", "Camel");

        SnakeYAMLTestHelper.marshalAndUnmarshal(
            context(),
            SnakeYAMLTestHelper.createTestMap(),
            "mock:reverse",
            "direct:in",
            "direct:back",
            "{name: Camel}"
        );
    }

    @Test
    public void testMarshalAndUnmarshalPojo() throws Exception {
        SnakeYAMLTestHelper.marshalAndUnmarshal(
            context(),
            SnakeYAMLTestHelper.createTestPojo(),
            "mock:reversePojo",
            "direct:inPojo",
            "direct:backPojo",
            "!!org.apache.camel.component.snakeyaml.model.TestPojo {name: Camel}"
        );
    }

    @Test
    public void testMarshalAndUnmarshalPojoWithPrettyFlow() throws Exception {
        SnakeYAMLTestHelper.marshalAndUnmarshal(
            context(),
            SnakeYAMLTestHelper.createTestPojo(),
            "mock:reversePojoWithPrettyFlow",
            "direct:inPojoWithPrettyFlow",
            "direct:backPojoWithPrettyFlow",
            "!!org.apache.camel.component.snakeyaml.model.TestPojo {\n  name: Camel\n}"
        );
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/snakeyaml/SnakeYAMLSpringTest.xml");
    }
}
