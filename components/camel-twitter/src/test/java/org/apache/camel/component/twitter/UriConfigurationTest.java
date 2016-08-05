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
package org.apache.camel.component.twitter;

import org.apache.camel.CamelContext;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class UriConfigurationTest extends Assert {
    private CamelContext context = new DefaultCamelContext();

    private CamelTwitterTestSupport support = new CamelTwitterTestSupport();

    @Test
    public void testBasicAuthentication() throws Exception {
        Endpoint endpoint = context.getEndpoint("twitter:todo/todo?" + support.getUriTokens());
        assertTrue("Endpoint not a TwitterEndpoint: " + endpoint, endpoint instanceof TwitterEndpoint);
        TwitterEndpoint twitterEndpoint = (TwitterEndpoint) endpoint;

        assertTrue(!twitterEndpoint.getProperties().getConsumerKey().isEmpty());
        assertTrue(!twitterEndpoint.getProperties().getConsumerSecret().isEmpty());
        assertTrue(!twitterEndpoint.getProperties().getAccessToken().isEmpty());
        assertTrue(!twitterEndpoint.getProperties().getAccessTokenSecret().isEmpty());
    }
    
    @Test
    public void testPageSetting() throws Exception {
        Endpoint endpoint = context.getEndpoint("twitter:todo/page?count=50&numberOfPages=2");
        assertTrue("Endpoint not a TwitterEndpoint: " + endpoint, endpoint instanceof TwitterEndpoint);
        TwitterEndpoint twitterEndpoint = (TwitterEndpoint) endpoint;

        assertEquals(new Integer(50), twitterEndpoint.getProperties().getCount());
        assertEquals(new Integer(2), twitterEndpoint.getProperties().getNumberOfPages());
    }
    
    @Test
    public void testHttpProxySetting() throws Exception {
        Endpoint endpoint = context.getEndpoint("twitter:todo/todo?httpProxyHost=example.com&httpProxyPort=3338&httpProxyUser=test&httpProxyPassword=pwd");
        assertTrue("Endpoint not a TwitterEndpoint: " + endpoint, endpoint instanceof TwitterEndpoint);
        TwitterEndpoint twitterEndpoint = (TwitterEndpoint) endpoint;
        
        assertEquals("example.com", twitterEndpoint.getProperties().getHttpProxyHost());
        assertEquals(Integer.valueOf(3338), twitterEndpoint.getProperties().getHttpProxyPort());
        assertEquals("test", twitterEndpoint.getProperties().getHttpProxyUser());
        assertEquals("pwd", twitterEndpoint.getProperties().getHttpProxyPassword());
    }
    
    @Test
    public void testComponentConfiguration() throws Exception {
        TwitterComponent comp = context.getComponent("twitter", TwitterComponent.class);
        EndpointConfiguration conf = comp.createConfiguration("twitter:search?keywords=camel");

        assertEquals("camel", conf.getParameter("keywords"));

        ComponentConfiguration compConf = comp.createComponentConfiguration();
        String json = compConf.createParameterJsonSchema();
        assertNotNull(json);

        // REVIST this comparison test may be sensitive to some changes.
        assertTrue(json.contains("\"accessToken\": { \"kind\": \"parameter\", \"group\": \"common\", \"type\": \"string\""));
        assertTrue(json.contains("\"consumerKey\": { \"kind\": \"parameter\", \"group\": \"common\", \"type\": \"string\""));
    }

    @Test
    public void testComponentDocumentation() throws Exception {
        CamelContext context = new DefaultCamelContext();
        String html = context.getComponentDocumentation("twitter");
        assertNotNull("Should have found some auto-generated HTML", html);
    }

}
