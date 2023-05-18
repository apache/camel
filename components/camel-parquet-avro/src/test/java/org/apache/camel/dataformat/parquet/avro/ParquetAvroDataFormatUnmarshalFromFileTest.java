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
package org.apache.camel.dataformat.parquet.avro;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParquetAvroDataFormatUnmarshalFromFileTest extends CamelTestSupport {

    @Test
    public void testUnmarshalFromFile() throws Exception {
        Collection<Pojo> in = List.of(
                new Pojo(1, "airport"),
                new Pojo(2, "penguin"),
                new Pojo(3, "verb"));
        getMockEndpoint("mock:reverse").expectedMessageCount(1);
        File testFile = new File("src/test/resources/example1.parquet");
        ByteArrayInputStream bais = reteriveByteArrayInputStream(testFile);

        template.sendBody("direct:fromFile", bais);

        List<Exchange> exchanges = getMockEndpoint("mock:reverse").getExchanges();

        assertEquals(1, exchanges.size());
        for (Exchange exchange : exchanges) {
            List out = exchange.getIn().getBody(List.class);
            assertArrayEquals(in.toArray(), out.toArray());
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    private ByteArrayInputStream reteriveByteArrayInputStream(File file) throws IOException {
        return new ByteArrayInputStream(FileUtils.readFileToByteArray(file));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                ParquetAvroDataFormat format = new ParquetAvroDataFormat();
                format.setUnmarshalType(Pojo.class);
                from("direct:fromFile").unmarshal(format).to("mock:reverse");
            }
        };
    }
}
