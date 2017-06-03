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
import org.springframework.stereotype.Component;

//CHECKSTYLE:OFF
/**
 * A sample Spring Boot application that starts the Camel routes.
 */
@SpringBootApplication
public class ServiceApplication {
    
    @Component
    public class Service1Route extends RouteBuilder {
        public void configure() throws Exception {
            from("undertow:http://localhost:9011")
                .to("log:org.apache.camel.example?level=INFO&showAll=true&multiline=true")
                .transform().simple("Hi!, I'm service-1 on ${camelId}/${routeId}");
            from("undertow:http://localhost:9012")
                .to("log:org.apache.camel.example?level=INFO&showAll=true&multiline=true")
                .transform().simple("Hi!, I'm service-1 on ${camelId}/${routeId}");
            from("undertow:http://localhost:9013")
                .to("log:org.apache.camel.example?level=INFO&showAll=true&multiline=true")
                .transform().simple("Hi!, I'm service-1 on ${camelId}/${routeId}");
        }
    }

    @Component
    public class Service2Route extends RouteBuilder {
        public void configure() throws Exception {
            from("undertow:http://localhost:9021")
                .to("log:org.apache.camel.example?level=INFO&showAll=true&multiline=true")
                .transform().simple("Hi!, I'm service-2 on ${camelId}/${routeId}");
            from("undertow:http://localhost:9022")
                .to("log:org.apache.camel.example?level=INFO&showAll=true&multiline=true")
                .transform().simple("Hi!, I'm service-2 on ${camelId}/${routeId}");
            from("undertow:http://localhost:9023")
                .to("log:org.apache.camel.example?level=INFO&showAll=true&multiline=true")
                .transform().simple("Hi!, I'm service-2 on ${camelId}/${routeId}");
        }
    }

    /**
     * A main method to start this application.
     */
    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }

}
//CHECKSTYLE:ON
