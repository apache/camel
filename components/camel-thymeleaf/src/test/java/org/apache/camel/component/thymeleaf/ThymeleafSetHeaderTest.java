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
package org.apache.camel.component.thymeleaf;

import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ThymeleafSetHeaderTest extends CamelSpringTestSupport {

    @Test
    public void testSendingApple() throws Exception {

        assertRespondsWith("apple", "I am an apple");
    }

    @Test
    public void testSendingOrange() throws Exception {

        assertRespondsWith("orange", "I am an orange");
    }

    protected void assertRespondsWith(final String value, String expectedBody) throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("fruit", value);
        mock.expectedBodyReceived().body().endsWith(expectedBody);
        template.request("direct:start", exchange -> {

            Message in = exchange.getIn();
            in.setBody(value);
        });
        mock.assertIsSatisfied();
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {

        return new ClassPathXmlApplicationContext("org/apache/camel/component/thymeleaf/camel-context.xml");
    }

}
