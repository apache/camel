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
package org.apache.camel.example.guice.jms;

import org.apache.camel.builder.RouteBuilder;

/**
 * A simple example router from a file system to an ActiveMQ queue and then to a file system
 *
 * @version 
 */
public class MyRouteBuilder extends RouteBuilder {

    public void configure() {
        // populate the message queue with some messages
        from("file:src/data?noop=true").to("jms:test.MyQueue");

        from("jms:test.MyQueue").to("file://target/routeOutput");

        // set up a listener on the file component
        from("file://target/routeOutput?noop=true").bean("myBean");
    }

}