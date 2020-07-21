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
package org.apache.camel.component.zookeepermaster;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.component.file.remote.SftpEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;

@ContextConfiguration
public class MasterEndpointTest extends AbstractJUnit4SpringContextTests {
    @Autowired
    protected CamelContext camelContext;

    @EndpointInject("mock:results")
    protected MockEndpoint resultEndpoint;

    @Produce("seda:bar")
    protected ProducerTemplate template;

    @Test
    public void testEndpoint() throws Exception {
        // check the endpoint configuration
        List<Route> registeredRoutes = camelContext.getRoutes();
        assertEquals("number of routes", 1, registeredRoutes.size());
        MasterEndpoint endpoint = (MasterEndpoint) registeredRoutes.get(0).getEndpoint();
        assertEquals("wrong endpoint uri", "seda:bar", endpoint.getConsumerEndpointUri());

        String expectedBody = "<matched/>";

        resultEndpoint.expectedBodiesReceived(expectedBody);

        // lets wait for the entry to be registered...
        Thread.sleep(5000);

        template.sendBodyAndHeader(expectedBody, "foo", "bar");

        MockEndpoint.assertIsSatisfied(camelContext);
    }

    @Test
    public void testRawPropertiesOnChild() throws Exception {
        final String uri = "zookeeper-master://name:sftp://myhost/inbox?password=RAW(_BEFORE_AMPERSAND_&_AFTER_AMPERSAND_)&username=jdoe";

        DefaultCamelContext ctx = new DefaultCamelContext();
        MasterEndpoint master = (MasterEndpoint) ctx.getEndpoint(uri);
        SftpEndpoint sftp = (SftpEndpoint) master.getEndpoint();

        assertEquals("_BEFORE_AMPERSAND_&_AFTER_AMPERSAND_", sftp.getConfiguration().getPassword());
    }
}
