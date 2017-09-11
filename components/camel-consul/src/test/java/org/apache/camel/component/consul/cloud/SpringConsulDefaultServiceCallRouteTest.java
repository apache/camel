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

package org.apache.camel.component.consul.cloud;

import java.util.List;

import org.apache.camel.component.ribbon.cloud.RibbonServiceLoadBalancer;
import org.apache.camel.impl.cloud.DefaultServiceCallProcessor;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringConsulDefaultServiceCallRouteTest extends SpringConsulServiceCallRouteTest {
    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/consul/cloud/SpringConsulDefaultServiceCallRouteTest.xml");
    }

    @Test
    public void testServiceCallConfiguration() throws Exception {
        List<DefaultServiceCallProcessor> processors = findServiceCallProcessors();

        Assert.assertFalse(processors.isEmpty());
        Assert.assertEquals(2, processors.size());
        Assert.assertFalse(processors.get(0).getLoadBalancer() instanceof RibbonServiceLoadBalancer);
        Assert.assertFalse(processors.get(1).getLoadBalancer() instanceof RibbonServiceLoadBalancer);
    }
}
