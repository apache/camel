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
package org.apache.camel.example.cdi;

import javax.ejb.Startup;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.Uri;

/**
 * Configures all our Camel routes, components, endpoints and beans
 */
@ContextName
@Startup
@ApplicationScoped
public class MyRoutes extends RouteBuilder {

    @Inject
    @Uri("file://target/testdata/result")
    private Endpoint resultEndpoint;

    @Inject
    private SomeBean someBean;

    @Override
    public void configure() throws Exception {
        // you can configure the route rule with Java DSL here

        // populate the message queue with some messages
        from("file:src/data?noop=true")
            .bean(someBean)
            .to(resultEndpoint);
    }

    public Endpoint getResultEndpoint() {
        return resultEndpoint;
    }
}
