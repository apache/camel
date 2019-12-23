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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.Before;
import org.junit.Test;

public class FileEagerDeleteTargetFileTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/eagerdelete");
        super.setUp();
        template.sendBodyAndHeader("file://target/data/eagerdelete", "Hello World", Exchange.FILE_NAME, "world.txt");
    }

    @Test
    public void testEagerDeleteTargetFileTrue() throws Exception {
        template.sendBodyAndHeader("file://target/data/eagerdelete?tempFileName=inprogress-${file:name}&eagerDeleteTargetFile=true", "Bye World", Exchange.FILE_NAME, "world.txt");

        File file = new File("target/data/eagerdelete/world.txt");
        assertTrue("File should exist", file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testEagerDeleteTargetFileFalse() throws Exception {
        template.sendBodyAndHeader("file://target/data/eagerdelete?tempFileName=inprogress-${file:name}&eagerDeleteTargetFile=false", "Bye World", Exchange.FILE_NAME, "world.txt");

        File file = new File("target/data/eagerdelete/world.txt");
        assertTrue("File should exist", file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testEagerDeleteTargetFileDefault() throws Exception {
        template.sendBodyAndHeader("file://target/data/eagerdelete?tempFileName=inprogress-${file:name}", "Bye World", Exchange.FILE_NAME, "world.txt");

        File file = new File("target/data/eagerdelete/world.txt");
        assertTrue("File should exist", file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

}
