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
package org.apache.camel.jsonpath;

import java.io.File;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringJsonPathSplitWriteAsStringTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/jsonpath/SpringJsonPathSplitWriteAsStringTest.xml");
    }

    @Test
    public void testSplitToJSon() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:line");
        mock.expectedMessageCount(2);
        // we want the output as JSon string
        mock.allMessages().body().isInstanceOf(String.class);
        mock.message(0).body().isEqualTo("{\"action\":\"CU\",\"id\":123,\"modifiedTime\":\"2015-07-28T11:40:09.520+02:00\"}");
        mock.message(1).body().isEqualTo("{\"action\":\"CU\",\"id\":456,\"modifiedTime\":\"2015-07-28T11:42:29.510+02:00\"}");

        template.sendBody("direct:start", new File("src/test/resources/content.json"));

        assertMockEndpointsSatisfied();
    }
}
