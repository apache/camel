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
package org.apache.camel.dsl.xml.io;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.xml.io.beans.MyTlsClient;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The security policy checks the properties of the beans that an XML route file defines, as it checks a bean configured
 * from camel.beans.* properties.
 */
class XmlMainSecurityPolicyBeansTest {

    private static final String ROUTES = "org/apache/camel/dsl/xml/io/security-policy-beans.xml";

    @Test
    void insecureBeanPropertyFailsStartupWithProdProfile() {
        Main main = new Main();
        try {
            main.addInitialProperty("camel.main.profile", "prod");
            main.configure().withRoutesIncludePattern(ROUTES);

            RuntimeCamelException ex = assertThrows(RuntimeCamelException.class, main::start);
            assertTrue(ex.getMessage().contains("[insecure:ssl]"), ex.getMessage());
            assertTrue(ex.getMessage().contains("[camel.beans.myClient.trustAllCertificates]"), ex.getMessage());
        } finally {
            main.stop();
        }
    }

    @Test
    void allowedBeanPropertyStartsWithProdProfile() {
        Main main = new Main();
        try {
            main.addInitialProperty("camel.main.profile", "prod");
            main.addInitialProperty("camel.security.allowedProperties", "camel.beans.myClient.trustAllCertificates");
            main.configure().withRoutesIncludePattern(ROUTES);
            main.start();

            MyTlsClient client = main.getCamelContext().getRegistry().lookupByNameAndType("myClient", MyTlsClient.class);
            assertNotNull(client);
            assertTrue(client.isTrustAllCertificates());
        } finally {
            main.stop();
        }
    }
}
