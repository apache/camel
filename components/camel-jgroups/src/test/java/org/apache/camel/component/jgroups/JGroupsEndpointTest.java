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
package org.apache.camel.component.jgroups;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JGroupsEndpointTest extends CamelTestSupport {

    // Constants

    static final String CLUSTER_NAME = "CLUSTER_NAME";

    // Routes fixture

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jgroups:" + CLUSTER_NAME).to("mock:test");
            }
        };
    }

    // Tests

    @Test
    public void shouldSetClusterName() throws Exception {
        // When
        JGroupsEndpoint endpoint = getMandatoryEndpoint("jgroups:" + CLUSTER_NAME, JGroupsEndpoint.class);

        // Then
        assertEquals(CLUSTER_NAME, endpoint.getClusterName());
    }

    @Test
    public void shouldResolveDefaultChannel() throws Exception {
        // When
        JGroupsEndpoint endpoint = getMandatoryEndpoint("jgroups:" + CLUSTER_NAME, JGroupsEndpoint.class);

        // Then
        assertNotNull(endpoint.getResolvedChannel());
    }

}
