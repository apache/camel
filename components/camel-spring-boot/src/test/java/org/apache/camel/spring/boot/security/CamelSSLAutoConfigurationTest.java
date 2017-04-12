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
package org.apache.camel.spring.boot.security;

import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.util.jsse.GlobalSSLContextParametersSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Testing the ssl configuration
 */
@RunWith(SpringRunner.class)
@SpringBootApplication
@DirtiesContext
@ContextConfiguration(classes = {CamelSSLAutoConfiguration.class, CamelAutoConfiguration.class})
@SpringBootTest(properties = {
        "camel.ssl.config.cert-alias=web",
        "camel.ssl.config.key-managers.key-password=changeit",
        "camel.ssl.config.key-managers.key-store.password=changeit",
        "camel.ssl.config.key-managers.key-store.type=PKCS12",
        "camel.ssl.config.trust-managers.key-store.password=changeit",
        "camel.ssl.config.trust-managers.key-store.type=jks"
})
public class CamelSSLAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void checkSSLPropertiesPresent() {
        GlobalSSLContextParametersSupplier supplier = applicationContext.getBean(GlobalSSLContextParametersSupplier.class);
        assertNotNull(supplier);
        assertNotNull(supplier.get());
        assertEquals("web", supplier.get().getCertAlias());
        assertNotNull(supplier.get().getKeyManagers());
        assertEquals("changeit", supplier.get().getKeyManagers().getKeyPassword());
        assertNotNull(supplier.get().getTrustManagers());
        assertNotNull(supplier.get().getTrustManagers().getKeyStore());
        assertEquals("jks", supplier.get().getTrustManagers().getKeyStore().getType());
    }

}

