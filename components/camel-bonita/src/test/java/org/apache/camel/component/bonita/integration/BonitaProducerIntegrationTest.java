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
package org.apache.camel.component.bonita.integration;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Bonita producer integration tests. Requires a running Bonita instance.
 *
 * See test-options.properties for configuration options.
 */
@Ignore("Requires local Bonita instance to test")
public class BonitaProducerIntegrationTest extends BonitaIntegrationTestSupport {

    @Test
    public void testStartCase() throws Exception {
        Map<String, Serializable> map = new HashMap<>();
        map.put("vacationRequestIdContract", "1");

        template.sendBody("direct:startCase", map);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:startCase")
                        .to("bonita:startCase?hostname={{host}}&port={{port}}&processName={{process}}&username={{username}}&password={{password}}");
            }
        };
    }
}
