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
package org.apache.camel.example;

import org.apache.camel.builder.RouteBuilder;

public class DataFormatComponentTest extends DataFormatTest {
    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").
                        to("dataformat:jaxb:marshal?contextPath=org.apache.camel.example").
                        to("direct:marshalled");

                from("direct:marshalled").
                        to("dataformat:jaxb:unmarshal?contextPath=org.apache.camel.example").
                        to("mock:result");
                
                from("direct:prettyPrint").
                        to("dataformat:jaxb:marshal?contextPath=org.apache.camel.foo.bar&prettyPrint=true").
                        to("mock:result");
            }
        };
    }

}