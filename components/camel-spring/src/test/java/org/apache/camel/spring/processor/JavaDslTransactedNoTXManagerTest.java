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
package org.apache.camel.spring.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class JavaDslTransactedNoTXManagerTest extends ContextTestSupport {

    public void testTransactedNoTXManager() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .transacted()
                    .to("mock:result");
            }
        });
        try {
            context.start();
            fail("Should have thrown an exception");
        } catch (FailedToCreateRouteException e) {
            NoSuchBeanException cause = assertIsInstanceOf(NoSuchBeanException.class, e.getCause());
            assertEquals("No bean could be found in the registry of type: PlatformTransactionManager", cause.getMessage());
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
