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
package org.apache.camel.component.splunk;

import com.splunk.SSLSecurityProtocol;
import com.splunk.Service;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SplunkComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createProducerEndpointWithMinimalConfiguration() throws Exception {
        SplunkComponent component = context.getComponent("splunk", SplunkComponent.class);

        SplunkEndpoint endpoint = (SplunkEndpoint)component.createEndpoint("splunk://test?username=test&password=pw");
        assertEquals(Service.DEFAULT_HOST, endpoint.getConfiguration().getHost());
        assertEquals(Service.DEFAULT_PORT, endpoint.getConfiguration().getPort());
        assertEquals("test", endpoint.getConfiguration().getUsername());
        assertEquals("pw", endpoint.getConfiguration().getPassword());
        assertEquals(Service.DEFAULT_SCHEME, endpoint.getConfiguration().getScheme());
        assertEquals(5000, endpoint.getConfiguration().getConnectionTimeout());
        assertFalse(endpoint.getConfiguration().isUseSunHttpsHandler());
        assertFalse(endpoint.getConfiguration().isRaw());
        assertEquals(SSLSecurityProtocol.TLSv1_2, endpoint.getConfiguration().getSslProtocol());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createProducerWithoutUserAndPassword() throws Exception {
        SplunkComponent component = context.getComponent("splunk", SplunkComponent.class);
        component.createEndpoint("splunk://test");
    }

    @Test
    public void createProducerEndpointWithMaximalConfiguration() throws Exception {
        SplunkComponent component = context.getComponent("splunk", SplunkComponent.class);

        SplunkEndpoint endpoint = (SplunkEndpoint)component
            .createEndpoint("splunk://tcp?username=test&password=pw&host=myhost&port=3333&" + "tcpReceiverPort=4444&index=myindex&sourceType=testSource&"
                            + "source=test&eventHost=original-host.com&owner=me&app=fantasticapp&useSunHttpsHandler=true&raw=true&sslProtocol=SSLv3");
        assertEquals("myhost", endpoint.getConfiguration().getHost());
        assertEquals(3333, endpoint.getConfiguration().getPort());
        assertEquals("test", endpoint.getConfiguration().getUsername());
        assertEquals("pw", endpoint.getConfiguration().getPassword());
        assertEquals(4444, endpoint.getConfiguration().getTcpReceiverPort());
        assertEquals("myindex", endpoint.getConfiguration().getIndex());
        assertEquals("testSource", endpoint.getConfiguration().getSourceType());
        assertEquals("test", endpoint.getConfiguration().getSource());
        assertEquals("original-host.com", endpoint.getConfiguration().getEventHost());
        assertEquals("me", endpoint.getConfiguration().getOwner());
        assertEquals("fantasticapp", endpoint.getConfiguration().getApp());
        assertTrue(endpoint.getConfiguration().isUseSunHttpsHandler());
        assertTrue(endpoint.getConfiguration().isRaw());
        assertEquals(SSLSecurityProtocol.SSLv3, endpoint.getConfiguration().getSslProtocol());
    }

    @Test
    public void createConsumerEndpointWithMinimalConfiguration() throws Exception {
        SplunkComponent component = context.getComponent("splunk", SplunkComponent.class);

        SplunkEndpoint endpoint = (SplunkEndpoint)component.createEndpoint("splunk://realtime?username=test&" + "password=pw&search=Splunk search query goes here");
        assertEquals(Service.DEFAULT_HOST, endpoint.getConfiguration().getHost());
        assertEquals(Service.DEFAULT_PORT, endpoint.getConfiguration().getPort());
        assertEquals("test", endpoint.getConfiguration().getUsername());
        assertEquals("pw", endpoint.getConfiguration().getPassword());
        assertEquals(Service.DEFAULT_SCHEME, endpoint.getConfiguration().getScheme());
        assertEquals(5000, endpoint.getConfiguration().getConnectionTimeout());
        assertEquals("Splunk search query goes here", endpoint.getConfiguration().getSearch());
    }

    @Test
    public void createConsumerEndpointWithMaximalConfiguration() throws Exception {
        SplunkComponent component = context.getComponent("splunk", SplunkComponent.class);

        SplunkEndpoint endpoint = (SplunkEndpoint)component
            .createEndpoint("splunk://normal?username=test&password=pw&host=myhost&port=3333&delay=10000&" + "search=Splunk search query goes here&initEarliestTime=-1d"
                            + "&latestTime=now&count=10&" + "owner=me&app=fantasticapp");
        assertEquals("myhost", endpoint.getConfiguration().getHost());
        assertEquals(3333, endpoint.getConfiguration().getPort());
        assertEquals("test", endpoint.getConfiguration().getUsername());
        assertEquals("pw", endpoint.getConfiguration().getPassword());
        assertEquals("-1d", endpoint.getConfiguration().getInitEarliestTime());
        assertEquals("now", endpoint.getConfiguration().getLatestTime());
        assertEquals(10, endpoint.getConfiguration().getCount());
        assertEquals("me", endpoint.getConfiguration().getOwner());
        assertEquals("fantasticapp", endpoint.getConfiguration().getApp());
        assertEquals(Service.DEFAULT_SCHEME, endpoint.getConfiguration().getScheme());
        assertEquals("Splunk search query goes here", endpoint.getConfiguration().getSearch());
    }

}
