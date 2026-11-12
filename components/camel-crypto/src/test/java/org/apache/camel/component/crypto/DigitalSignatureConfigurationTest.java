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
package org.apache.camel.component.crypto;

import java.security.SecureRandom;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The reference-name options resolve their object from the registry, and each of them has to do so on its own: a
 * SecureRandom reference does not depend on a keystore being configured.
 */
public class DigitalSignatureConfigurationTest {

    @Test
    public void secureRandomNameShouldResolveWithoutAKeystore() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            SecureRandom random = new SecureRandom();
            context.getRegistry().bind("mySecureRandom", random);

            DigitalSignatureConfiguration configuration = new DigitalSignatureConfiguration();
            configuration.setCamelContext(context);
            configuration.setSecureRandomName("mySecureRandom");

            assertSame(random, configuration.getSecureRandom(),
                    "the SecureRandom named by secureRandomName has to be used");
            assertEquals("mySecureRandom", configuration.getSecureRandomName());
        }
    }

    @Test
    public void secureRandomNameShouldResolveTheSameWayAsTheOtherReferenceNames() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            SecureRandom random = new SecureRandom();
            context.getRegistry().bind("mySecureRandom", random);

            // the order the endpoint's configurer happens to set the options in must not matter
            DigitalSignatureConfiguration configuration = new DigitalSignatureConfiguration();
            configuration.setSecureRandomName("mySecureRandom");
            assertNull(configuration.getSecureRandom(), "nothing can be resolved before the context is known");

            configuration.setCamelContext(context);

            assertSame(random, configuration.getSecureRandom(),
                    "setCamelContext retries the reference names, so the SecureRandom has to be resolved then");
        }
    }

    @Test
    public void anUnknownSecureRandomNameShouldNotClearTheConfiguredOne() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            SecureRandom random = new SecureRandom();

            DigitalSignatureConfiguration configuration = new DigitalSignatureConfiguration();
            configuration.setCamelContext(context);
            configuration.setSecureRandom(random);
            configuration.setSecureRandomName("notInTheRegistry");

            assertSame(random, configuration.getSecureRandom(),
                    "a name that resolves to nothing must leave the configured SecureRandom alone");
        }
    }

}
