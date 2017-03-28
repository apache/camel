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
package org.apache.camel.component.drill;

import org.apache.camel.Endpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class EndpointTest extends CamelTestSupport {

    private static final String HOST = "my.host.me";
    private static final Integer PORT = 4000;
    private static final String DIRECTORY = "directory";
    private static final String CLUSTERID = "clusterId";
    private static final DrillConnectionMode MODE = DrillConnectionMode.ZK;

    @Test
    public void testZKJdbcURL() throws Exception {
        Endpoint endpoint = context.getEndpoint("drill://" + HOST + "?port=" + PORT + "&directory=" + DIRECTORY + "&clusterId=" + CLUSTERID + "&mode=" + MODE);

        final String uri = "jdbc:drill:zk=" + HOST + ":" + PORT + "/" + DIRECTORY + "/" + CLUSTERID;

        assertTrue(endpoint instanceof DrillEndpoint);

        assertEquals(HOST, ((DrillEndpoint)endpoint).getHost());
        assertEquals(PORT, ((DrillEndpoint)endpoint).getPort());
        assertEquals(DIRECTORY, ((DrillEndpoint)endpoint).getDirectory());
        assertEquals(CLUSTERID, ((DrillEndpoint)endpoint).getClusterId());
        assertEquals(MODE, ((DrillEndpoint)endpoint).getMode());

        assertEquals(uri, ((DrillEndpoint)endpoint).toJDBCUri());
    }

}
