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
import org.springframework.util.SocketUtils;

//CHECKSTYLE:OFF
/**
 * A sample Spring Boot application that starts the Camel routes.
 */
@SpringBootApplication
public class ServiceApplication {

    @Component
    public class Services extends RouteBuilder {
        public void configure() throws Exception {
            fromF("service:my-service:undertow:http://localhost:%d/path/to/service/1", SocketUtils.findAvailableTcpPort())
                .transform().simple("Hi!, I'm service-1 on path: /path/to/service/1");
            fromF("service:my-service:undertow:http://localhost:%d/path/to/service/2", SocketUtils.findAvailableTcpPort())
                .transform().simple("Hi!, I'm service-1 on path: /path/to/service/2");
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
