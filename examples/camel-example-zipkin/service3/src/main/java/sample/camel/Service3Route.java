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
import org.apache.camel.zipkin.ZipkinEventNotifier;

public class Service3Route extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        ZipkinEventNotifier zipkin = new ZipkinEventNotifier();
        zipkin.setHostName("192.168.99.100");
        zipkin.setPort(9410);
        zipkin.setServiceName("service3");

        // add zipkin to CamelContext
        getContext().getManagementStrategy().addEventNotifier(zipkin);

        from("undertow:http://0.0.0.0:7070/service3").routeId("service3")
                .convertBodyTo(String.class)
                .delay(simple("${random(1000,2000)}"))
                .transform(simple("Bye: ${body}"));
    }

}
