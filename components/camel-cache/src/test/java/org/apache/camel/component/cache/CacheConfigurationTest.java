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
package org.apache.camel.component.cache;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class CacheConfigurationTest {

    @Test
    public void doURICheck() throws Exception {
        CamelContext context = new DefaultCamelContext();

        CacheComponent component = new CacheComponent(context);

        CacheEndpoint endpoint1 = (CacheEndpoint) component.createEndpoint("cache://myname1?diskPersistent=true");

        CacheEndpoint endpoint2 = (CacheEndpoint) component.createEndpoint("cache://myname2?diskPersistent=false");

        Assert.assertTrue("Endpoint1 cache name is myname1", "myname1".equals(endpoint1.getConfig().getCacheName()));
        Assert.assertTrue("Endpoint2 cache name is myname2", "myname2".equals(endpoint2.getConfig().getCacheName()));

        Assert.assertTrue("Endpoint1 is true", endpoint1.getConfig().isDiskPersistent());
        Assert.assertTrue("Endpoint2 is false", !endpoint2.getConfig().isDiskPersistent());
    }
}
