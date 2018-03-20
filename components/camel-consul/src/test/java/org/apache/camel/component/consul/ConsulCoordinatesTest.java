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
package org.apache.camel.component.consul;

import java.util.List;

import com.orbitz.consul.model.coordinate.Coordinate;
import com.orbitz.consul.model.coordinate.Datacenter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.consul.endpoint.ConsulCoordinatesActions;
import org.junit.Assert;
import org.junit.Test;

public class ConsulCoordinatesTest extends ConsulTestSupport {

    @Test
    public void testDatacenters() throws Exception {
        List<Datacenter> ref = getConsul().coordinateClient().getDatacenters();
        List<Datacenter> res = fluentTemplate()
            .withHeader(ConsulConstants.CONSUL_ACTION, ConsulCoordinatesActions.DATACENTERS)
            .to("direct:consul")
            .request(List.class);

        Assert.assertFalse(ref.isEmpty());
        Assert.assertFalse(res.isEmpty());
        Assert.assertEquals(ref, res);
    }
    
    @Test
    public void testNodes() throws Exception {
        List<Coordinate> ref = getConsul().coordinateClient().getNodes();
        List<Coordinate> res = fluentTemplate()
            .withHeader(ConsulConstants.CONSUL_ACTION, ConsulCoordinatesActions.NODES)
            .to("direct:consul")
            .request(List.class);

        Assert.assertFalse(ref.isEmpty());
        Assert.assertFalse(res.isEmpty());
        Assert.assertEquals(ref, res);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:consul")
                    .to("consul:coordinates");
            }
        };
    }
}
