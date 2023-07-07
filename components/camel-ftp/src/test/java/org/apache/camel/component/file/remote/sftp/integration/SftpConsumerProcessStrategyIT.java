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
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpConsumerProcessStrategyIT extends SftpServerTestSupport {

    @BindToRegistry("myStrategy")
    private final MyStrategy<File> myStrategy = new MyStrategy<>();

    @Test
    public void testSftpConsume() {
        // create file using regular file
        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        String out = consumer.receiveBody("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                                          + "?username=admin&password=admin&processStrategy=#myStrategy&knownHostsFile="
                                          + service.getKnownHostsFile(),
                5000, String.class);
        assertNotNull(out);
        // Apache SSHD appends \u0000 at last byte in retrieved file
        assertTrue(out.startsWith("Hello World"));
        assertEquals(1, myStrategy.getInvoked(), "CustomProcessStrategy should have been invoked 1 times");
    }

    private static class MyStrategy<T extends File> implements GenericFileProcessStrategy<T> {

        private final LongAdder invoked = new LongAdder();

        @Override
        public void prepareOnStartup(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint) {
            // noop
        }

        @Override
        public boolean begin(
                GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) {
            return true;
        }

        @Override
        public void abort(
                GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) {
            // noop
        }

        @Override
        public void commit(
                GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) {
            invoked.increment();
        }

        @Override
        public void rollback(
                GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) {
            // noop
        }

        int getInvoked() {
            return invoked.intValue();
        }
    }
}
