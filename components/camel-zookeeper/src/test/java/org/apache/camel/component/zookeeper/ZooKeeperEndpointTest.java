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
package org.apache.camel.component.zookeeper;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ZooKeeperEndpointTest extends CamelTestSupport {

    @Test
    public void multipleZooKeeperServers() {
        ZooKeeperEndpoint endpoint = context.getEndpoint("zookeeper:someserver1,someserver2:1234/zoo", ZooKeeperEndpoint.class);
        assertEquals(2, endpoint.getConfiguration().getServers().size(), "Get wrong number of servers");
        assertEquals("someserver1", endpoint.getConfiguration().getServers().get(0), "The first server address is wrong");
        assertEquals("someserver2:1234", endpoint.getConfiguration().getServers().get(1), "The second server address is wrong");
    }

}
