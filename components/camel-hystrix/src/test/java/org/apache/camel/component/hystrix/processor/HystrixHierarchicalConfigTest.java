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
package org.apache.camel.component.hystrix.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.support.SimpleRegistry;
import org.junit.Assert;
import org.junit.Test;

public class HystrixHierarchicalConfigTest {

    @Test
    public void testRegistryConfiguration() throws Exception {
        final SimpleRegistry registry = new SimpleRegistry();
        final CamelContext context = new DefaultCamelContext(registry);
        final Route route = new DefaultRoute(context, null, null, null, null);

        HystrixConfigurationDefinition def = new HystrixConfigurationDefinition();
        def.setGroupKey("global-group-key");
        def.setThreadPoolKey("global-thread-key");
        def.corePoolSize(10);

        HystrixConfigurationDefinition ref = new HystrixConfigurationDefinition();
        ref.setGroupKey("ref-group-key");
        ref.corePoolSize(5);

        registry.bind(HystrixConstants.DEFAULT_HYSTRIX_CONFIGURATION_ID, def);
        registry.bind("ref-hystrix", ref);

        final HystrixReifier reifier = new HystrixReifier(route,
                new CircuitBreakerDefinition()
                        .configuration("ref-hystrix")
                        .hystrixConfiguration()
                        .groupKey("local-conf-group-key")
                        .requestLogEnabled(false)
                        .end()
        );
        final HystrixConfigurationDefinition config = reifier.buildHystrixConfiguration();

        Assert.assertEquals("local-conf-group-key", config.getGroupKey());
        Assert.assertEquals("global-thread-key", config.getThreadPoolKey());
        Assert.assertEquals(Integer.toString(5), config.getCorePoolSize());
    }

    @Test
    public void testContextConfiguration() throws Exception {
        final CamelContext context = new DefaultCamelContext();
        final Route route = new DefaultRoute(context, null, null, null, null);

        HystrixConfigurationDefinition def = new HystrixConfigurationDefinition();
        def.setGroupKey("global-group-key");
        def.setThreadPoolKey("global-thread-key");
        def.corePoolSize(10);

        HystrixConfigurationDefinition ref = new HystrixConfigurationDefinition();
        ref.setGroupKey("ref-group-key");
        ref.corePoolSize(5);

        context.getExtension(Model.class).setHystrixConfiguration(def);
        context.getExtension(Model.class).addHystrixConfiguration("ref-hystrix", ref);

        final HystrixReifier reifier = new HystrixReifier(route,
                new CircuitBreakerDefinition()
                        .configuration("ref-hystrix")
                        .hystrixConfiguration()
                        .groupKey("local-conf-group-key")
                        .requestLogEnabled(false)
                        .end()
        );
        final HystrixConfigurationDefinition config = reifier.buildHystrixConfiguration();

        Assert.assertEquals("local-conf-group-key", config.getGroupKey());
        Assert.assertEquals("global-thread-key", config.getThreadPoolKey());
        Assert.assertEquals(Integer.toString(5), config.getCorePoolSize());
    }

    @Test
    public void testMixedConfiguration() throws Exception {
        final SimpleRegistry registry = new SimpleRegistry();
        final CamelContext context = new DefaultCamelContext(registry);
        final Route route = new DefaultRoute(context, null, null, null, null);

        HystrixConfigurationDefinition def = new HystrixConfigurationDefinition();
        def.setGroupKey("global-group-key");
        def.setThreadPoolKey("global-thread-key");
        def.corePoolSize(10);

        HystrixConfigurationDefinition ref = new HystrixConfigurationDefinition();
        ref.setGroupKey("ref-group-key");
        ref.corePoolSize(5);

        // this should be ignored
        HystrixConfigurationDefinition defReg = new HystrixConfigurationDefinition();
        defReg.setGroupKey("global-reg-group-key");
        defReg.setThreadPoolKey("global-reg-thread-key");
        defReg.corePoolSize(20);

        context.getExtension(Model.class).setHystrixConfiguration(def);

        registry.bind(HystrixConstants.DEFAULT_HYSTRIX_CONFIGURATION_ID, defReg);
        registry.bind("ref-hystrix", ref);

        final HystrixReifier reifier = new HystrixReifier(route,
                new CircuitBreakerDefinition()
                        .configuration("ref-hystrix")
                        .hystrixConfiguration()
                        .groupKey("local-conf-group-key")
                        .requestLogEnabled(false)
                        .end()
        );
        final HystrixConfigurationDefinition config = reifier.buildHystrixConfiguration();

        Assert.assertEquals("local-conf-group-key", config.getGroupKey());
        Assert.assertEquals("global-thread-key", config.getThreadPoolKey());
        Assert.assertEquals(Integer.toString(5), config.getCorePoolSize());
    }
}
