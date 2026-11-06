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
package org.apache.camel.component.file.remote.sftp.integration;

import java.io.File;

import com.jcraft.jsch.JSch;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hybrid post-quantum key exchange must be usable, and used by default.
 * <p/>
 * jsch proposes {@code mlkem768x25519-sha256} first, but it only implements ML-KEM through the JDK 24+ multi-release
 * classes or through the BouncyCastle lightweight API - so on the Java 21 baseline the group is dropped from the
 * proposal, and the handshake silently falls back to a classical key exchange, unless BouncyCastle is on the runtime
 * classpath.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpPostQuantumKeyExchangeIT extends SftpServerTestSupport {

    private static final String PQC_KEX = "mlkem768x25519-sha256";

    @Test
    public void theDefaultProposalLeadsWithTheHybridGroup() {
        assertTrue(JSch.getConfig("kex").startsWith(PQC_KEX),
                "jsch must propose " + PQC_KEX + " first, but proposes: " + JSch.getConfig("kex"));
    }

    @Test
    public void aTransferNegotiatesTheHybridGroup() {
        // pinning the proposal to the hybrid group only: the handshake can succeed only if ML-KEM is really available
        String uri
                = "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}?username=admin&password=admin"
                  + "&keyExchangeProtocols=" + PQC_KEX
                  + "&knownHostsFile=" + service.getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello-pqc.txt");

        File file = ftpFile("hello-pqc.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
    }
}
