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
package org.apache.camel.impl.cloud;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.model.cloud.CombinedServiceCallServiceFilterConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class CombinedServiceFilterTest extends ContextTestSupport {
    @Test
    public void testMultiServiceFilterConfiguration() throws Exception {
        CombinedServiceCallServiceFilterConfiguration conf =
            new CombinedServiceCallServiceFilterConfiguration()
                .healthy()
                .passThrough();

        CombinedServiceFilter filter = (CombinedServiceFilter)conf.newInstance(context);
        Assert.assertEquals(2, filter.getDelegates().size());
        Assert.assertTrue(filter.getDelegates().get(0) instanceof HealthyServiceFilter);
        Assert.assertTrue(filter.getDelegates().get(1) instanceof PassThroughServiceFilter);
    }


    @Test
    public void testMultiServiceFilter() throws Exception {
        CombinedServiceCallServiceFilterConfiguration conf =
            new CombinedServiceCallServiceFilterConfiguration()
                .healthy()
                .custom(services -> services.stream().filter(s -> s.getPort() < 2000).collect(Collectors.toList())
        );

        List<ServiceDefinition> services = conf.newInstance(context).apply(Arrays.asList(
            new DefaultServiceDefinition("no-name", "127.0.0.1", 1000),
            new DefaultServiceDefinition("no-name", "127.0.0.1", 1001, new DefaultServiceHealth(false)),
            new DefaultServiceDefinition("no-name", "127.0.0.1", 1002, new DefaultServiceHealth(true)),
            new DefaultServiceDefinition("no-name", "127.0.0.1", 2001, new DefaultServiceHealth(true)),
            new DefaultServiceDefinition("no-name", "127.0.0.1", 2001, new DefaultServiceHealth(false))
        ));

        Assert.assertEquals(2, services.size());
        Assert.assertFalse(services.stream().anyMatch(s -> !s.getHealth().isHealthy()));
        Assert.assertFalse(services.stream().anyMatch(s -> s.getPort() > 2000));
        Assert.assertTrue(services.stream().anyMatch(s -> s.getPort() == 1000));
        Assert.assertTrue(services.stream().anyMatch(s -> s.getPort() == 1002));
    }
}
