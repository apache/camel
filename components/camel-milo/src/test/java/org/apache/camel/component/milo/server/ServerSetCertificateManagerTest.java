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

package org.apache.camel.component.milo.server;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.component.milo.AbstractMiloServerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test setting the certificate manager
 */
public class ServerSetCertificateManagerTest extends AbstractMiloServerTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServerSetCertificateManagerTest.class);

    @BeforeEach
    public void setup(TestInfo testInfo) {
        final var displayName = testInfo.getDisplayName();
        LOG.info("********************************************************************************");
        LOG.info(displayName);
        LOG.info("********************************************************************************");
    }

    @Override
    protected void configureMiloServer(final MiloServerComponent server) throws Exception {
        super.configureMiloServer(server);

        final Path baseDir = Paths.get("target/testing/cert/default");
        final Path trusted = baseDir.resolve("trusted");

        Files.createDirectories(trusted);
        Files.copy(Paths.get("src/test/resources/ca/cacert.pem"), trusted.resolve("cacert.pem"), REPLACE_EXISTING);

        server.loadServerCertificate(loadDefaultTestKey());
        server.setDefaultCertificateValidator(baseDir.toFile().toString());
    }

    @Test
    public void shouldStart() {}
}
