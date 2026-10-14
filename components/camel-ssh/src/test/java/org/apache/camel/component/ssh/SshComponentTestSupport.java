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
package org.apache.camel.component.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.server.SshServer;

public class SshComponentTestSupport extends CamelTestSupport {
    protected SshServer sshd;
    protected int port;

    @Override
    public void doPreSetup() throws Exception {
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(0);
        sshd.setKeyPairProvider(new FileKeyPairProvider(Paths.get(getHostKey())));
        sshd.setCommandFactory(new TestEchoCommandFactory());
        sshd.setPasswordAuthenticator((username, password, session) -> true);
        sshd.setPublickeyAuthenticator((username, key, session) -> true);
        sshd.start();
        port = sshd.getPort();
    }

    protected String getHostKey() {
        return "src/test/resources/hostkey.pem";
    }

    /**
     * The public key of the test server, in the form used by a known_hosts entry.
     */
    protected String serverPublicKey() throws IOException, GeneralSecurityException {
        KeyPair keyPair = sshd.getKeyPairProvider().loadKeys(null).iterator().next();
        return PublicKeyEntry.toString(keyPair.getPublic());
    }

    /**
     * Writes a known_hosts file for this test and returns it as a resource. The test server listens on a random port,
     * so its entries must use the [localhost]:port form.
     */
    protected String writeKnownHosts(String name, String... entries) throws IOException {
        Path file = Paths.get("target", "known_hosts", getClass().getSimpleName(), name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", entries) + "\n");
        return "file:" + file;
    }

    @Override
    public void doPostTearDown() throws IOException, InterruptedException {
        if (sshd != null) {
            sshd.stop(true);
            Thread.sleep(50);
        }
    }
}
