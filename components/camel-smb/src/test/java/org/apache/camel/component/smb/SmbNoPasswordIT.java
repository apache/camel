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
package org.apache.camel.component.smb;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An endpoint without a password, or without a username, fails with a clear error instead of a NullPointerException.
 * The test server does not map unknown users to guest, so the guest login fails too.
 */
class SmbNoPasswordIT extends SmbServerTestSupport {

    @Test
    void usernameWithoutPassword() {
        assertConnectFails(String.format("smb:%s/%s?username=%s", service.address(), service.shareName(), service.username()),
                "Authentication failed");
    }

    @Test
    void guestWithoutPassword() {
        assertConnectFails(String.format("smb:%s/%s?username=Guest", service.address(), service.shareName()),
                "Authentication failed");
    }

    @Test
    void noUsername() {
        assertConnectFails(String.format("smb:%s/%s?password=secret", service.address(), service.shareName()),
                "as no username is set");
    }

    private void assertConnectFails(String uri, String reason) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "no-password.txt"));

        GenericFileOperationFailedException cause = assertInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith("Cannot connect to: "), cause.getMessage());
        assertTrue(cause.getMessage().contains(reason), cause.getMessage());
    }
}
