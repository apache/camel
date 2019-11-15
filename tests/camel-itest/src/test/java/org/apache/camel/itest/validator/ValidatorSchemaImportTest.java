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
package org.apache.camel.itest.validator;

import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class ValidatorSchemaImportTest extends CamelTestSupport {

    protected MockEndpoint validEndpoint;
    protected MockEndpoint finallyEndpoint;
    protected MockEndpoint invalidEndpoint;

    /**
     * Test for the valid schema location
     * @throws Exception
     */
    @Test
    public void testRelativeParentSchemaImport() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .doTry()
                        .to("validator:org/apache/camel/component/validator/relativeparent/child/child.xsd")
                        .to("mock:valid")
                    .doCatch(ValidationException.class)
                        .to("mock:invalid")
                    .doFinally()
                        .to("mock:finally")
                    .end();
            }
        });
        validEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start",
                "<childuser xmlns='http://foo.com/bar'><user><id>1</id><username>Test User</username></user></childuser>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }
    
    /**
     * Test for the invalid schema import location.
     * 
     * @throws Exception
     */
    @Test
    public void testDotSlashSchemaImport() throws Exception {
        this.context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").doTry()
                    .to("validator:org/apache/camel/component/validator/dotslash/child.xsd").to("mock:valid")
                    .doCatch(ValidationException.class).to("mock:invalid").doFinally().to("mock:finally")
                    .end();
            }
        });
        validEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template
            .sendBody("direct:start",
                      "<childuser xmlns='http://foo.com/bar'><user><id>1</id><username>Test User</username></user></childuser>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    /**
     * Test for the invalid schema import location.
     * 
     * @throws Exception
     */
    @Test
    public void testRelativeDoubleSlashSchemaImport() throws Exception {
        this.context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").doTry()
                    .to("validator:org/apache/camel/component/validator/doubleslash/child.xsd")
                    .to("mock:valid").doCatch(ValidationException.class).to("mock:invalid").doFinally()
                    .to("mock:finally").end();
            }
        });
        validEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template
            .sendBody("direct:start",
                      "<childuser xmlns='http://foo.com/bar'><user><id>1</id><username>Test User</username></user></childuser>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }
    
    /**
     * Test for the valid schema location relative to a path other than the validating schema
     * @throws Exception
     */
    @Test
    public void testChildParentUncleSchemaImport() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .doTry()
                        .to("validator:org/apache/camel/component/validator/childparentuncle/child/child.xsd")
                        .to("mock:valid")
                    .doCatch(ValidationException.class)
                        .to("mock:invalid")
                    .doFinally()
                        .to("mock:finally")
                    .end();
            }
        });
        validEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start",
                "<childuser xmlns='http://foo.com/bar'><user><id>1</id><username>Test User</username></user></childuser>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        validEndpoint = resolveMandatoryEndpoint("mock:valid", MockEndpoint.class);
        invalidEndpoint = resolveMandatoryEndpoint("mock:invalid", MockEndpoint.class);
        finallyEndpoint = resolveMandatoryEndpoint("mock:finally", MockEndpoint.class);
    }
}
