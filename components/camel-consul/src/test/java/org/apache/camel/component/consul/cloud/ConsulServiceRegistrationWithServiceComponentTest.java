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
package org.apache.camel.component.consul.cloud;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.service.ServiceComponent;

public class ConsulServiceRegistrationWithServiceComponentTest extends ConsulServiceRegistrationTestBase {

    @BindToRegistry("service")
    ServiceComponent comp = new ServiceComponent();

    @Override
    protected Map<String, String> getMetadata() {
        return new HashMap<String, String>() {
            {
                put("service.type", "consul");
                put("service.zone", "US");
            }
        };
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("service:%s:jetty:http://0.0.0.0:%d/service/endpoint?service.type=consul&service.zone=US", SERVICE_NAME, SERVICE_PORT).routeId(SERVICE_ID)
                    .routeGroup(SERVICE_NAME).noAutoStartup().to("log:service-registry?level=INFO");
            }
        };
    }
}
