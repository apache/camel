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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;

/**
 * @version 
 */
public class FileConsumerTemplateTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/consumer");
        super.setUp();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testFileConsumerTemplate() throws Exception {
        template.sendBodyAndHeader("file:target/consumer", "Hello World", Exchange.FILE_NAME, "hello.txt");
        // file should exist
        File file = new File("target/consumer/hello.txt");

        assertTrue("File should exist " + file, file.exists());
        
        String body = consumer.receiveBody("file:target/consumer?delete=true", 5000, String.class);
        assertEquals("Hello World", body);

        // file should be deleted
        assertFalse("File should be deleted " + file, file.exists());
    }

}
