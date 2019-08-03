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
package org.apache.camel.component.stream;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

public class SplitStreamFileTest extends CamelTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/stream");
        createDirectory("target/stream");

        super.setUp();
    }

    @Test
    public void testCloseFileOnDone() throws Exception {
        template.sendBody("direct:start", "A,B,C,D");
        template.sendBody("direct:start", "A,B");
        
        File file = new File("target/stream/splitFile.txt");
        String result = IOConverter
                .toString(file, new DefaultExchange(context));
        assertEquals("Get a wrong result", "A" + LS + "B" + LS + "C" + LS + "D" + LS + "A" + LS + "B" + LS, result);

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .split(body().tokenize(","))
                        .to("stream:file?fileName=target/stream/splitFile.txt&closeOnDone=false");
            }
        };
    }

}
