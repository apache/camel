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
package org.apache.camel.example.cdi.test;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.cdi.Beans;

/**
 * Example of a test route that can be reused across multiple test classes
 * using the {@code Beans} annotation.
 *
 * @see Beans#classes()
 */
class TestRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:out").to("mock:out");
    }
}