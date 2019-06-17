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
package org.apache.camel.example;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * This is an alternative example to show how you can use a public static void main class
 * to run Camel standalone (without help from its Main class). This is to demonstrate
 * what code you need to write to startup Camel without any help (or magic).
 * <p/>
 * Compare this example with {@link MyApplication} which uses Camel's main class to
 * run Camel standalone in a easier way.
 */
public final class StandaloneCamel {

    private StandaloneCamel() {
    }

    public static void main(String[] args) throws Exception {
        // create a new CamelContext
        CamelContext camelContext = new DefaultCamelContext();

        // configure where to load properties file in the properties component
        camelContext.getPropertiesComponent().setLocation("classpath:application.properties");
        // resolve property placeholder
        String hello = camelContext.resolvePropertyPlaceholders("{{hi}}");

        // and create bean with the placeholder
        MyBean myBean = new MyBean(hello, "Bye");
        // register bean to Camel
        camelContext.getRegistry().bind("myBean", myBean);

        // add routes to Camel
        camelContext.addRoutes(new MyRouteBuilder());

        // start Camel
        camelContext.start();

        // just run for 10 seconds and stop
        System.out.println("Running for 10 seconds and then stopping");
        Thread.sleep(10000);

        // stop and shutdown Camel
        camelContext.stop();
    }
}
