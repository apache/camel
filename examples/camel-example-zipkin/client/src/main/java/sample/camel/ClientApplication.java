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
package sample.camel;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import org.apache.camel.spi.CamelEvent.CamelContextStartingEvent;
import org.apache.camel.zipkin.ZipkinTracer;

@ApplicationScoped
public class ClientApplication {

    public void setupCamel(@Observes CamelContextStartingEvent event) {
        // create zipkin
        ZipkinTracer zipkin = new ZipkinTracer();
        zipkin.setEndpoint("http://localhost:9411/api/v2/spans");
        zipkin.addClientServiceMapping("http://localhost:9090/service1", "service1");
        // capture 100% of all the events
        zipkin.setRate(1.0f);
        // include message bodies in the traces (not recommended for production)
        zipkin.setIncludeMessageBodyStreams(true);

        // register zipkin to CamelContext
        zipkin.init(event.getContext());
    }

}
