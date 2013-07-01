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

public class GenericFileMessageTest extends ContextTestSupport {

    public void testGenericMessageToStringConversion() throws Exception {
        GenericFileMessage<File> message = new GenericFileMessage<File>(); 
        assertStringContains(message.toString(), "org.apache.camel.component.file.GenericFileMessage@");
        
        GenericFile<File> file = new GenericFile<File>();
        file.setFileName("target/test.txt");
        file.setFile(new File("target/test.txt"));
        message = new GenericFileMessage<File>(file); 
        assertEquals("target/test.txt", message.toString());
    }
}
