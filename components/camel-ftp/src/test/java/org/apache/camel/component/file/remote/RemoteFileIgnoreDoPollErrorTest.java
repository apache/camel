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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProducer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class RemoteFileIgnoreDoPollErrorTest {
    private final RemoteFileEndpoint<Object> remoteFileEndpoint = new RemoteFileEndpoint<Object>() {
        @Override
        protected RemoteFileConsumer<Object> buildConsumer(Processor processor) {
            return null;
        }

        @Override
        protected GenericFileProducer<Object> buildProducer() {
            return null;
        }

        @Override
        public RemoteFileOperations<Object> createRemoteFileOperations() throws Exception {
            return null;
        }

        @Override
        public String getScheme() {
            return null;
        }
    };

    @Test
    public void testReadDirErrorIsHandled() throws Exception {
        RemoteFileConsumer<Object> consumer = getRemoteFileConsumer("true", true);
        boolean result = consumer.doSafePollSubDirectory("anyPath", "adir", new ArrayList<GenericFile<Object>>(), 0);
        assertTrue(result);
    }

    @Test
    public void testReadDirErrorIsHandledWithNoMorePoll() throws Exception {
        RemoteFileConsumer<Object> consumer = getRemoteFileConsumer("false", true);
        boolean result = consumer.doSafePollSubDirectory("anyPath", "adir", new ArrayList<GenericFile<Object>>(), 0);
        assertFalse(result);
    }

    @Test
    public void testReadDirErrorNotHandled() throws Exception {
        RemoteFileConsumer<Object> consumer = getRemoteFileConsumer("IllegalStateException", false);
        try {
            consumer.doSafePollSubDirectory("anyPath", "adir", new ArrayList<GenericFile<Object>>(), 0);
            fail("Must throw wrapped IllegalStateException in GenericFileOperationFailedException");
        } catch (GenericFileOperationFailedException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void testReadDirErrorNotHandledForGenericFileOperationException() throws Exception {
        RemoteFileConsumer<Object> consumer = getRemoteFileConsumer("GenericFileOperationFailedException", false);
        try {
            consumer.doSafePollSubDirectory("anyPath", "adir", new ArrayList<GenericFile<Object>>(), 0);
            fail("Must throw GenericFileOperationFailedException");
        } catch (GenericFileOperationFailedException e) {
            assertNull(e.getCause());
        }
    }

    private RemoteFileConsumer<Object> getRemoteFileConsumer(final String doPollResult, final boolean ignoreCannotRetrieveFile) {
        return new RemoteFileConsumer<Object>(remoteFileEndpoint, null, null, null) {
            @Override
            protected boolean doPollDirectory(String absolutePath, String dirName, List<GenericFile<Object>> genericFiles, int depth) {
                if ("IllegalStateException".equals(doPollResult)) {
                    throw new IllegalStateException("Problem");
                } else if ("GenericFileOperationFailedException".equals(doPollResult)) {
                    throw new GenericFileOperationFailedException("Perm error");
                } else {
                    return "true".equals(doPollResult);
                }
            }

            @Override
            protected boolean pollDirectory(String fileName, List<GenericFile<Object>> genericFiles, int depth) {
                return false;
            }

            @Override
            protected boolean isMatched(GenericFile<Object> file, String doneFileName, List<Object> files) {
                return false;
            }

            @Override
            protected boolean ignoreCannotRetrieveFile(String name, Exchange exchange, Exception cause) {
                return ignoreCannotRetrieveFile;
            }

            @Override
            protected void updateFileHeaders(GenericFile<Object> genericFile, Message message) {
                // noop
            }
        };
    }
}
