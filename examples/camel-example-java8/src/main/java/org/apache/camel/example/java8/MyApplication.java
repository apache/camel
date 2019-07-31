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
package org.apache.camel.example.java8;

import java.util.Date;
import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MyApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyApplication.class);

    private MyApplication() {
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.addRouteBuilder(new MyRouteBuilder());
        main.run(args);
    }

    private static class MyRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("timer:simple?period=503")
                .id("simple-route")
                .transform()
                    .exchange(this::dateToTime)
                .process()
                    .message(this::log)
                .process()
                    .body(this::log)
                .choice()
                    .when()
                        .body(Integer.class, b -> (b & 1) == 0)
                        .log("Received even number")
                    .when()
                        .body(Integer.class, (b, h) -> h.containsKey("skip") ? false : (b & 1) == 0)
                        .log("Received odd number")
                    .when()
                        .body(Objects::isNull)
                        .log("Received null body")
                    .when()
                        .body(Integer.class, b -> (b & 1) != 0)
                        .log("Received odd number")
                .endChoice();
        }

        private Long dateToTime(Exchange e) {
            return e.getProperty(Exchange.TIMER_FIRED_TIME, Date.class).getTime();
        }

        private void log(Object b) {
            LOGGER.info("body is: {}", b);
        }

        private void log(Message m) {
            LOGGER.info("message is: {}", m);
        }
    }
}
