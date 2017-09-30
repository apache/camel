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
package org.apache.camel.example;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.stereotype.Component;

//CHECKSTYLE:OFF
/**
 * A sample Spring Boot application that starts the Camel routes.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ConsumerApplication {
    @Component
    public class ConsumerRoute extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            rest("/serviceCall")
                .get("/{serviceId}")
                .to("direct:service-call");

            from("direct:service-call")
                .setBody().constant(null)
                .removeHeaders("CamelHttp*")
                .to("log:service-call?level=INFO&showAll=true&multiline=true")
                .choice()
                    .when(header("serviceId").isEqualTo("service1"))
                        .serviceCall("service-1")
                        .convertBodyTo(String.class)
                        .log("service-1 : ${body}")
                    .when(header("serviceId").isEqualTo("service2"))
                        .serviceCall("service-2")
                        .convertBodyTo(String.class)
                        .log("service-1 : ${body}");
        }
    }

    /**
     * A main method to start this application.
     */
    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }

}
//CHECKSTYLE:ON
