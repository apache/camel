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
package org.apache.camel.example.jmx;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;

/**
 * A simple example router demonstrating the camel-jmx component.
 */
public class MyRouteBuilder extends RouteBuilder {

    private SimpleBean bean;
    private MBeanServer server;

    public MyRouteBuilder() throws Exception {
        server = ManagementFactory.getPlatformMBeanServer();
        bean = new SimpleBean();
        // START SNIPPET: e2
        server.registerMBean(bean, new ObjectName("jmxExample", "name", "simpleBean"));
        // END SNIPPET: e2
    }

    @Override
    public void configure() {

        // START SNIPPET: e1
        from("jmx:platform?objectDomain=jmxExample&key.name=simpleBean").
                to("log:jmxEvent");
        // END SNIPPET: e1

        from("timer:foo?period=6000").bean(bean, "tick");
    }
}
