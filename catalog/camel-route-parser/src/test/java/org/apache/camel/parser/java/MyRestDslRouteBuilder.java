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
package org.apache.camel.parser.java;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestHostNameResolver;

public class MyRestDslRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        restConfiguration()
            .contextPath("myapi").port(1234)
            .component("jetty")
            .apiComponent("swagger")
            .apiHost("localhost")
            .apiContextPath("myapi/swagger")
            .skipBindingOnErrorCode(true)
            .scheme("https")
            .hostNameResolver(RestHostNameResolver.allLocalIp)
            .bindingMode(RestBindingMode.json)
            .componentProperty("foo", "123")
            .endpointProperty("pretty", "false")
            .consumerProperty("bar", "456")
            .corsHeaderProperty("key1", "value1")
            .corsHeaderProperty("key2", "value2");

        rest("/foo").consumes("xml").produces("json").description("my foo service")
            .get("{id}").apiDocs(false)
                .description("get by id")
                .to("log:id")
            .post().bindingMode(RestBindingMode.xml)
                .description("post something")
                .toD("log:post");
    }
}
