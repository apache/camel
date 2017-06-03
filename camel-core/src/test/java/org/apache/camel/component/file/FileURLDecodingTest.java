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

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Paths;


import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;


public class FileURLDecodingTest extends ContextTestSupport {
    
    static final String TARGET_DIR = "target/files";
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory(TARGET_DIR);
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        context.stop();
        super.tearDown();
    }
    
    public void testSimpleFile() throws Exception {
        assertTargetFile("data.txt", "data.txt");
    }
    
    public void testFilePlus() throws Exception {
        assertTargetFile("data+.txt", "data .txt");
    }
    
    public void testFileSpace() throws Exception {
        assertTargetFile("data%20.txt", "data .txt");
    }
    
    public void testFile2B() throws Exception {
        assertTargetFile("data%2B.txt", "data .txt");
    }
    public void testFileRaw2B() throws Exception {
        assertTargetFile("RAW(data%2B.txt)", "data%2B.txt");
    }

    public void testFileRawPlus() throws Exception {
        assertTargetFile("RAW(data+.txt)", "data+.txt");
    }
   
    public void testFileRawSpace() throws Exception {
        assertTargetFile("RAW(data%20.txt)", "data%20.txt");
    }

    public void testFileRaw2520() throws Exception {
        assertTargetFile("RAW(data%2520.txt)", "data%2520.txt");
    }
    
    public void testFileWithTwoHundredPercent() throws Exception {
        assertTargetFile("RAW(data%%.txt)", "data%%.txt");
    }
   
   
    private void assertTargetFile(final String encoded, final String expected) throws Exception {
        
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("file:" + TARGET_DIR + "?fileName=" + encoded);
            }
        });
        
        context.start();
            
        String result = template.requestBody("direct:start", "Kermit", String.class);
        assertEquals("Kermit", result);
        
        BufferedReader br = new BufferedReader(new FileReader(Paths.get(TARGET_DIR, expected).toFile()));
        assertEquals("Kermit", br.readLine());
        br.close();
    }


}
