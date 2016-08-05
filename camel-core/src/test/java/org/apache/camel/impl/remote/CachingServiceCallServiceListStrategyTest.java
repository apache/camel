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
package org.apache.camel.impl.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class CachingServiceCallServiceListStrategyTest {

    @Test
    public void testCachingServiceList() throws Exception {
        MyStrategy strategy = new MyStrategy();
        CachingServiceCallServiceListStrategy<DefaultServiceCallServer> caching = CachingServiceCallServiceListStrategy.wrap(strategy, 1, TimeUnit.SECONDS);

        strategy.addServer(new DefaultServiceCallServer("localhost", 1111));
        Assert.assertEquals(1, caching.getUpdatedListOfServers("noname").size());
        strategy.addServer(new DefaultServiceCallServer("localhost", 1112));
        Assert.assertEquals(1, caching.getUpdatedListOfServers("noname").size());

        // Let the cache expire
        Thread.sleep(1100);

        Assert.assertEquals(2, caching.getUpdatedListOfServers("noname").size());
    }

    private class MyStrategy extends DefaultServiceCallServerListStrategy<DefaultServiceCallServer> {
        private List<DefaultServiceCallServer> servers = new ArrayList<>();

        @Override
        public List<DefaultServiceCallServer> getUpdatedListOfServers(String name) {
            return servers;
        }

        public void addServer(DefaultServiceCallServer server) {
            servers.add(server);
        }
    }
}
