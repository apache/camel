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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class ClassComponentInvalidConfigurationTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testClassNotFound() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("class:org.apache.camel.component.bean.XXX")
                    .to("mock:result");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (FailedToCreateRouteException e) {
            ResolveEndpointFailedException cause = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
            ClassNotFoundException not = assertIsInstanceOf(ClassNotFoundException.class, cause.getCause());
            assertEquals("org.apache.camel.component.bean.XXX", not.getMessage());
        }
    }

    @Test
    public void testPropertyNotFoundOnClass() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("class:org.apache.camel.component.bean.MyPrefixBean?bean.foo=bar")
                    .to("mock:result");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (FailedToCreateRouteException e) {
            ResolveEndpointFailedException cause = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
            assertTrue(cause.getMessage().contains("Unknown parameters"));
            assertTrue(cause.getMessage().contains("foo=bar"));
        }
    }

}