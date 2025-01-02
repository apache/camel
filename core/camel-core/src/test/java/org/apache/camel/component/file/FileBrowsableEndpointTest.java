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
package org.apache.camel.component.file;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileBrowsableEndpointTest extends ContextTestSupport {

    private static final String TEST_FILE_NAME_PREFIX = UUID.randomUUID().toString();

    @Test
    public void testBrowsableNoFiles() {
        BrowsableEndpoint browse
                = context.getEndpoint(fileUri("?initialDelay=0&delay=10"), BrowsableEndpoint.class);
        assertNotNull(browse);

        List<Exchange> list = browse.getExchanges();
        assertNotNull(list);
        assertEquals(0, list.size());
    }

    @Test
    public void testBrowsableOneFile() {
        template.sendBodyAndHeader(fileUri(), "A", Exchange.FILE_NAME, TEST_FILE_NAME_PREFIX + "a.txt");

        FileEndpoint endpoint = context.getEndpoint(fileUri("?initialDelay=0&delay=10"), FileEndpoint.class);
        assertNotNull(endpoint);

        MemoryIdempotentRepository repo = (MemoryIdempotentRepository) endpoint.getInProgressRepository();
        assertEquals(0, repo.getCacheSize());

        List<Exchange> list = endpoint.getExchanges();
        assertNotNull(list);
        assertEquals(1, list.size());

        assertEquals(TEST_FILE_NAME_PREFIX + "a.txt", list.get(0).getIn().getHeader(Exchange.FILE_NAME));

        // the in progress repo should not leak
        assertEquals(0, repo.getCacheSize());

        // and the file is still there
        assertTrue(Files.exists(testFile(TEST_FILE_NAME_PREFIX + "a.txt")), "File should exist a.txt");
    }

    @Test
    public void testBrowsableTwoFiles() {
        template.sendBodyAndHeader(fileUri(), "A", Exchange.FILE_NAME, TEST_FILE_NAME_PREFIX + "a.txt");
        template.sendBodyAndHeader(fileUri(), "B", Exchange.FILE_NAME, TEST_FILE_NAME_PREFIX + "b.txt");

        FileEndpoint endpoint
                = context.getEndpoint(fileUri("?initialDelay=0&delay=10"), FileEndpoint.class);
        assertNotNull(endpoint);

        MemoryIdempotentRepository repo = (MemoryIdempotentRepository) endpoint.getInProgressRepository();
        assertEquals(0, repo.getCacheSize());

        List<Exchange> list = endpoint.getExchanges();
        assertNotNull(list);
        assertEquals(2, list.size());

        // the in progress repo should not leak
        assertEquals(0, repo.getCacheSize());

        // and the files is still there
        assertTrue(Files.exists(testFile(TEST_FILE_NAME_PREFIX + "a.txt")), "File should exist a.txt");
        assertTrue(Files.exists(testFile(TEST_FILE_NAME_PREFIX + "b.txt")), "File should exist b.txt");
    }

    @Test
    public void testBrowsableThreeFilesRecursive() {
        template.sendBodyAndHeader(fileUri(), "A", Exchange.FILE_NAME, TEST_FILE_NAME_PREFIX + "a.txt");
        template.sendBodyAndHeader(fileUri(), "B", Exchange.FILE_NAME,
                "foo" + File.separator + TEST_FILE_NAME_PREFIX + "b.txt");
        template.sendBodyAndHeader(fileUri(), "C", Exchange.FILE_NAME,
                "bar" + File.separator + TEST_FILE_NAME_PREFIX + "c.txt");

        FileEndpoint endpoint = context.getEndpoint(
                fileUri("?initialDelay=0&delay=10&recursive=true"), FileEndpoint.class);
        assertNotNull(endpoint);

        MemoryIdempotentRepository repo = (MemoryIdempotentRepository) endpoint.getInProgressRepository();
        assertEquals(0, repo.getCacheSize());

        List<Exchange> list = endpoint.getExchanges();
        assertNotNull(list);
        assertEquals(3, list.size());

        // the in progress repo should not leak
        assertEquals(0, repo.getCacheSize());

        // and the files is still there
        assertTrue(Files.exists(testFile(TEST_FILE_NAME_PREFIX + "a.txt")), "File should exist a.txt");
        assertTrue(Files.exists(testFile("foo" + File.separator + TEST_FILE_NAME_PREFIX + "b.txt")),
                "File should exist foo/b.txt");
        assertTrue(Files.exists(testFile("bar" + File.separator + TEST_FILE_NAME_PREFIX + "c.txt")),
                "File should exist bar/c.txt");
    }
}
