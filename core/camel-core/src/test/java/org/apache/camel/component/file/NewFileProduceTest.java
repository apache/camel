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

import java.util.HashMap;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.junit.jupiter.api.Test;

/**
 * Simple unit test to produce a new file
 */
public class NewFileProduceTest extends ContextTestSupport {

    @Test
    public void testNewFileProducer() throws Exception {
        FileComponent comp = new FileComponent();
        comp.setCamelContext(context);

        Endpoint endpoint = comp.createEndpoint(fileUri(), testDirectory().toString(),
                new HashMap<String, Object>());
        template.send(endpoint, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.FILE_NAME, "bye.txt");
                exchange.getIn().setBody("Bye World");
            }
        });

        assertFileExists(testFile("bye.txt"));
    }

}
