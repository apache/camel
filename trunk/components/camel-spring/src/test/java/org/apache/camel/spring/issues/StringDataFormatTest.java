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
package org.apache.camel.spring.issues;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Spring bases data format unit test.
 */
public class StringDataFormatTest extends SpringTestSupport {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/issues/stringDataFormatTest.xml");
    }

    public void testMarshalString() throws Exception {
        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        String body = "Hello Thai Elephant \u0E08";

        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedMessageCount(1);

        byte[] out = (byte[]) template.requestBody("direct:marshal", body);
        assertMockEndpointsSatisfied();

        String result = new String(out, "UTF-8");
        assertEquals(body, result);
    }

    public void testUnMarshalString() throws Exception {
        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        byte[] body = "Hello Thai Elephant \u0E08".getBytes();

        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        String out = (String) template.requestBody("direct:unmarshal", body);
        assertMockEndpointsSatisfied();

        assertEquals(new String(body, "UTF-8"), out);
    }

}