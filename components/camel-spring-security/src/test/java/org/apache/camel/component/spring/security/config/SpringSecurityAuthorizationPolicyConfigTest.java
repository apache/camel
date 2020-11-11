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
package org.apache.camel.component.spring.security.config;

import org.apache.camel.component.spring.security.SpringSecurityAuthorizationPolicy;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SpringSecurityAuthorizationPolicyConfigTest {

    private AbstractXmlApplicationContext context;

    @BeforeEach
    public void setUp() {
        context = new ClassPathXmlApplicationContext(
                new String[] { "/org/apache/camel/component/spring/security/config/SpringSecurityAuthorizationPolicy.xml" });
    }

    @AfterEach
    public void tearDown() {
        IOHelper.close(context);
    }

    @Test
    public void testAuthorizationPolicy() {

        SpringSecurityAuthorizationPolicy adminPolicy = context.getBean("admin", SpringSecurityAuthorizationPolicy.class);
        assertNotNull(adminPolicy, "We should get admin policy");
        assertNotNull(adminPolicy.getAccessDecisionManager(), "The accessDecisionManager should not be null");
        assertNotNull(adminPolicy.getAuthenticationManager(), "The authenticationManager should not be null");
        assertNotNull(adminPolicy.getSpringSecurityAccessPolicy(), "The springSecurityAccessPolicy should not be null");

        SpringSecurityAuthorizationPolicy userPolicy = context.getBean("user", SpringSecurityAuthorizationPolicy.class);
        assertNotNull(userPolicy, "We should get user policy");
        assertNotNull(userPolicy.getAccessDecisionManager(), "The accessDecisionManager should not be null");
        assertNotNull(userPolicy.getAuthenticationManager(), "The authenticationManager should not be null");
        assertNotNull(userPolicy.getSpringSecurityAccessPolicy(), "The springSecurityAccessPolicy should not be null");

        assertEquals(adminPolicy.getAccessDecisionManager(), userPolicy.getAccessDecisionManager(),
                "user policy and admin policy should have same accessDecisionManager");
        assertEquals(adminPolicy.getAuthenticationManager(), userPolicy.getAuthenticationManager(),
                "user policy and admin policy should have same authenticationManager");
    }

}
