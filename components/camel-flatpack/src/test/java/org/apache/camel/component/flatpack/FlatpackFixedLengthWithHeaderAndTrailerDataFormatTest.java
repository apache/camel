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
package org.apache.camel.component.flatpack;

import java.io.File;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.dataformat.flatpack.FlatpackDataFormat;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test for fixed length DataFormat.
 */
public class FlatpackFixedLengthWithHeaderAndTrailerDataFormatTest extends CamelTestSupport {

    @Test
    public void testUnmarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        // by default we get on big message
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(DataSetList.class);
        mock.message(0).header("MyFirstHeader").isEqualTo("1");
        mock.message(0).header("MySecondHeader").isEqualTo("2");

        String data = IOConverter.toString(new File("src/test/data/headerandtrailer/PEOPLE-HeaderAndTrailer.txt"), null);

        template.sendBody("direct:unmarshal", data);
        assertMockEndpointsSatisfied();

        DataSetList list = mock.getExchanges().get(0).getIn().getBody(DataSetList.class);
        assertEquals(6, list.size());

        // assert header
        Map<?, ?> header = list.get(0);
        assertEquals("HBT", header.get("INDICATOR"));
        assertEquals("20080817", header.get("DATE"));

        // assert data
        Map<?, ?> row = list.get(1);
        assertEquals("JOHN", row.get("FIRSTNAME"));

        // assert trailer
        Map<?, ?> trailer = list.get(5);
        assertEquals("FBT", trailer.get("INDICATOR"));
        assertEquals("SUCCESS", trailer.get("STATUS"));
    }

    public void testMarshalWithDefinition() throws Exception {
        // TODO: header and trailer not supported for FlatpackWriter
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                FlatpackDataFormat df = new FlatpackDataFormat();
                df.setDefinition("PEOPLE-HeaderAndTrailer.pzmap.xml");
                df.setFixed(true);

                from("direct:unmarshal")
                    .setHeader("MyFirstHeader", constant("1"))
                    .unmarshal(df)
                    .setHeader("MySecondHeader", constant("2"))
                    .to("mock:unmarshal");

                // with the definition
                from("direct:marshal").marshal(df).convertBodyTo(String.class).to("mock:marshal");
            }
        };
    }
}