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
package org.apache.camel.jaxb;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.MessageDefinition;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class DumpToXmlTest extends CamelTestSupport {
    @Override
    @Before
    public void setUp() throws Exception {
        // Delete the dump directory
        File file = new File("target/camel/dump");
        file = file.getAbsoluteFile();
        if (file.exists()) {
            deleteDirectory("target/camel/dump");
        }
        super.setUp();
    }

    @Test
    public void testDumplFilesToJaxb() throws Exception {
        // sleep to let the file and jaxb do its works
        Thread.sleep(5000);

        // assert dump file exists
        File file = new File("target/camel/dump");
        file = file.getAbsoluteFile();
        if (!file.exists()) {
            // sleep a while for the slower box
            Thread.sleep(5000);
        }
        assertTrue("The dump folder should exists", file.exists());
        assertEquals("There should be 2 dumped files", 2, file.list().length);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file:src/test/data?noop=true").convertBodyTo(MessageDefinition.class).to("file:target/camel/dump");
            }
        };
    }
}