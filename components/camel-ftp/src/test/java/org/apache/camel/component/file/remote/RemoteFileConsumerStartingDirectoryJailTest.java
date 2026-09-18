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
package org.apache.camel.component.file.remote;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file name used to build the path of a polled file comes from the directory listing returned by the remote server,
 * so it is not guaranteed to be a single path segment. Verifies the resolved path is kept inside the directory being
 * polled before it is used as the operand for retrieving, deleting or renaming.
 */
class RemoteFileConsumerStartingDirectoryJailTest extends CamelTestSupport {

    private RemoteFileConsumer<?> consumer(String uri) throws Exception {
        RemoteFileEndpoint<?> endpoint = context.getEndpoint(uri, RemoteFileEndpoint.class);
        return (RemoteFileConsumer<?>) endpoint.createConsumer(exchange -> {
        });
    }

    @Test
    void shouldAcceptPathsWithinTheStartingDirectory() throws Exception {
        RemoteFileConsumer<?> consumer = consumer("ftp://hostname/poll");

        assertTrue(consumer.isWithinStartingDirectory("poll/file.txt"));
        assertTrue(consumer.isWithinStartingDirectory("poll/sub/file.txt"));
        // a ../ that still resolves back inside the polled directory is legitimate
        assertTrue(consumer.isWithinStartingDirectory("poll/sub/../file.txt"));
    }

    @Test
    void shouldRejectPathsEscapingTheStartingDirectory() throws Exception {
        RemoteFileConsumer<?> consumer = consumer("ftp://hostname/poll");

        assertFalse(consumer.isWithinStartingDirectory("poll/a/../../../secret.txt"));
        assertFalse(consumer.isWithinStartingDirectory("poll/../secret.txt"));
        assertFalse(consumer.isWithinStartingDirectory("poll/../../etc/shadow"));
        // a sibling directory whose name merely extends the polled directory name is not contained
        assertFalse(consumer.isWithinStartingDirectory("poll/../pollute/secret.txt"));
        assertFalse(consumer.isWithinStartingDirectory(null));
    }

    @Test
    void shouldRejectPathsEscapingTheStartingDirectoryOverSftp() throws Exception {
        RemoteFileConsumer<?> consumer = consumer("sftp://hostname/poll");

        assertTrue(consumer.isWithinStartingDirectory("poll/file.txt"));
        assertFalse(consumer.isWithinStartingDirectory("poll/a/../../../secret.txt"));
        assertFalse(consumer.isWithinStartingDirectory("poll/../secret.txt"));
    }

    @Test
    void shouldRejectUpwardsPathsWhenPollingTheSessionRoot() throws Exception {
        RemoteFileConsumer<?> consumer = consumer("ftp://hostname");

        assertTrue(consumer.isWithinStartingDirectory("file.txt"));
        // no directory is configured, but navigating above the session root still escapes
        assertFalse(consumer.isWithinStartingDirectory("../secret.txt"));
    }
}
