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
package org.apache.camel.component.mail;

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;

/**
 * CAMEL-9106
 */
public class MapMailMessagesBugRoute extends RouteBuilder {

    public static void main(String... args) throws Exception {
        Main main = new Main();
        main.addRouteBuilder(new MapMailMessagesBugRoute());
        main.run(args);
    }

    @Override
    public void configure() {
        // This is for Office365 host. Set your own host/username/password.
        // When setting option mapMailMessage=true (the default) option peek=true fails with the SEEN flag.
        // When setting option mapMailMessage=false option peek=true will work as supposed.
        from("imaps://outlook.office365.com?peek=true&unseen=true&debugMode=true&username=<username>&password=<password>")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        if (true) {
                            throw new IOException("This will cause messages to be marked SEEN even when peek=true.");
                        }
                    }
                })
                .to("log:mail");
    }

}

