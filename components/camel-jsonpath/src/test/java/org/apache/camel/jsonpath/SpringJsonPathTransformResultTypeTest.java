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
package org.apache.camel.jsonpath;

import java.io.File;

import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringJsonPathTransformResultTypeTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/jsonpath/SpringJsonPathTransformResultTypeTest.xml");
    }

    @Test
    public void testPriceResultType() throws Exception {
        getMockEndpoint("mock:price").expectedMessageCount(4);
        getMockEndpoint("mock:price").message(0).body().isInstanceOf(Float.class);
        getMockEndpoint("mock:price").message(1).body().isInstanceOf(Double.class);
        getMockEndpoint("mock:price").message(2).body().isInstanceOf(Float.class);
        getMockEndpoint("mock:price").message(3).body().isInstanceOf(Double.class);

        template.sendBody("direct:start", new File("src/test/resources/books.json"));
        template.sendBody("direct:second", new File("src/test/resources/books.json"));
        template.sendBody("direct:start", new File("src/test/resources/books.json"));
        template.sendBody("direct:second", new File("src/test/resources/books.json"));

        assertMockEndpointsSatisfied();
    }

}
