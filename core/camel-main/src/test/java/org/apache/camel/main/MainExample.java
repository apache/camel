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
package org.apache.camel.main;

import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// START SNIPPET: e1
public class MainExample {

    private static final Logger LOG = LoggerFactory.getLogger(MainExample.class);

    public static void main(String[] args) throws Exception {
        MainExample example = new MainExample();
        example.boot();
    }

    public void boot() throws Exception {
        // create a Main instance
        Main main = new Main();
        // bind MyBean into the registry
        main.bind("foo", new MyBean());
        // add routes
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        // add event listener
        main.addMainListener(new Events());
        // set the properties from a file
        main.setPropertyPlaceholderLocations("example.properties");
        // to configure some options
        main.configure().withName("MyMainCamel").withJmxEnabled(false).withMessageHistory(false);
        // run until you terminate the JVM
        LOG.info("Starting Camel. Use ctrl + c to terminate the JVM.\n");
        main.run();
    }

    private static class MyRouteBuilder extends RouteBuilder {
        private static final Logger LOG = LoggerFactory.getLogger(MyRouteBuilder.class);

        @Override
        public void configure() {
            from("timer:foo?delay={{millisecs}}")
                    .process(new Processor() {
                        public void process(Exchange exchange) {
                            LOG.info("Invoked timer at {}", new Date());
                        }
                    })
                    .bean("foo");
        }
    }

    public static class MyBean {

        private static final Logger LOG = LoggerFactory.getLogger(MyBean.class);
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public void callMe() {
            LOG.info("MyBean.callMe method has been called");
        }
    }

    public static class Events extends MainListenerSupport {

        private static final Logger LOG = LoggerFactory.getLogger(Events.class);

        @Override
        public void afterStart(BaseMainSupport main) {
            LOG.info("MainExample with Camel is now started!");
        }

        @Override
        public void beforeStop(BaseMainSupport main) {
            LOG.info("MainExample with Camel is now being stopped!");
        }
    }
}
// END SNIPPET: e1
