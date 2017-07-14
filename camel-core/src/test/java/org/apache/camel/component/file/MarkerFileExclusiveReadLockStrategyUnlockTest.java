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
package org.apache.camel.component.file;

import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;

public class MarkerFileExclusiveReadLockStrategyUnlockTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        setupDirectory();
        super.setUp();
    }

    public void testUnlocking() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();
        writeFiles();
        boolean done = notify.matches(5, TimeUnit.SECONDS);

        assertTrue("Route should be done processing 1 exchanges", done);

        assertFileNotExists("target/marker-unlock/input-a/file1.dat.camelLock");
        assertFileNotExists("target/marker-unlock/input-b/file2.dat.camelLock");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/marker-unlock/input-a?fileName=file1.dat&readLock=markerFile&initialDelay=0&delay=10")
                        .pollEnrich("file:target/marker-unlock/input-b?fileName=file2.dat&readLock=markerFile&initialDelay=0&delay=10")
                        .to("mock:result");
            }
        };
    }

    private void setupDirectory() {
        deleteDirectory("target/marker-unlock/");
        createDirectory("target/marker-unlock/input-a");
        createDirectory("target/marker-unlock/input-b");
    }

    private void writeFiles() throws Exception {
        FileOutputStream fos1 = new FileOutputStream("target/marker-unlock/input-a/file1.dat");
        FileOutputStream fos2 = new FileOutputStream("target/marker-unlock/input-b/file2.dat");
        fos1.write("File-1".getBytes());
        fos2.write("File-2".getBytes());
        fos1.flush();
        fos1.close();
        fos2.flush();
        fos2.close();
    }
}
