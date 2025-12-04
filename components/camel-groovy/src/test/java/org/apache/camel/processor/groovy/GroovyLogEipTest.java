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

package org.apache.camel.processor.groovy;

import static org.junit.jupiter.api.Assertions.fail;

import groovy.lang.MissingPropertyException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GroovyLogEipTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getGlobalOptions().put(Exchange.LOG_EIP_LANGUAGE, "groovy");
        return context;
    }

    @Test
    public void testLogOkay() {
        template.sendBody("direct:start", 3);
    }

    @Test
    public void testLogFail() {
        try {
            template.sendBody("direct:fail", 4);
            fail("Should fail");
        } catch (Exception e) {
            Assertions.assertInstanceOf(MissingPropertyException.class, e.getCause());
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").log("body").log("body * 2").log("'Hello ${body}'");

                from("direct:fail").log("XXX");
            }
        };
    }
}
