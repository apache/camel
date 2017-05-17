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

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class ClientRoute extends RouteBuilder {

    @Override
    public void configure() {
        // you can configure the route rule with Java DSL here
        from("timer:trigger?period=500").streamCaching()
            .bean("counterBean")
            .log(" Client request: ${body}")
            .hystrix()
                // see application.properties how hystrix is configured
                .to("http://localhost:9090/service1")
            //.onFallback()
            // we use a fallback without network that provides a repsonse message immediately
            //    .transform().simple("Fallback ${body}")
            .onFallbackViaNetwork()
                // we use fallback via network where we call a 2nd service
                .to("http://localhost:7070/service2")
            .end()
            .log("Client response: ${body}");
    }

}
