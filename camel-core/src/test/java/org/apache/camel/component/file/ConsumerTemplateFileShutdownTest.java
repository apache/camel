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
import org.junit.Test;

/**
 *
 */
public class ConsumerTemplateFileShutdownTest extends ContextTestSupport {
    
    @Test
    public void testConsumerTemplateFile() throws Exception {
        deleteDirectory("target/consumertemplate");
        
        template.sendBodyAndHeader("file:target/consumertemplate", "Hello World", Exchange.FILE_NAME, "hello.txt");
        
        Exchange exchange = consumer.receive("file:target/consumertemplate?fileName=hello.txt", 5000);
        assertNotNull(exchange);
        
        assertEquals("Hello World", exchange.getIn().getBody(String.class));

        consumer.doneUoW(exchange);
        consumer.stop();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
