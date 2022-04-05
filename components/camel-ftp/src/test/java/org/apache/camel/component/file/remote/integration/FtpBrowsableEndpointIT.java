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
package org.apache.camel.component.file.remote.integration;

import java.nio.file.Path;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.remote.FtpEndpoint;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FtpBrowsableEndpointIT extends FtpServerTestSupport {

    private Path browseDir;

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/browse?password=admin";
    }

    @BeforeEach
    public void createDir() {
        browseDir = service.getFtpRootDir().resolve("browse");
        deleteDirectory(browseDir);
        createDirectory(browseDir);
    }

    @Test
    public void testBrowsableNoFiles() {
        BrowsableEndpoint browse = context.getEndpoint(getFtpUrl(), BrowsableEndpoint.class);
        assertNotNull(browse);

        List<Exchange> list = browse.getExchanges();
        assertNotNull(list);
        assertEquals(0, list.size());
    }

    @Test
    public void testBrowsableOneFile() {
        template.sendBodyAndHeader(getFtpUrl(), "A", Exchange.FILE_NAME, "a.txt");

        FtpEndpoint<?> endpoint = context.getEndpoint(getFtpUrl(), FtpEndpoint.class);
        assertNotNull(endpoint);

        MemoryIdempotentRepository repo = (MemoryIdempotentRepository) endpoint.getInProgressRepository();
        assertEquals(0, repo.getCacheSize());

        List<Exchange> list = endpoint.getExchanges();
        assertNotNull(list);
        assertEquals(1, list.size());

        assertEquals("a.txt", list.get(0).getIn().getHeader(Exchange.FILE_NAME));

        // the in progress repo should not leak
        assertEquals(0, repo.getCacheSize());

        // and the file is still there
        assertFileExists(browseDir.resolve("a.txt"));
    }

    @Test
    public void testBrowsableTwoFiles() {
        template.sendBodyAndHeader(getFtpUrl(), "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(getFtpUrl(), "B", Exchange.FILE_NAME, "b.txt");

        FtpEndpoint<?> endpoint = context.getEndpoint(getFtpUrl() + "&sortBy=file:name", FtpEndpoint.class);
        assertNotNull(endpoint);

        MemoryIdempotentRepository repo = (MemoryIdempotentRepository) endpoint.getInProgressRepository();
        assertEquals(0, repo.getCacheSize());

        List<Exchange> list = endpoint.getExchanges();
        assertNotNull(list);
        assertEquals(2, list.size());

        assertEquals("a.txt", list.get(0).getIn().getHeader(Exchange.FILE_NAME));
        assertEquals("b.txt", list.get(1).getIn().getHeader(Exchange.FILE_NAME));

        // the in progress repo should not leak
        assertEquals(0, repo.getCacheSize());

        // and the files is still there
        assertFileExists(browseDir.resolve("a.txt"));
        assertFileExists(browseDir.resolve("b.txt"));
    }

    @Test
    public void testBrowsableThreeFilesRecursive() {
        template.sendBodyAndHeader(getFtpUrl(), "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(getFtpUrl(), "B", Exchange.FILE_NAME, "foo/b.txt");
        template.sendBodyAndHeader(getFtpUrl(), "C", Exchange.FILE_NAME, "bar/c.txt");

        FtpEndpoint<?> endpoint = context.getEndpoint(getFtpUrl() + "&recursive=true&sortBy=file:name", FtpEndpoint.class);
        assertNotNull(endpoint);

        MemoryIdempotentRepository repo = (MemoryIdempotentRepository) endpoint.getInProgressRepository();
        assertEquals(0, repo.getCacheSize());

        List<Exchange> list = endpoint.getExchanges();
        assertNotNull(list);
        assertEquals(3, list.size());

        assertEquals("a.txt", list.get(0).getIn().getHeader(Exchange.FILE_NAME));
        assertEquals("c.txt", list.get(1).getIn().getHeader(Exchange.FILE_NAME_ONLY));
        assertEquals("b.txt", list.get(2).getIn().getHeader(Exchange.FILE_NAME_ONLY));

        // the in progress repo should not leak
        assertEquals(0, repo.getCacheSize());

        // and the files is still there
        assertFileExists(browseDir.resolve("a.txt"));
        assertFileExists(browseDir.resolve("foo/b.txt"));
        assertFileExists(browseDir.resolve("bar/c.txt"));
    }
}
