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

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class StreamGroupLinesStrategyTest extends StreamGroupLinesTest {
    
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myGroupStrategy", new MyGroupStrategy());
        return jndi;
    }
    
    class MyGroupStrategy implements GroupStrategy {

        @Override
        public Object groupLines(List<String> lines) {
            StringBuilder buffer = new StringBuilder();
            for (String line : lines) {
                buffer.append(line);
                buffer.append(LS);
            }
            return buffer.toString();
        }
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

        Object result = mock.getExchanges().get(0).getIn().getBody();
        assertEquals("Get a wrong result.", "A" + LS + "B" + LS + "C" + LS, result);

        Object result2 = mock.getExchanges().get(1).getIn().getBody();
        assertEquals("Get a wrong result.", "D" + LS + "E" + LS + "F" + LS, result2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("stream:file?fileName=target/stream/streamfile.txt&groupLines=3&groupStrategy=#myGroupStrategy").to("mock:result");
            }
        };
    }

}
