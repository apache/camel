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
import org.apache.camel.zipkin.ZipkinTracer;

public class Service2Route extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // create zipkin
        ZipkinTracer zipkin = new ZipkinTracer();
        zipkin.setHostName("192.168.99.100");
        zipkin.setPort(9410);
        // set the service name
        zipkin.setServiceName("service2");
        // capture 100% of all the events
        zipkin.setRate(1.0f);
        // include message bodies in the traces (not recommended for production)
        zipkin.setIncludeMessageBodyStreams(true);

        // add zipkin to CamelContext
        zipkin.init(getContext());

        from("undertow:http://0.0.0.0:7070/service2").routeId("service2").streamCaching()
                .log(" Service2 request: ${body}")
                .delay(simple("${random(1000,2000)}"))
                .transform(simple("Service2-${body}"))
                .log("Service2 response: ${body}");
    }

}
