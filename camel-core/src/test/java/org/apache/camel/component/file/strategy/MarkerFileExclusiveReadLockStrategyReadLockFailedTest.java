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
package org.apache.camel.component.file.strategy;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests the MarkerFileExclusiveReadLockStrategy in a multi-threaded scenario.
 */
public class MarkerFileExclusiveReadLockStrategyReadLockFailedTest extends ContextTestSupport {

    private static final transient Logger LOG = LoggerFactory.getLogger(MarkerFileExclusiveReadLockStrategyReadLockFailedTest.class);
    
    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/readlock/");
        createDirectory("target/readlock/in");
        super.setUp();
    }

    public void testReadLockFailed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/readlock/out/file1.dat");

        writeFiles();

        assertMockEndpointsSatisfied();

        String content = context.getTypeConverter().convertTo(String.class, new File("target/readlock/out/file1.dat").getAbsoluteFile());
        String[] lines = content.split(LS);
        for (int i = 0; i < 20; i++) {
            assertEquals("Line " + i, lines[i]);
        }
        
        // wait for a while for camel to clean up the file
        Thread.sleep(500);

        assertFileDoesNotExists("target/readlock/in/file1.dat.camelLock");
        assertFileExists("target/readlock/in/file2.dat.camelLock");

        assertFileDoesNotExists("target/readlock/in/file1.dat");
        assertFileExists("target/readlock/in/file2.dat");
        
        File lock = new File("target/readlock/in/file2.dat.camelLock");
        lock.delete();
        // wait for a while for camel to clean up the file
        Thread.sleep(500);
        assertFileExists("target/readlock/out/file2.dat");
        
      
    }

    private void writeFiles() throws Exception {
        LOG.debug("Writing files...");
        // create a camelLock file first
        File lock = new File("target/readlock/in/file2.dat.camelLock");
        lock.createNewFile();
        
        FileOutputStream fos = new FileOutputStream("target/readlock/in/file1.dat");
        FileOutputStream fos2 = new FileOutputStream("target/readlock/in/file2.dat");
        for (int i = 0; i < 20; i++) {
            fos.write(("Line " + i + LS).getBytes());
            fos2.write(("Line " + i + LS).getBytes());
            LOG.debug("Writing line " + i);
        }

        fos.flush();
        fos.close();
        fos2.flush();
        fos2.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/readlock/in?readLock=markerFile")
                        .to("file:target/readlock/out", "mock:result");
            }
        };
    }

   
    private static void assertFileDoesNotExists(String filename) {
        File file = new File(filename).getAbsoluteFile();
        assertFalse("File " + filename + " should not exist, it should have been deleted after being processed", file.exists());
    }

}
