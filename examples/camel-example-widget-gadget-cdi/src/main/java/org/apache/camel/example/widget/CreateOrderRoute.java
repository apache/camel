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
package org.apache.camel.example.widget;

import org.apache.camel.builder.RouteBuilder;

/**
 * A simple route that routes orders from the file system to the ActiveMQ newOrder queue.
 */
public class CreateOrderRoute extends RouteBuilder {

    // we do not have to use @Inject to inject the endpoints
    // camel-cdi will auto detect all RouteBuilder classes and include those in the application

    @Override
    public void configure() throws Exception {
        // route files form src/data (noop = keep the file as-is after done, and do not pickup the same file again)
        from("file:src/data?noop=true")
            // route to the newOrder queue on the ActiveMQ broker
            .to("activemq:queue:newOrder");
    }
}
