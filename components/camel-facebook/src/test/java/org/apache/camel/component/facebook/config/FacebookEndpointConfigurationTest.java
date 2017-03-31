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
package org.apache.camel.component.facebook.config;


import org.apache.camel.component.facebook.FacebookComponent;
import org.apache.camel.component.facebook.FacebookEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class FacebookEndpointConfigurationTest extends CamelTestSupport {

    @Test
    public void testConfigurationBeanUriParam() throws Exception {
        FacebookComponent component = new FacebookComponent(context);
        FacebookEndpoint facebookEndpoint = (FacebookEndpoint) component.createEndpoint("facebook://getFeed?configuration=#configuration");
        assertTrue("Configuration bean wasn't taken into account!", "fakeId".equals(facebookEndpoint.getConfiguration().getOAuthAppId()));
        assertTrue("Configuration bean wasn't taken into account!", "fakeSecret".equals(facebookEndpoint.getConfiguration().getOAuthAppSecret()));
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        FacebookEndpointConfiguration facebookEndpointConfiguration = new FacebookEndpointConfiguration();
        facebookEndpointConfiguration.setOAuthAppId("fakeId");
        facebookEndpointConfiguration.setOAuthAppSecret("fakeSecret");
        jndi.bind("configuration", facebookEndpointConfiguration);
        return jndi;
    }

}
