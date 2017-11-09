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
package org.apache.camel.component.aws.cw;


import java.util.Date;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.mockito.Mockito.mock;

public class CwComponentConfigurationTest extends CamelTestSupport {
    private static final Date NOW = new Date();
    AmazonCloudWatchClient amazonCwClient = mock(AmazonCloudWatchClient.class);

    @Test
    public void createEndpointWithAllOptions() throws Exception {
        CwComponent component = new CwComponent(context);
        CwEndpoint endpoint = (CwEndpoint) component.createEndpoint("aws-cw://camel.apache.org/test?amazonCwClient=#amazonCwClient&name=testMetric&value=2&unit=Count&timestamp=#now");

        assertEquals("camel.apache.org/test", endpoint.getConfiguration().getNamespace());
        assertEquals("testMetric", endpoint.getConfiguration().getName());
        assertEquals(Double.valueOf(2), endpoint.getConfiguration().getValue());
        assertEquals("Count", endpoint.getConfiguration().getUnit());
        assertEquals(NOW, endpoint.getConfiguration().getTimestamp());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAccessKeyConfiguration() throws Exception {
        CwComponent component = new CwComponent(context);
        component.createEndpoint("aws-cw://camel.apache.org/test?secretKey=yyy");
    }

    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutSecretKeyConfiguration() throws Exception {
        CwComponent component = new CwComponent(context);
        component.createEndpoint("aws-cw://camel.apache.org/test?accessKey=xxx");
    }
    
    @Test
    public void createEndpointWithoutSecretKeyAndAccessKeyConfiguration() throws Exception {
        CwComponent component = new CwComponent(context);
        component.createEndpoint("aws-cw://camel.apache.org/test?amazonCwClient=#amazonCwClient&accessKey=xxx");
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("amazonCwClient", amazonCwClient);
        registry.bind("now", NOW);
        return registry;
    }
}