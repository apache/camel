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
package org.apache.camel.component.infinispan.policy;

import java.util.Properties;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.api.BasicCacheContainer;
import org.junit.Ignore;

@Ignore("Disabled as it requires a transactional cache")
public class InfinispanRemoteRoutePolicyTest extends InfinispanRoutePolicyTestBase {

    @Override
    protected BasicCacheContainer createCacheManager() throws Exception {
        Properties props = new Properties();
        props.setProperty("infinispan.client.hotrod.server_list", "127.0.0.1:11222");
        props.setProperty("infinispan.client.hotrod.force_return_values", "true");

        return new RemoteCacheManager(
            new ConfigurationBuilder().withProperties(props).build(),
            true
        );
    }
}
