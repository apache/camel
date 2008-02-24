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
package org.apache.camel.component.validator;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision$
 */
public class ValidatorRouteTest extends SpringTestSupport {
    protected MockEndpoint validEndpoint;
    protected MockEndpoint invalidEndpoint;

    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start",
                "<mail xmlns='http://foo.com/bar'><subject>Hey</subject><body>Hello world!</body></mail>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint);
    }

    public void testInvalidMessage() throws Exception {
        invalidEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start",
                "<mail xmlns='http://foo.com/bar'><body>Hello world!</body></mail>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        validEndpoint = resolveMandatoryEndpoint("mock:valid", MockEndpoint.class);
        invalidEndpoint = resolveMandatoryEndpoint("mock:invalid", MockEndpoint.class);
    }

    protected int getExpectedRouteCount() {
        // TODO why zero?
        return 0;
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/validator/camelContext.xml");
    }
}