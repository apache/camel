/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.apache.camel.Processor;
import org.apache.camel.ExchangePattern;
import org.apache.camel.processor.Pipeline;

import java.io.File;
import java.util.Collections;

/**
 * @version $Revision: 1.1 $
 */
public class FileExchangeTest extends ContextTestSupport {
    protected File file;
    protected ExchangePattern pattern = ExchangePattern.InOnly;

    public void testCopy() {
        FileExchange fileExchange = new FileExchange(context, pattern, file);
        Exchange exchange = fileExchange.copy();
        FileExchange copy = assertIsInstanceOf(FileExchange.class, exchange);
        assertEquals("File", file, copy.getFile());
        Object body = copy.getIn().getBody();
        assertNotNull("Should have a body!", body);
    }

    public void testCopyAfterBodyChanged() throws Exception {
        FileExchange original = new FileExchange(context, pattern, file);
        Object expectedBody = 1234;
        original.getIn().setBody(expectedBody);
        Exchange exchange = original.copy();
        FileExchange copy = assertIsInstanceOf(FileExchange.class, exchange);
        assertEquals("File", file, copy.getFile());
        Object body = copy.getIn().getBody();
        assertNotNull("Should have a body!", body);
        assertEquals("Copied exchange in body", expectedBody, body);
    }

    public void testPipelineCopy() throws Exception {
        Processor myProcessor = new Processor() {
            public void process(Exchange exchange) throws Exception {
                Object body = exchange.getIn().getBody();
                assertNotNull("Should have a body!", body);
            }
        };

        Pipeline pipeline = new Pipeline(Collections.singletonList(myProcessor));
        FileExchange exchange = new FileExchange(context, pattern, file);
        pipeline.process(exchange.copy());            
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        file = new File(FileExchangeTest.class.getResource("FileExchangeTest.class").getFile());
    }
}