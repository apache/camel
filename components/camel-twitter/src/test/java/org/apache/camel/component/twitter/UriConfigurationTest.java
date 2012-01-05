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
import org.apache.camel.Endpoint;
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

        Assert.assertTrue(!twitterEndpoint.getProperties().getConsumerKey().isEmpty());
        Assert.assertTrue(!twitterEndpoint.getProperties().getConsumerSecret().isEmpty());
        Assert.assertTrue(!twitterEndpoint.getProperties().getAccessToken().isEmpty());
        Assert.assertTrue(!twitterEndpoint.getProperties().getAccessTokenSecret().isEmpty());
    }
}
