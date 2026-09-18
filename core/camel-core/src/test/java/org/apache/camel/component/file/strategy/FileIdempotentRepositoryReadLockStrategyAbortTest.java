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
package org.apache.camel.component.file.strategy;

import java.io.File;
import java.nio.file.Files;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests releaseExclusiveReadLockOnAbort directly: it must only remove the idempotent key when this strategy itself
 * added it during acquireExclusiveReadLock. abort() can also be reached after a successful acquire, e.g. when a preMove
 * rename fails afterwards, so a pre-existing key (owned by a previous run or another node) must never be touched, while
 * a key we own must be cleaned up.
 */
class FileIdempotentRepositoryReadLockStrategyAbortTest extends ContextTestSupport {

    private FileIdempotentRepositoryReadLockStrategy newStrategy(MemoryIdempotentRepository repo) throws Exception {
        ServiceHelper.startService(repo);

        FileEndpoint endpoint = context.getEndpoint(fileUri(), FileEndpoint.class);

        FileIdempotentRepositoryReadLockStrategy strategy = new FileIdempotentRepositoryReadLockStrategy();
        strategy.setCamelContext(context);
        strategy.setIdempotentRepository(repo);
        strategy.prepareOnStartup(null, endpoint);
        ServiceHelper.startService(strategy);
        return strategy;
    }

    private GenericFile<File> newGenericFile(String name) throws Exception {
        File file = testFile(name).toFile();
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), "Hello World");

        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(file);
        genericFile.setAbsoluteFilePath(file.getAbsolutePath());
        return genericFile;
    }

    @Test
    void testAbortDoesNotRemovePreExistingKey() throws Exception {
        MemoryIdempotentRepository repo = new MemoryIdempotentRepository();
        FileIdempotentRepositoryReadLockStrategy strategy = newStrategy(repo);

        GenericFile<File> genericFile = newGenericFile("hello.txt");
        Exchange exchange = new DefaultExchange(context);

        // simulate the key already being owned by a previous, already-committed run
        repo.add(exchange, genericFile.getAbsoluteFilePath());

        boolean acquired = strategy.acquireExclusiveReadLock(null, genericFile, exchange);
        assertThat(acquired).as("must not acquire a key that already exists").isFalse();

        strategy.releaseExclusiveReadLockOnAbort(null, genericFile, exchange);

        assertThat(repo.contains(genericFile.getAbsoluteFilePath()))
                .as("pre-existing key must be retained since we never owned it")
                .isTrue();
    }

    @Test
    void testAbortRemovesKeyWeAcquired() throws Exception {
        MemoryIdempotentRepository repo = new MemoryIdempotentRepository();
        FileIdempotentRepositoryReadLockStrategy strategy = newStrategy(repo);

        GenericFile<File> genericFile = newGenericFile("hello2.txt");
        Exchange exchange = new DefaultExchange(context);

        boolean acquired = strategy.acquireExclusiveReadLock(null, genericFile, exchange);
        assertThat(acquired).as("must acquire a key that does not yet exist").isTrue();

        // simulate begin() throwing afterwards for an unrelated reason (e.g. a preMove rename
        // failing), which still routes into releaseExclusiveReadLockOnAbort
        strategy.releaseExclusiveReadLockOnAbort(null, genericFile, exchange);

        assertThat(repo.contains(genericFile.getAbsoluteFilePath()))
                .as("key we acquired ourselves must be removed on abort")
                .isFalse();
    }
}
