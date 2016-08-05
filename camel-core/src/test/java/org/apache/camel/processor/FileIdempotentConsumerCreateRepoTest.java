/**
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
package org.apache.camel.processor;

import java.io.File;

import static java.util.UUID.randomUUID;

import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.util.FileUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import static org.apache.camel.processor.idempotent.FileIdempotentRepository.fileIdempotentRepository;

public class FileIdempotentConsumerCreateRepoTest extends Assert {

    File store;

    @Test
    public void shouldCreateParentOfRepositoryFileStore() throws Exception {
        File parentDirectory = new File("target/repositoryParent_" + randomUUID());
        store = new File(parentDirectory, "store");
        assertStoreExists(store);
    }

    @Test
    public void shouldUseCurrentDirIfHasNoParentFile() throws Exception {
        String storeFileName = "store" + randomUUID();
        store = new File(storeFileName);
        assertStoreExists(store);
    }


    private void assertStoreExists(File store) throws Exception {
        // Given
        IdempotentRepository<String> repo = fileIdempotentRepository(store);

        // must start repo
        repo.start();

        // When
        repo.add("anyKey");

        // Then
        assertTrue(store.exists());

        repo.stop();
    }

    @After
    public void after() {
        FileUtil.deleteFile(this.store);
    }
}