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
package org.apache.camel.model;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test to verify end-user exceptions for miss configuration
 */
public class ProcessorTypeConfigurationTest extends ContextTestSupport {

    public void testProcessorRefMissConfigured() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                public void configure() throws Exception {
                    from("direct:in").processRef("hello");
                }
            });
            fail("Should have thrown RuntimeCamelException");
        } catch (IllegalArgumentException e) {
            assertEquals("target not provided on node: process[ref: hello]", e.getMessage());
        }
    }

}
