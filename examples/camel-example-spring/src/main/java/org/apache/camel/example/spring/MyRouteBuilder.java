/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.example.spring;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * A simple example router from an ActiveMQ queue to a file system
 *
 * @version $Revision: 1.1 $
 */
public class MyRouteBuilder extends RouteBuilder {
    public void configure() {
        from("activemq:test.MyQueue").to("file://test");

        // set up a listener on the file component
        from("file://test").process(new Processor() {
            public void process(Exchange e) {
                System.out.println("Received exchange: " + e.getIn());
            }
        });
    }
}
