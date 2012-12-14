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
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test that FileProducer can use message id as the filename.
 */
public class FileProduceGeneratedFileNameTest extends ContextTestSupport {

    public void testGeneratedFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:a");
        FileEndpoint fileEndpoint = resolveMandatoryEndpoint("file://target", FileEndpoint.class);

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");

        String id = fileEndpoint.getGeneratedFileName(exchange.getIn());
        template.send(endpoint, exchange);

        File file = new File("target/" + id);
        assertEquals("The generated file should exists: " + file, true, file.exists());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a").to("file://target");
            }
        };
    }
}
