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
package org.apache.camel.component.hawtdb;

import java.io.File;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtdb.api.SortedIndex;
import org.fusesource.hawtdb.api.Transaction;
import org.junit.Test;

/**
 * @version 
 */
public class HawtDBGrowIssueTest extends CamelTestSupport {

    private HawtDBCamelCodec codec = new HawtDBCamelCodec();
    private HawtDBFile hawtDBFile;
    private final int size = 1024;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/data");
        File file = new File("target/data/hawtdb.dat");
        hawtDBFile = new HawtDBFile();
        hawtDBFile.setFile(file);
        // use 16kb segments
        hawtDBFile.setMappingSegementSize(16 * 1024);
        // set 1mb as max file
        hawtDBFile.setMaxFileSize(1024 * 1024);
        hawtDBFile.start();
    }

    @Override
    public void tearDown() throws Exception {
        hawtDBFile.stop();
        super.tearDown();
    }

    @Test
    public void testGrowIssue() throws Exception {
        // a 1kb string for testing
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < 1024; i++) {
            sb.append("X");
        }

        // the key
        final Buffer key = codec.marshallKey("foo");

        // we update using the same key, which means we should be able to do this within the file size limit
        for (int i = 0; i < size; i++) {
            final Buffer data = codec.marshallKey(i + "-" + sb.toString());

            log.debug("Updating " + i);

            hawtDBFile.execute(new Work<Object>() {
                public Object execute(Transaction tx) {
                    SortedIndex<Buffer, Buffer> index = hawtDBFile.getRepositoryIndex(tx, "repo", true);
                    return index.put(key, data);
                }
            });
        }

        // get the last
        Buffer out = hawtDBFile.execute(new Work<Buffer>() {
            public Buffer execute(Transaction tx) {
                SortedIndex<Buffer, Buffer> index = hawtDBFile.getRepositoryIndex(tx, "repo", true);
                return index.get(key);
            }
        });

        String data = codec.unmarshallKey(out);
        log.info(data);
        assertTrue("Should be 1023", data.startsWith("1023"));
        assertEquals(1029, data.length());
    }

}
