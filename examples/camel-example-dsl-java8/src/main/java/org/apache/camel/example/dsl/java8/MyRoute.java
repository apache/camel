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
package org.apache.camel.example.dsl.java8;

import java.util.Date;
import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class MyRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("timer:simple?period=503")
            .id("simple-route")
            .transform()
                .exchange(this::dateToTime)
            .choice()
                .when()
                    .body(Integer.class, b ->  (b & 1) == 0)
                    .log("Received even number")
                .when()
                    .body(Integer.class, (b, h) -> h.containsKey("skip") ? false : (b & 1) == 0)
                    .log("Received odd number")
                .when()
                    .body(Objects::isNull)
                    .log("Received null body")
                .when()
                    .body(Integer.class, b ->  (b & 1) != 0)
                    .log("Received odd number")
                .endChoice();


         //   .transform(this::dateToTime2)
         //   .transform(function(
         //       (Exchange e) -> e.getProperty(Exchange.TIMER_FIRED_TIME, Date.class).getTime()
         //   ))
    }

    private Long dateToTime(Exchange e) {
        return e.getProperty(Exchange.TIMER_FIRED_TIME, Date.class).getTime();
    }

    private <T> T dateToTime2(Exchange exchange, Class<T> type) {
        return null;
    }
}
