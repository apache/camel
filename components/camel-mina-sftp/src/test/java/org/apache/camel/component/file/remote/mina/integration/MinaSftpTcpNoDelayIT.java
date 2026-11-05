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
import java.net.StandardSocketOptions;

import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.component.file.remote.SftpRemoteFile;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.io.nio2.Nio2Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mina-sftp connections set TCP_NODELAY, as the JSch-based sftp component does: Apache MINA SSHD leaves it off by
 * default.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class MinaSftpTcpNoDelayIT extends MinaSftpServerTestSupport {

    @Test
    void setsTcpNoDelayOnTheConnection() throws Exception {
        MinaSftpEndpoint endpoint = context.getEndpoint("mina-sftp://localhost:" + service.getPort() + "/"
                                                        + service.getFtpRootDir()
                                                        + "?username=admin&password=admin&strictHostKeyChecking=no"
                                                        + "&useUserKnownHostsFile=false",
                MinaSftpEndpoint.class);
        RemoteFileOperations<SftpRemoteFile> operations = endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);
        try {
            ClientSession session = (ClientSession) getField(operations, "session");
            Nio2Session connection = (Nio2Session) session.getIoSession();
            assertTrue(connection.getSocket().getOption(StandardSocketOptions.TCP_NODELAY), "TCP_NODELAY");
        } finally {
            operations.disconnect();
        }
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
