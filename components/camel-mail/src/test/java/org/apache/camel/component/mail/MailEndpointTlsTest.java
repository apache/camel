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
package org.apache.camel.component.mail;

import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MailEndpointTlsTest extends CamelTestSupport {

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "smtp" },
                { "smtps" },
                { "pop3" },
                { "pop3s" },
                { "imap" },
                { "imaps" }
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testMailEndpointTlsConfig(String protocol) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail." + protocol + ".starttls.enable", "true");

        MailConfiguration cfg = new MailConfiguration();
        cfg.setPort(21);
        cfg.configureProtocol(protocol);
        cfg.setHost("myhost");
        cfg.setUsername("james");
        cfg.setPassword("secret");
        cfg.setAdditionalJavaMailProperties(properties);

        assertTrue(cfg.isStartTlsEnabled());

        Properties javaMailProperties = cfg.createJavaMailSender().getJavaMailProperties();
        assertNull(javaMailProperties.get("mail." + protocol + ".ssl.socketFactory"));
        assertNull(javaMailProperties.get("mail." + protocol + ".ssl.socketFactory.port"));
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testMailEndpointNoTlsConfig(String protocol) throws Exception {
        MailConfiguration cfg = new MailConfiguration();
        cfg.setPort(21);
        cfg.configureProtocol(protocol);
        cfg.setHost("myhost");
        cfg.setUsername("james");
        cfg.setPassword("secret");
        cfg.setSslContextParameters(MailTestHelper.createSslContextParameters());

        Properties javaMailProperties = cfg.createJavaMailSender().getJavaMailProperties();

        assertFalse(cfg.isStartTlsEnabled());

        if (protocol.endsWith("s")) {
            assertTrue(cfg.isSecureProtocol());
            assertNotNull(javaMailProperties.get("mail." + protocol + ".socketFactory"));
            assertNotNull(javaMailProperties.get("mail." + protocol + ".socketFactory.fallback"));
            assertNotNull(javaMailProperties.get("mail." + protocol + ".socketFactory.port"));
        } else {
            assertFalse(cfg.isSecureProtocol());
            assertNull(javaMailProperties.get("mail." + protocol + ".socketFactory"));
            assertNull(javaMailProperties.get("mail." + protocol + ".socketFactory.fallback"));
            assertNull(javaMailProperties.get("mail." + protocol + ".socketFactory.port"));
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testMailEndpointTlsSslContextParametersConfig(String protocol) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail." + protocol + ".starttls.enable", "true");

        MailConfiguration cfg = new MailConfiguration();
        cfg.setPort(21);
        cfg.configureProtocol(protocol);
        cfg.setHost("myhost");
        cfg.setUsername("james");
        cfg.setPassword("secret");
        cfg.setSslContextParameters(MailTestHelper.createSslContextParameters());
        cfg.setAdditionalJavaMailProperties(properties);

        assertTrue(cfg.isStartTlsEnabled());

        Properties javaMailProperties = cfg.createJavaMailSender().getJavaMailProperties();
        assertNotNull(javaMailProperties.get("mail." + protocol + ".ssl.socketFactory"));
        assertNotNull(javaMailProperties.get("mail." + protocol + ".ssl.socketFactory.port"));
    }

}
