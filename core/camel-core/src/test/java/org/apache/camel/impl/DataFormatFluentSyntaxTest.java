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
package org.apache.camel.impl;

import org.apache.camel.builder.RouteBuilder;

/**
 * A unit test class ensuring that the fluent syntax to create data format works as expected.
 */
public class DataFormatFluentSyntaxTest extends RefDataFormatTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // START SNIPPET: e1
                from("direct:a").marshal(
                    dataFormat().custom().ref("reverse").end()
                ).to("mock:a");

                from("direct:b").unmarshal(
                    dataFormat().custom().ref("reverse").end()
                ).to("mock:b");
                // END SNIPPET: e1
            }
        };
    }
}
