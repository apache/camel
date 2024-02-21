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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test about retrying deleting processed file, that can be a bit more tricky on some OS as java.io.delete can
 * return wrong answer
 */
public class GenericFileDeleteProcessStrategyTest extends ContextTestSupport {

    private static int existsCounter;
    private static int deleteCounter;

    private class MyGenericFileOperations implements GenericFileOperations<Object> {

        @Override
        public GenericFile<Object> newGenericFile() {
            return null;
        }

        @Override
        public void setEndpoint(GenericFileEndpoint<Object> endpoint) {
        }

        @Override
        public boolean deleteFile(String name) throws GenericFileOperationFailedException {
            deleteCounter++;
            return false;
        }

        @Override
        public boolean existsFile(String name) throws GenericFileOperationFailedException {
            existsCounter++;
            // The file name should be normalized
            if (FileUtil.normalizePath(testFile("boom.txt").toString()).equals(name)) {
                // test that we can newer delete this file
                return true;
            }

            return existsCounter <= 1;
        }

        @Override
        public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
            return false;
        }

        @Override
        public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
            return false;
        }

        @Override
        public boolean retrieveFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
            return false;
        }

        @Override
        public void releaseRetrievedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
            // No-op
        }

        @Override
        public boolean storeFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
            return false;
        }

        @Override
        public String getCurrentDirectory() throws GenericFileOperationFailedException {
            return null;
        }

        @Override
        public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        }

        @Override
        public void changeToParentDirectory() throws GenericFileOperationFailedException {
        }

        @Override
        public Object[] listFiles() throws GenericFileOperationFailedException {
            return null;
        }

        @Override
        public Object[] listFiles(String path) throws GenericFileOperationFailedException {
            return null;
        }
    }

    @Test
    public void testTroubleDeletingFile() throws Exception {
        deleteCounter = 0;
        existsCounter = 0;

        @SuppressWarnings("unchecked")
        GenericFileEndpoint<Object> endpoint = context.getEndpoint(fileUri(), GenericFileEndpoint.class);
        Exchange exchange = endpoint.createExchange();

        GenericFile<Object> file = new GenericFile<>();
        file.setAbsoluteFilePath(testFile("me.txt").toString());

        GenericFileDeleteProcessStrategy<Object> strategy = new GenericFileDeleteProcessStrategy<>();
        strategy.commit(new MyGenericFileOperations(), endpoint, exchange, file);

        assertEquals(2, deleteCounter, "Should have tried to delete file 2 times");
        assertEquals(2, existsCounter, "Should have tried to delete file 2 times");
    }

    @Test
    public void testCannotDeleteFile() throws Exception {
        deleteCounter = 0;
        existsCounter = 0;

        @SuppressWarnings("unchecked")
        GenericFileEndpoint<Object> endpoint = context.getEndpoint(fileUri(), GenericFileEndpoint.class);
        Exchange exchange = endpoint.createExchange();

        GenericFile<Object> file = new GenericFile<>();
        file.setAbsoluteFilePath(testFile("boom.txt").toString());

        GenericFileDeleteProcessStrategy<Object> strategy = new GenericFileDeleteProcessStrategy<>();

        Assertions.assertThrows(GenericFileOperationFailedException.class,
                () -> strategy.commit(new MyGenericFileOperations(), endpoint, exchange, file),
                "Should have thrown an exception");

        assertEquals(3, deleteCounter, "Should have tried to delete file 3 times");
        assertEquals(3, existsCounter, "Should have tried to delete file 3 times");
    }
}
