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
package org.apache.camel.component.stream;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class StreamGroupLinesTest extends CamelTestSupport {

    private FileOutputStream fos;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/stream");
        createDirectory("target/stream");

        File file = new File("target/stream/streamfile.txt");
        file.createNewFile();

        fos = new FileOutputStream(file);
        fos.write("A\n".getBytes());
        fos.write("B\n".getBytes());
        fos.write("C\n".getBytes());
        fos.write("D\n".getBytes());
        fos.write("E\n".getBytes());
        fos.write("F\n".getBytes());

        fos.close();

        super.setUp();
    }

    @Test
    public void testGroupLines() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.setAssertPeriod(1000);
        mock.message(0).header(StreamConstants.STREAM_INDEX).isEqualTo(0);
        mock.message(0).header(StreamConstants.STREAM_COMPLETE).isEqualTo(false);
        mock.message(1).header(StreamConstants.STREAM_INDEX).isEqualTo(1);
        mock.message(1).header(StreamConstants.STREAM_COMPLETE).isEqualTo(true);

        assertMockEndpointsSatisfied();

        List<?> list = mock.getExchanges().get(0).getIn().getBody(List.class);
        assertEquals(3, list.size());
        assertEquals("A", list.get(0));
        assertEquals("B", list.get(1));
        assertEquals("C", list.get(2));

        List<?> list2 = mock.getExchanges().get(1).getIn().getBody(List.class);
        assertEquals(3, list2.size());
        assertEquals("D", list2.get(0));
        assertEquals("E", list2.get(1));
        assertEquals("F", list2.get(2));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("stream:file?fileName=target/stream/streamfile.txt&groupLines=3").to("mock:result");
            }
        };
    }
}
