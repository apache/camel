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
package org.apache.camel.test.main.junit6.sanitizer;

import org.apache.camel.BeanInject;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.sanitizer.SanitizerViolation;
import org.apache.camel.sanitizer.Violation;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.test.main.junit6.CamelMainTest;
import org.apache.camel.test.main.junit6.Configure;
import org.apache.camel.test.main.junit6.common.MyConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The exchange sanitizer in a @CamelMainTest test, whose extension builds the context in its beforeEach: the order of
 * the two annotations does not matter.
 */
@CamelMainTest
@CamelSanitizer
class SanitizerCamelMainTest {

    @BeanInject
    CamelContext context;

    @EndpointInject("direct:throws")
    ProducerTemplate template;

    @Configure
    protected void configure(MainConfigurationProperties configuration) {
        // the bean of the route of application.properties (routes/my-route.xml)
        configuration.addConfiguration(MyConfiguration.class);
        configuration.addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:throws").process(new UserCode.Throws()).id("throws");
            }
        });
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "throws")
    void aProcessorThatThrows() {
        template.send(e -> e.getMessage().setBody("x"));
        SanitizerViolation v = SanitizerCamelTestSupportTest.violation(context, Violation.THREW_BEFORE_CALLBACK);
        assertEquals(UserCode.Throws.class.getName(), v.getProcessor());
    }
}
