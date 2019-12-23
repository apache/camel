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
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class FileBrowsableEndpointTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/browse");
        super.setUp();
    }

    @Test
    public void testBrowsableNoFiles() throws Exception {
        BrowsableEndpoint browse = context.getEndpoint("file:target/data/browse?initialDelay=0&delay=10", BrowsableEndpoint.class);
        assertNotNull(browse);

        List<Exchange> list = browse.getExchanges();
        assertNotNull(list);
        assertEquals(0, list.size());
    }

    @Test
    public void testBrowsableOneFile() throws Exception {
        template.sendBodyAndHeader("file:target/data/browse", "A", Exchange.FILE_NAME, "a.txt");

        FileEndpoint endpoint = context.getEndpoint("file:target/data/browse?initialDelay=0&delay=10", FileEndpoint.class);
        assertNotNull(endpoint);

        MemoryIdempotentRepository repo = (MemoryIdempotentRepository)endpoint.getInProgressRepository();
        assertEquals(0, repo.getCacheSize());

        List<Exchange> list = endpoint.getExchanges();
        assertNotNull(list);
        assertEquals(1, list.size());

        assertEquals("a.txt", list.get(0).getIn().getHeader(Exchange.FILE_NAME));

        // the in progress repo should not leak
        assertEquals(0, repo.getCacheSize());

        // and the file is still there
        File file = new File("target/data/browse/a.txt");
        assertTrue("File should exist " + file, file.exists());
    }

    @Test
    public void testBrowsableTwoFiles() throws Exception {
        template.sendBodyAndHeader("file:target/data/browse", "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file:target/data/browse", "B", Exchange.FILE_NAME, "b.txt");

        FileEndpoint endpoint = context.getEndpoint("file:target/data/browse?initialDelay=0&delay=10&sortBy=file:name", FileEndpoint.class);
        assertNotNull(endpoint);

        MemoryIdempotentRepository repo = (MemoryIdempotentRepository)endpoint.getInProgressRepository();
        assertEquals(0, repo.getCacheSize());

        List<Exchange> list = endpoint.getExchanges();
        assertNotNull(list);
        assertEquals(2, list.size());

        assertEquals("a.txt", list.get(0).getIn().getHeader(Exchange.FILE_NAME));
        assertEquals("b.txt", list.get(1).getIn().getHeader(Exchange.FILE_NAME));

        // the in progress repo should not leak
        assertEquals(0, repo.getCacheSize());

        // and the files is still there
        File fileA = new File("target/data/browse/a.txt");
        assertTrue("File should exist " + fileA, fileA.exists());
        File fileB = new File("target/data/browse/b.txt");
        assertTrue("File should exist " + fileB, fileB.exists());
    }

    @Test
    public void testBrowsableThreeFilesRecursive() throws Exception {
        template.sendBodyAndHeader("file:target/data/browse", "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file:target/data/browse", "B", Exchange.FILE_NAME, "foo/b.txt");
        template.sendBodyAndHeader("file:target/data/browse", "C", Exchange.FILE_NAME, "bar/c.txt");

        FileEndpoint endpoint = context.getEndpoint("file:target/data/browse?initialDelay=0&delay=10&recursive=true&sortBy=file:name", FileEndpoint.class);
        assertNotNull(endpoint);

        MemoryIdempotentRepository repo = (MemoryIdempotentRepository)endpoint.getInProgressRepository();
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
        File fileA = new File("target/data/browse/a.txt");
        assertTrue("File should exist " + fileA, fileA.exists());
        File fileB = new File("target/data/browse/foo/b.txt");
        assertTrue("File should exist " + fileB, fileB.exists());
        File fileC = new File("target/data/browse/bar/c.txt");
        assertTrue("File should exist " + fileC, fileC.exists());
    }
}
