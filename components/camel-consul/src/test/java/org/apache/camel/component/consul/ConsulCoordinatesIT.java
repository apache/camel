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
package org.apache.camel.component.consul;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.consul.endpoint.ConsulCoordinatesActions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.model.coordinate.Coordinate;
import org.kiwiproject.consul.model.coordinate.Datacenter;

public class ConsulCoordinatesIT extends ConsulTestSupport {

    @Test
    public void testDatacenters() {
        List<Datacenter> ref = getConsul().coordinateClient().getDatacenters();
        List<Datacenter> res = fluentTemplate().withHeader(ConsulConstants.CONSUL_ACTION, ConsulCoordinatesActions.DATACENTERS)
                .to("direct:consul").request(List.class);

        Assertions.assertFalse(ref.isEmpty());
        Assertions.assertFalse(res.isEmpty());
        Assertions.assertEquals(ref, res);
    }

    @Disabled("Disabled as it seems that nodes is always 0")
    @Test
    public void testNodes() {
        List<Coordinate> ref = getConsul().coordinateClient().getNodes();
        List<Coordinate> res = fluentTemplate().withHeader(ConsulConstants.CONSUL_ACTION, ConsulCoordinatesActions.NODES)
                .to("direct:consul").request(List.class);

        Assertions.assertFalse(ref.isEmpty());
        Assertions.assertFalse(res.isEmpty());
        Assertions.assertEquals(ref, res);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:consul").to("consul:coordinates");
            }
        };
    }
}
