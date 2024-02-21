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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileURLDecodingTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        context.stop();
        super.tearDown();
    }

    @Test
    public void testSimpleFile() throws Exception {
        assertTargetFile("data.txt", "data.txt");
    }

    @Test
    public void testFilePlus() throws Exception {
        assertTargetFile("data .txt", "data .txt");
    }

    @Test
    public void testFileSpace() throws Exception {
        assertTargetFile("data%20.txt", "data .txt");
    }

    @Test
    public void testFile2B() throws Exception {
        assertTargetFile("data .txt", "data .txt");
    }

    @Test
    public void testFileRaw2B() throws Exception {
        assertTargetFile("RAW(data%2B.txt)", "data%2B.txt");
    }

    @Test
    public void testFileRawPlus() throws Exception {
        assertTargetFile("RAW(data+.txt)", "data+.txt");
    }

    @Test
    public void testFileRawSpace() throws Exception {
        assertTargetFile("RAW(data%20.txt)", "data%20.txt");
    }

    @Test
    public void testFileWithTwoHundredPercent() throws Exception {
        assertTargetFile("RAW(data%%.txt)", "data%%.txt");
    }

    private void assertTargetFile(final String encoded, final String expected) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(fileUri("?fileName=" + encoded));
            }
        });

        context.start();

        String result = template.requestBody("direct:start", "Kermit", String.class);
        assertEquals("Kermit", result);

        assertFileExists(testFile(expected), "Kermit");
    }

}
