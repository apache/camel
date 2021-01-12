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

import java.io.File;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FtpBrowsableEndpointTest extends FtpServerTestSupport {

    private File browseDir;

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/browse?password=admin";
    }

    @BeforeEach
    public void createDir() {
        browseDir = new File(service.getFtpRootDir(), "browse");
        createDirectory(service.getFtpRootDir() + File.pathSeparator + "browse");
    }

    @AfterEach
    public void cleanupDir() {
        FileUtil.removeDir(new File(service.getFtpRootDir(), "browse"));
    }

    @Test
    public void testBrowsableNoFiles() throws Exception {
        BrowsableEndpoint browse = context.getEndpoint(getFtpUrl(), BrowsableEndpoint.class);
        assertNotNull(browse);

        List<Exchange> list = browse.getExchanges();
        assertNotNull(list);
        assertEquals(0, list.size());
    }

    @Test
    public void testBrowsableOneFile() throws Exception {
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
        File file = new File(browseDir, "a.txt");
        assertTrue(file.exists(), "File should exist " + file);
    }

    @Test
    public void testBrowsableTwoFiles() throws Exception {
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
        File fileA = new File(browseDir, "a.txt");
        assertTrue(fileA.exists(), "File should exist " + fileA);
        File fileB = new File(browseDir, "/b.txt");
        assertTrue(fileB.exists(), "File should exist " + fileB);
    }

    @Test
    public void testBrowsableThreeFilesRecursive() throws Exception {
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
        File fileA = new File(browseDir, "/a.txt");
        assertTrue(fileA.exists(), "File should exist " + fileA);
        File fileB = new File(browseDir, "/foo/b.txt");
        assertTrue(fileB.exists(), "File should exist " + fileB);
        File fileC = new File(browseDir, "/bar/c.txt");
        assertTrue(fileC.exists(), "File should exist " + fileC);
    }
}
