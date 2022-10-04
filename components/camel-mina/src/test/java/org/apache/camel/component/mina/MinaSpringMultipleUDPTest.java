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
package org.apache.camel.component.mina;

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.awaitility.Awaitility.await;

/**
 *
 */
public class MinaSpringMultipleUDPTest extends CamelSpringTestSupport {

    private static final String LS = System.lineSeparator();

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/mina/SpringMultipleUDPTest-context.xml");
    }

    @Test
    public void testMinaSpringProtobufEndpoint() {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(7);

        for (int i = 0; i < 7; i++) {
            template.requestBody("myMinaEndpoint", "Hello World" + i + LS);
        }

        // Sleep for awhile to let the messages go through.
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> MockEndpoint.assertIsSatisfied(context));
    }
}
