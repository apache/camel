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
package org.apache.camel.component.shiro.security;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ShiroRolesAuthorizationTest extends CamelTestSupport {
    
    @EndpointInject(uri = "mock:success")
    protected MockEndpoint successEndpoint;

    @EndpointInject(uri = "mock:authorizationException")
    protected MockEndpoint failureEndpoint;
    
    private byte[] passPhrase = {
        (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B,
        (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F,
        (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13,
        (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17};
    
    @Test
    public void testShiroAuthorizationFailure() throws Exception {        
        // The user ringo has role sec-level1
        ShiroSecurityToken shiroSecurityToken = new ShiroSecurityToken("ringo", "starr");
        TestShiroSecurityTokenInjector shiroSecurityTokenInjector = new TestShiroSecurityTokenInjector(shiroSecurityToken, passPhrase);
        
        successEndpoint.expectedMessageCount(0);
        failureEndpoint.expectedMessageCount(1);
        
        template.send("direct:secureEndpoint", shiroSecurityTokenInjector);
        
        successEndpoint.assertIsSatisfied();
        failureEndpoint.assertIsSatisfied();
    }
    
    @Test
    public void testSuccessfulAuthorization() throws Exception {        
        // The user george has role sec-level2
        ShiroSecurityToken shiroSecurityToken = new ShiroSecurityToken("george", "harrison");
        TestShiroSecurityTokenInjector shiroSecurityTokenInjector = new TestShiroSecurityTokenInjector(shiroSecurityToken, passPhrase);
        
        successEndpoint.expectedMessageCount(1);
        failureEndpoint.expectedMessageCount(0);
        
        template.send("direct:secureEndpoint", shiroSecurityTokenInjector);
        
        successEndpoint.assertIsSatisfied();
        failureEndpoint.assertIsSatisfied();
    }

    @Test
    public void testSuccessfulAuthorizationForHigherScope() throws Exception {        
        // The user john has role sec-level3 
        ShiroSecurityToken shiroSecurityToken = new ShiroSecurityToken("john", "lennon");
        TestShiroSecurityTokenInjector shiroSecurityTokenInjector = new TestShiroSecurityTokenInjector(shiroSecurityToken, passPhrase);
        
        successEndpoint.expectedMessageCount(1);
        failureEndpoint.expectedMessageCount(0);
        
        template.send("direct:secureEndpoint", shiroSecurityTokenInjector);
        
        successEndpoint.assertIsSatisfied();
        failureEndpoint.assertIsSatisfied();
    }
    
    @Test
    public void testFailureAuthorizationAll() throws Exception {        
        // The user george has role sec-level2 but not sec-level3
        ShiroSecurityToken shiroSecurityToken = new ShiroSecurityToken("george", "harrison");
        TestShiroSecurityTokenInjector shiroSecurityTokenInjector = new TestShiroSecurityTokenInjector(shiroSecurityToken, passPhrase);
        
        successEndpoint.expectedMessageCount(0);
        failureEndpoint.expectedMessageCount(1);
        
        template.send("direct:secureAllEndpoint", shiroSecurityTokenInjector);
        
        successEndpoint.assertIsSatisfied();
        failureEndpoint.assertIsSatisfied();
    }
    
    @Test
    public void testSuccessfulAuthorizationAll() throws Exception {        
        // The user paul has role sec-level2 and sec-level3
        ShiroSecurityToken shiroSecurityToken = new ShiroSecurityToken("paul", "mccartney");
        TestShiroSecurityTokenInjector shiroSecurityTokenInjector = new TestShiroSecurityTokenInjector(shiroSecurityToken, passPhrase);
        
        successEndpoint.expectedMessageCount(1);
        failureEndpoint.expectedMessageCount(0);
        
        template.send("direct:secureAllEndpoint", shiroSecurityTokenInjector);
        
        successEndpoint.assertIsSatisfied();
        failureEndpoint.assertIsSatisfied();
    }
    
    
    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
    
        return new RouteBuilder[] {new RouteBuilder() {
            public void configure() {
                
                List<String> rolesList = new ArrayList<String>();
                rolesList.add("sec-level2");
                rolesList.add("sec-level3");
                
                final ShiroSecurityPolicy securityPolicy = 
                    new ShiroSecurityPolicy("src/test/resources/securityconfig.ini", passPhrase, true);
                securityPolicy.setRolesList(rolesList);
                
                onException(CamelAuthorizationException.class).
                    to("mock:authorizationException");
                
                from("direct:secureEndpoint").
                    policy(securityPolicy).
                    to("log:incoming payload").
                    to("mock:success");
            }
        }, new RouteBuilder() {
            public void configure() {
                
                List<String> rolesList = new ArrayList<String>();
                rolesList.add("sec-level2");
                rolesList.add("sec-level3");
                
                final ShiroSecurityPolicy securityPolicy = 
                    new ShiroSecurityPolicy("src/test/resources/securityconfig.ini", passPhrase, true);
                securityPolicy.setRolesList(rolesList);
                securityPolicy.setAllRolesRequired(true);
                
                onException(CamelAuthorizationException.class).
                    to("mock:authorizationException");
                
                from("direct:secureAllEndpoint").
                    policy(securityPolicy).
                    to("log:incoming payload").
                    to("mock:success");
            }
        }
        };
    }
    
    private static class TestShiroSecurityTokenInjector extends ShiroSecurityTokenInjector {

        TestShiroSecurityTokenInjector(ShiroSecurityToken shiroSecurityToken, byte[] bytes) {
            super(shiroSecurityToken, bytes);
        }
        
        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setHeader(ShiroSecurityConstants.SHIRO_SECURITY_TOKEN, encrypt());
            exchange.getIn().setBody("Beatle Mania");
        }
    }
    
}
