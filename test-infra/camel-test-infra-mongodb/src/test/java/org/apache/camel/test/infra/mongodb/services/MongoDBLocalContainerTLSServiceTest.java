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
package org.apache.camel.test.infra.mongodb.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoDBLocalContainerTLSServiceTest {

    @Test
    void tlsClasspathResourcesExist() {
        ClassLoader loader = MongoDBLocalContainerTLSService.class.getClassLoader();

        assertNotNull(loader.getResource(MongoDBLocalContainerTLSService.SERVER_CERT_RESOURCE));
        assertNotNull(loader.getResource(MongoDBLocalContainerTLSService.CA_CERT_RESOURCE));
        assertNotNull(loader.getResource(MongoDBLocalContainerTLSService.CERT_RESOURCE_PATH + "/ca-truststore.jks"));
    }

    @Test
    void mountableFilesResolveToNonEmptyTlsCertificates() throws Exception {
        MountableFile serverCert = MountableFile.forClasspathResource(MongoDBLocalContainerTLSService.SERVER_CERT_RESOURCE);
        MountableFile caCert = MountableFile.forClasspathResource(MongoDBLocalContainerTLSService.CA_CERT_RESOURCE);

        Path serverPath = Path.of(serverCert.getFilesystemPath());
        Path caPath = Path.of(caCert.getFilesystemPath());

        assertTrue(Files.exists(serverPath));
        assertTrue(Files.exists(caPath));
        assertTrue(Files.size(serverPath) > 0);
        assertTrue(Files.size(caPath) > 0);
    }

    @Test
    void initContainerConfiguresIndividualCertCopyAndTlsCommand() {
        MongoDBLocalContainerTLSService service = new MongoDBLocalContainerTLSService("mirror.gcr.io/mongo:7.0.31-jammy");
        GenericContainer<?> container = service.getContainer();

        String[] commandParts = container.getCommandParts();

        assertTrue(Arrays.asList(commandParts).contains("--tlsMode"));
        assertTrue(Arrays.asList(commandParts).contains("requireTLS"));
        assertTrue(Arrays.asList(commandParts).contains(MongoDBLocalContainerTLSService.CONTAINER_SSL_DIR + "/server.pem"));
        assertTrue(Arrays.asList(commandParts).contains(MongoDBLocalContainerTLSService.CONTAINER_SSL_DIR + "/ca.pem"));

        // Directory classpath mapping breaks on Testcontainers 2.x copy strategy (CAMEL-24323).
        assertDoesNotThrow(() -> MountableFile.forClasspathResource(MongoDBLocalContainerTLSService.SERVER_CERT_RESOURCE));
        assertDoesNotThrow(() -> MountableFile.forClasspathResource(MongoDBLocalContainerTLSService.CA_CERT_RESOURCE));
    }
}
