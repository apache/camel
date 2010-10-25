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
package org.apache.camel.component.gae.auth;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.apache.camel.component.gae.auth.GAuthTokenSecret.COOKIE_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/org/apache/camel/component/gae/auth/context.xml"})
public class GAuthRouteBuilderTest {

    @Autowired
    private ProducerTemplate template;

    @Test
    public void testAuthorizeCs() throws Exception {
        Exchange exchange = template.request("direct:input1-cs", new Processor() {
            public void process(Exchange exchange) throws Exception {
                // ...
            }
        });
        assertEquals("gauth-token-secret=testRequestTokenSecret1", exchange.getOut().getHeader("Set-Cookie"));
        assertEquals(302, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("https://www.google.com/accounts/OAuthAuthorizeToken" 
            + "?oauth_token=testRequestToken1" 
            + "&oauth_callback=http%3A%2F%2Ftest.example.org%2Fhandler", 
            exchange.getOut().getHeader("Location"));
    }

    @Test
    public void testAuthorizePk() throws Exception {
        Exchange exchange = template.request("direct:input1-pk", new Processor() {
            public void process(Exchange exchange) throws Exception {
                // ...
            }
        });
        assertNull(exchange.getOut().getHeader("Set-Cookie"));
        assertEquals(302, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("https://www.google.com/accounts/OAuthAuthorizeToken" 
            + "?oauth_token=testRequestToken1" 
            + "&oauth_callback=http%3A%2F%2Ftest.example.org%2Fhandler", 
            exchange.getOut().getHeader("Location"));
    }

    @Test
    public void testUpgradeCs() throws Exception {
        Exchange exchange = template.request("direct:input2-cs", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader("auth_token", "testToken1");
                exchange.getIn().setHeader("auth_verifier", "testVerifier1");
                exchange.getIn().setHeader("Cookie", COOKIE_NAME + "=testSecret1");
            }
        });
        assertEquals("testAccessTokenSecret1", exchange.getOut().getHeader(GAuthUpgradeBinding.GAUTH_ACCESS_TOKEN_SECRET));
        assertEquals("testAccessToken1", exchange.getOut().getHeader(GAuthUpgradeBinding.GAUTH_ACCESS_TOKEN));
    }

    @Test
    // should be in binding test (remove from here)
    public void testUpgradeCsNoCookie() throws Exception {
        Exchange exchange = template.request("direct:input2-cs", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader("auth_token", "testToken1");
                exchange.getIn().setHeader("auth_verifier", "testVerifier1");
            }
        });
        assertEquals(GAuthException.class, exchange.getException().getClass());
    }

    @Test
    public void testUpgradePk() throws Exception {
        Exchange exchange = template.request("direct:input2-pk", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader("auth_token", "testToken1");
                exchange.getIn().setHeader("auth_verifier", "testVerifier1");
            }
        });
        assertEquals("testAccessTokenSecret1", exchange.getOut().getHeader(GAuthUpgradeBinding.GAUTH_ACCESS_TOKEN_SECRET));
        assertEquals("testAccessToken1", exchange.getOut().getHeader(GAuthUpgradeBinding.GAUTH_ACCESS_TOKEN));
    }

}
