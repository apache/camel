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
package org.apache.camel.component.zookeepermaster;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ServiceHelper;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class MasterQuartz2EndpointTest extends AbstractJUnit4SpringContextTests {

    protected static ZKServerFactoryBean lastServerBean;

    protected static CuratorFactoryBean lastClientBean;

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject(uri = "mock:results")
    protected MockEndpoint resultEndpoint;

    // Yeah this sucks.. why does the spring context not get shutdown
    // after each test case?  Not sure!
    @Autowired
    protected ZKServerFactoryBean zkServerBean;

    @Autowired
    protected CuratorFactoryBean zkClientBean;

    @Before
    public void startService() throws Exception {
        ServiceHelper.startService(camelContext);
    }

    @After
    public void afterRun() throws Exception {
        lastServerBean = zkServerBean;
        lastClientBean = zkClientBean;
        ServiceHelper.stopServices(camelContext);
    }

    @AfterClass
    public static void shutDownZK() throws Exception {
        lastClientBean.destroy();
        lastServerBean.destroy();
    }

    @Test
    public void testEndpoint() throws Exception {
        resultEndpoint.expectedMinimumMessageCount(2);

        MockEndpoint.assertIsSatisfied(camelContext);
    }
}
