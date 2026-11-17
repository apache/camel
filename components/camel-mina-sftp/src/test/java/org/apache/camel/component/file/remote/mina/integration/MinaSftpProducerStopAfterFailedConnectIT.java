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
package org.apache.camel.component.file.remote.mina.integration;

import java.lang.reflect.Field;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.apache.camel.component.file.remote.mina.MinaSftpOperations;
import org.apache.camel.component.file.remote.mina.MinaSftpProducer;
import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.auth.pubkey.RejectAllPublickeyAuthenticator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A connect that fails, here as the server rejects the password, has already created and started the SSH client, but
 * left no open session: the stop of the producer must still stop the SSH client, so that its NIO2 and timer threads do
 * not stay alive (CAMEL-24273).
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class MinaSftpProducerStopAfterFailedConnectIT extends CamelTestSupport {

    @RegisterExtension
    PasswordCheckingSftpEmbeddedService service = new PasswordCheckingSftpEmbeddedService();

    @Test
    void stopAfterFailedConnectStopsSshClient() throws Exception {
        MinaSftpEndpoint endpoint = context.getEndpoint(
                "mina-sftp://localhost:" + service.getPort() + "/{{ftp.root.dir}}/upload?username=admin&password=wrong"
                                                        + "&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                                                        + "&maximumReconnectAttempts=0",
                MinaSftpEndpoint.class);
        MinaSftpOperations operations = (MinaSftpOperations) endpoint.createRemoteFileOperations();
        MinaSftpProducer producer = new MinaSftpProducer(endpoint, operations);
        producer.start();

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
        producer.process(exchange);
        assertNotNull(exchange.getException(GenericFileOperationFailedException.class),
                "The upload should fail, as the server rejects the password, exception: " + exchange.getException());
        assertFalse(operations.isConnected(), "The failed connect should leave no open session");

        SshClient sshClient = sshClientOf(operations);
        assertNotNull(sshClient, "The failed connect should have created the SSH client");
        assertTrue(sshClient.isStarted(), "The failed connect should have started the SSH client");

        producer.stop();

        assertFalse(sshClient.isStarted(), "The stop of the producer should stop the SSH client");
        assertTrue(sshClient.isClosed(), "The stop of the producer should close the SSH client");
    }

    private static SshClient sshClientOf(MinaSftpOperations operations) throws Exception {
        Field field = MinaSftpOperations.class.getDeclaredField("sshClient");
        field.setAccessible(true);
        return (SshClient) field.get(operations);
    }

    /**
     * The embedded SFTP server, which accepts only the password {@code admin}, and no public key (the SSH client offers
     * the keys of the user, such as {@code ~/.ssh/id_rsa}).
     */
    static class PasswordCheckingSftpEmbeddedService extends SftpEmbeddedService {

        @Override
        public void setUpServer() throws Exception {
            super.setUpServer();
            sshd.setPasswordAuthenticator((username, password, session) -> "admin".equals(password));
        }

        @Override
        protected PublickeyAuthenticator getPublickeyAuthenticator() {
            return RejectAllPublickeyAuthenticator.INSTANCE;
        }
    }
}
