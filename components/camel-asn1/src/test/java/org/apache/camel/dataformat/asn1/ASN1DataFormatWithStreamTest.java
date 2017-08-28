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
package org.apache.camel.dataformat.asn1;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

public class ASN1DataFormatWithStreamTest extends CamelTestSupport {

    private ASN1DataFormat asn1;
    private String fileName = "src/test/resources/asn1_data/SMS_SINGLE.tt";
    
    private void baseASN1DataFormatWithStreamTest(String mockEnpointName, String directEndpointName) throws Exception {
        getMockEndpoint(mockEnpointName).expectedMessageCount(1);

        File testFile = new File(fileName);
        ByteArrayInputStream bais = ASN1DataFormatTestHelper.reteriveByteArrayInputStream(testFile);

        template.sendBody(directEndpointName, bais);

        List<Exchange> exchanges = getMockEndpoint(mockEnpointName).getExchanges();

        assertTrue(exchanges.size() == 1);
        for (Exchange exchange : exchanges) {
            assertTrue(exchange.getIn().getBody() instanceof byte[]);
            assertTrue(Arrays.equals(FileUtils.readFileToByteArray(testFile), exchange.getIn().getBody(byte[].class)));
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUnmarshalReturnOutputStream() throws Exception {
        baseASN1DataFormatWithStreamTest("mock:unmarshal", "direct:unmarshal");
    }
    
    @Test
    public void testUnmarshalReturnOutputStreamDsl() throws Exception {
        baseASN1DataFormatWithStreamTest("mock:unmarshaldsl", "direct:unmarshaldsl");
    }

    @Test
    public void testUnmarshalMarshalReturnOutputStream() throws Exception {
        baseASN1DataFormatWithStreamTest("mock:marshal", "direct:unmarshalthenmarshal");
    }
    
    @Test
    public void testUnmarshalMarshalReturnOutputStreamDsl() throws Exception {
        baseASN1DataFormatWithStreamTest("mock:marshaldsl", "direct:unmarshalthenmarshaldsl");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                asn1 = new ASN1DataFormat();

                from("direct:unmarshal").unmarshal(asn1).to("mock:unmarshal");
                from("direct:unmarshalthenmarshal").unmarshal(asn1).marshal(asn1).to("mock:marshal");
                
                from("direct:unmarshaldsl").unmarshal().asn1().to("mock:unmarshaldsl");
                from("direct:unmarshalthenmarshaldsl").unmarshal().asn1().marshal().asn1().to("mock:marshaldsl");
            }
        };
    }

}
