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
package org.apache.camel.language.datasonnet;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class DatasonnetAnnotationTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .bean(MyBean.class)
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testAnnotation() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello, DataSonnet. I am Camel");

        template.sendBody("direct:start", "DataSonnet");

        MockEndpoint.assertIsSatisfied(context);
    }

    public static class MyBean {

        public String hello(@Datasonnet("'Hello, ' + payload") String payload) {
            return payload + ". I am Camel";
        }
    }

}
