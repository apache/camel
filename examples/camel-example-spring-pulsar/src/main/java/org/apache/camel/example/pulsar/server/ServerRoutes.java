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
package org.apache.camel.example.pulsar.server;

import org.apache.camel.builder.RouteBuilder;

/**
 * This class defines the routes on the Server. The class extends a base class in Camel {@link RouteBuilder}
 * that can be used to easily setup the routes in the configure() method.
 */
public class ServerRoutes extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // route from the numbers queue to our business that is a spring bean registered with the id=multiplier
        // Camel will introspect the multiplier bean and find the best candidate of the method to invoke.
        // As our multiplier bean only have one method its easy for Camel to find the method to use.
        from("pulsar:non-persistent://tn1/ns1/cameltest?subscriptionName=serversub&numberOfConsumers=1&consumerQueueSize=1")
                .to("multiplier")
                .to("log:INFO?showBody=true");

    }

}
