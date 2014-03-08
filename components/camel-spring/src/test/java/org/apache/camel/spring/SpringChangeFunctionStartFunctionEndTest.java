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
package org.apache.camel.spring;

import org.apache.camel.language.simple.SimpleLanguage;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Tests changing the function start and function end tokens using a spring bean.
 */
public class SpringChangeFunctionStartFunctionEndTest extends SpringTestSupport {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/SpringChangeFunctionStartFunctionEndTest.xml");
    }

    public void testSimpleWithPrefix() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Custom tokens is cool");

        template.sendBody("direct:start", "Custom tokens");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void tearDown() throws Exception {
        // change the tokens back to default after testing
        SimpleLanguage.changeFunctionStartToken("${", "$simple{");
        SimpleLanguage.changeFunctionEndToken("}");

        super.tearDown();
    }
}
