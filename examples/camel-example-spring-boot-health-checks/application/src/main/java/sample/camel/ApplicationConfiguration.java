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
package sample.camel;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {
    @Bean
    public RouteBuilder routesBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("timer:foo?period=1s")
                    .routeId("foo")
                    .process(e -> {
                        throw new RuntimeCamelException("This is a forced exception to have health check monitor this failure (route=foo)"); 
                    });
                from("timer:bar?period=1s")
                    .routeId("bar")
                    .process(e -> {
                        throw new RuntimeCamelException("This is a forced exception to have health check monitor this failure (route=bar)");
                    });
                from("timer:slow?period=1s")
                    .routeId("slow")
                    .process(e -> {
                        Thread.sleep(1200);
                    });
            }
        };
    }

    @Bean(name = "my-check-1")
    public ApplicationCheck applicationHealth1() {
        return new ApplicationCheck("global", "my-check-1");
    }

    @Bean(name = "my-check-2")
    public ApplicationCheck applicationHealth2() {
        return new ApplicationCheck("local", "my-check-2");
    }
}
