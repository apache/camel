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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.FileUtil;

/**
 *
 */
public class FileValidatorRouteTest extends ContextTestSupport {

    protected MockEndpoint validEndpoint;
    protected MockEndpoint finallyEndpoint;
    protected MockEndpoint invalidEndpoint;

    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("file:target/validator",
                "<mail xmlns='http://foo.com/bar'><subject>Hey</subject><body>Hello world!</body></mail>",
                Exchange.FILE_NAME, "valid.xml");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);

        // should be able to delete the file
        oneExchangeDone.matchesMockWaitTime();
        assertTrue("Should be able to delete the file", FileUtil.deleteFile(new File("target/validator/valid.xml")));
    }

    public void testInvalidMessage() throws Exception {
        invalidEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("file:target/validator",
                "<mail xmlns='http://foo.com/bar'><body>Hello world!</body></mail>",
                Exchange.FILE_NAME, "invalid.xml");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);

        // should be able to delete the file
        oneExchangeDone.matchesMockWaitTime();
        assertTrue("Should be able to delete the file", FileUtil.deleteFile(new File("target/validator/invalid.xml")));
    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/validator");
        super.setUp();
        validEndpoint = resolveMandatoryEndpoint("mock:valid", MockEndpoint.class);
        invalidEndpoint = resolveMandatoryEndpoint("mock:invalid", MockEndpoint.class);
        finallyEndpoint = resolveMandatoryEndpoint("mock:finally", MockEndpoint.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/validator?noop=true")
                    .doTry()
                        .to("validator:org/apache/camel/component/validator/schema.xsd")
                        .to("mock:valid")
                    .doCatch(ValidationException.class)
                        .to("mock:invalid")
                    .doFinally()
                        .to("mock:finally")
                    .end();
            }
        };
    }

}
