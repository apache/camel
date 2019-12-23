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

import javax.inject.Inject;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.Uri;

public class MyCdiConcatRouteBuilder extends RouteBuilder {

    private static final int DELAY = 4999;
    private static final int PORT = 80;

    @Inject
    @Uri("timer:foo?period=" + DELAY)
    private Endpoint inputEndpoint;

    @Inject
    @Uri("log:a")
    private Endpoint loga;

    @EndpointInject("netty-http:http:someserver:" + PORT + "/hello")
    private Endpoint mynetty;

    @Override
    public void configure() throws Exception {
        from(inputEndpoint)
            .log("I was here")
            .to(loga)
            .to(mynetty);
    }
}
