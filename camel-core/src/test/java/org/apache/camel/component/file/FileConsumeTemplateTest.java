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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;

/**
 * Using ConsumerTemplate to consume a file
 */
public class FileConsumeTemplateTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/template");
        super.setUp();
        template.sendBodyAndHeader("file://target/template/", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/template/", "Bye World", Exchange.FILE_NAME, "bye.txt");
    }

    public void testConsumeFileWithTemplate() throws Exception {
        Exchange out = consumer.receive("file://target/template?fileName=hello.txt");
        assertNotNull(out);
        assertEquals("Hello World", out.getIn().getBody(String.class));
    }

}