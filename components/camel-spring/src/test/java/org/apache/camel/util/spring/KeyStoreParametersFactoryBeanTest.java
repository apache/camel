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
package org.apache.camel.util.spring;

import javax.annotation.Resource;

import org.apache.camel.util.jsse.KeyStoreParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class KeyStoreParametersFactoryBeanTest {
    
    @Resource
    KeyStoreParameters ksp;
    
    @Resource(name = "&ksp")
    KeyStoreParametersFactoryBean kspfb;
    
    @Test
    public void testKeyStoreParameters() {
        assertEquals("keystore.jks", ksp.getResource());
        assertEquals("jks", ksp.getType());
        assertEquals("provider", ksp.getProvider());
        assertEquals("password", ksp.getPassword());
        
        assertEquals("test", kspfb.getCamelContext().getName());
    }
}
