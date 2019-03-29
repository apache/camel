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
package org.apache.camel.util.spring;

import javax.annotation.Resource;

import org.apache.camel.support.jsse.BaseSSLContextParameters;
import org.apache.camel.support.jsse.ClientAuthentication;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class SSLContextParametersFactoryBeanTest {
    
    @Resource
    SSLContextParameters scp;
    
    @Resource(name = "&scp")
    SSLContextParametersFactoryBean scpfb;
    
    @Test
    public void testKeyStoreParameters() {
        
        assertEquals("provider", scp.getProvider());
        assertEquals("protocol", scp.getSecureSocketProtocol());
        assertEquals("alice", scp.getCertAlias());
        
        validateBaseSSLContextParameters(scp);
        
        assertNotNull(scp.getKeyManagers());
        assertEquals("keyPassword", scp.getKeyManagers().getKeyPassword());
        assertEquals("provider", scp.getKeyManagers().getProvider());
        assertNotNull(scp.getKeyManagers().getKeyStore());
        assertEquals("type", scp.getKeyManagers().getKeyStore().getType());
        
        assertNotNull(scp.getTrustManagers());
        assertEquals("provider", scp.getTrustManagers().getProvider());
        assertNotNull(scp.getTrustManagers().getKeyStore());
        assertEquals("type", scp.getTrustManagers().getKeyStore().getType());
        
        assertNotNull(scp.getSecureRandom());
        assertEquals("provider", scp.getSecureRandom().getProvider());
        assertEquals("algorithm", scp.getSecureRandom().getAlgorithm());
        
        assertNotNull(scp.getClientParameters());
        validateBaseSSLContextParameters(scp.getClientParameters());
        
        assertNotNull(scp.getServerParameters());
        assertEquals(ClientAuthentication.WANT.name(), scp.getServerParameters().getClientAuthentication());
        validateBaseSSLContextParameters(scp.getServerParameters());
        
        assertEquals("test", scpfb.getCamelContext().getName());
        
        assertNotNull(scp.getCamelContext());
        assertNotNull(scp.getCipherSuitesFilter().getCamelContext());
        assertNotNull(scp.getSecureSocketProtocolsFilter().getCamelContext());
        assertNotNull(scp.getSecureRandom().getCamelContext());
        assertNotNull(scp.getKeyManagers().getCamelContext());
        assertNotNull(scp.getKeyManagers().getKeyStore().getCamelContext());
        assertNotNull(scp.getTrustManagers().getCamelContext());
        assertNotNull(scp.getTrustManagers().getKeyStore().getCamelContext());
        assertNotNull(scp.getClientParameters().getCamelContext());
        assertNotNull(scp.getClientParameters().getCipherSuitesFilter().getCamelContext());
        assertNotNull(scp.getClientParameters().getSecureSocketProtocolsFilter().getCamelContext());
        assertNotNull(scp.getServerParameters().getCamelContext());
        assertNotNull(scp.getServerParameters().getCipherSuitesFilter().getCamelContext());
        assertNotNull(scp.getServerParameters().getSecureSocketProtocolsFilter().getCamelContext());
    }
    
    private void validateBaseSSLContextParameters(BaseSSLContextParameters params) {
        assertEquals("1", params.getSessionTimeout());
        
        assertNotNull(params.getCipherSuites());
        assertEquals(1, params.getCipherSuites().getCipherSuite().size());
        assertNotNull(params.getCipherSuitesFilter());
        assertEquals(1, params.getCipherSuitesFilter().getInclude().size());
        assertEquals(1, params.getCipherSuitesFilter().getExclude().size());
        
        assertNotNull(params.getSecureSocketProtocols());
        assertEquals(1, params.getSecureSocketProtocols().getSecureSocketProtocol().size());
        assertNotNull(params.getSecureSocketProtocolsFilter());
        assertEquals(1, params.getSecureSocketProtocolsFilter().getInclude().size());
        assertEquals(1, params.getSecureSocketProtocolsFilter().getExclude().size());
    }
}
