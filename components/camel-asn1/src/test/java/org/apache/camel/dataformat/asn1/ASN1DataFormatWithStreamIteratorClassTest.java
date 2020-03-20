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
package org.apache.camel.dataformat.asn1;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.asn1.model.testsmscbercdr.SmsCdr;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ASN1DataFormatWithStreamIteratorClassTest extends CamelTestSupport {

    private ASN1DataFormat asn1;
    private String fileName = "src/test/resources/asn1_data/SMS_SINGLE.tt";

    private void baseUnmarshalReturnClassObjectTest(String mockEnpointName, String directEndpointName) throws Exception {
        getMockEndpoint(mockEnpointName).expectedMessageCount(1);

        File testFile = new File(fileName);
        ByteArrayInputStream bais = ASN1DataFormatTestHelper.reteriveByteArrayInputStream(testFile);

        template.sendBody(directEndpointName, bais);

        List<Exchange> exchanges = getMockEndpoint(mockEnpointName).getExchanges();

        assertTrue(exchanges.size() == 1);
        for (Exchange exchange : exchanges) {
            assertTrue(exchange.getIn().getBody() instanceof SmsCdr);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    void testUnmarshalReturnClassObject() throws Exception {
        baseUnmarshalReturnClassObjectTest("mock:unmarshal", "direct:unmarshal");
    }

    @Test
    void testUnmarshalReturnClassObjectDsl() throws Exception {
        baseUnmarshalReturnClassObjectTest("mock:unmarshaldsl", "direct:unmarshaldsl");
    }

    private void baseUnmarshalMarshalReturnOutputStreamTest(String mockEnpointName, String directEndpointName) throws Exception {
        getMockEndpoint(mockEnpointName).expectedMessageCount(1);

        File testFile = new File(fileName);
        ByteArrayInputStream bais = ASN1DataFormatTestHelper.reteriveByteArrayInputStream(testFile);

        template.sendBody(directEndpointName, bais);

        List<Exchange> exchanges = getMockEndpoint(mockEnpointName).getExchanges();

        assertTrue(exchanges.size() == 1);
        for (Exchange exchange : exchanges) {
            assertTrue(exchange.getIn().getBody() instanceof byte[]);
            // assertTrue(Arrays.equals(FileUtils.readFileToByteArray(testFile),
            // exchange.getIn().getBody(byte[].class)));

            // FileOutputStream fos = new
            // FileOutputStream("src/test/resources/after_unmarshal_marshal_SMS_SINGLE.tt");
            // fos.write(ObjectHelper.cast(byte[].class,
            // exchange.getIn().getBody()));
            // fos.close();
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    void testUnmarshalMarshalReturnOutputStream() throws Exception {
        baseUnmarshalMarshalReturnOutputStreamTest("mock:marshal", "direct:unmarshalthenmarshal");
    }

    @Test
    void testUnmarshalMarshalReturnOutputStreamDsl() throws Exception {
        baseUnmarshalMarshalReturnOutputStreamTest("mock:marshaldsl", "direct:unmarshalthenmarshaldsl");
    }

    @Test
    @Disabled
    void testUnmarshalReturnClassObjectAfterUnmarshalMarshalReturnOutputStream() throws Exception {
        getMockEndpoint("mock:unmarshal").expectedMessageCount(1);

        File testFile = new File("src/test/resources/after_unmarshal_marshal_SMS_SINGLE.tt");
        ByteArrayInputStream bais = ASN1DataFormatTestHelper.reteriveByteArrayInputStream(testFile);

        template.sendBody("direct:unmarshal", bais);

        List<Exchange> exchanges = getMockEndpoint("mock:unmarshal").getExchanges();

        assertTrue(exchanges.size() == 1);
        for (Exchange exchange : exchanges) {
            assertTrue(exchange.getIn().getBody() instanceof SmsCdr);
        }

        assertMockEndpointsSatisfied();
    }

    private void baseDoubleUnmarshalTest(String firstMockEnpointName, String secondMockEnpointName, String directEndpointName) throws Exception {
        getMockEndpoint(firstMockEnpointName).expectedMessageCount(1);
        getMockEndpoint(secondMockEnpointName).expectedMessageCount(1);

        File testFile = new File(fileName);
        ByteArrayInputStream bais = ASN1DataFormatTestHelper.reteriveByteArrayInputStream(testFile);

        template.sendBody(directEndpointName, bais);

        List<Exchange> exchangesFirst = getMockEndpoint(firstMockEnpointName).getExchanges();

        assertTrue(exchangesFirst.size() == 1);
        SmsCdr firstUnmarshalledCdr = null;
        for (Exchange exchange : exchangesFirst) {
            assertTrue(exchange.getIn().getBody() instanceof SmsCdr);
            firstUnmarshalledCdr = exchange.getIn().getBody(SmsCdr.class);
        }

        Thread.sleep(100);

        List<Exchange> exchangesSecond = getMockEndpoint(secondMockEnpointName).getExchanges();

        assertTrue(exchangesSecond.size() == 1);
        SmsCdr secondUnmarshalledCdr = null;
        for (Exchange exchange : exchangesSecond) {
            assertTrue(exchange.getIn().getBody() instanceof SmsCdr);
            secondUnmarshalledCdr = exchange.getIn().getBody(SmsCdr.class);
        }

        assertTrue(firstUnmarshalledCdr.toString().equals(secondUnmarshalledCdr.toString()));

        assertMockEndpointsSatisfied();
    }

    @Test
    void testDoubleUnmarshal() throws Exception {
        baseDoubleUnmarshalTest("mock:firstunmarshal", "mock:secondunmarshal", "direct:doubleunmarshal");
    }

    @Test
    void testDoubleUnmarshalDsl() throws Exception {
        baseDoubleUnmarshalTest("mock:firstunmarshaldsldsl", "mock:secondunmarshaldsl", "direct:doubleunmarshaldsl");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                asn1 = new ASN1DataFormat(SmsCdr.class);

                from("direct:unmarshal").unmarshal(asn1).split(bodyAs(Iterator.class)).streaming().to("mock:unmarshal");
                from("direct:unmarshalthenmarshal").unmarshal(asn1).split(bodyAs(Iterator.class)).streaming().marshal(asn1).to("mock:marshal");
                from("direct:doubleunmarshal").unmarshal(asn1).split(bodyAs(Iterator.class)).streaming().wireTap("direct:secondunmarshal").to("mock:firstunmarshal");
                from("direct:secondunmarshal").marshal(asn1).unmarshal(asn1).split(bodyAs(Iterator.class)).streaming().to("mock:secondunmarshal");

                from("direct:unmarshaldsl").unmarshal().asn1("org.apache.camel.dataformat.asn1.model.testsmscbercdr.SmsCdr").split(bodyAs(Iterator.class)).streaming()
                    .to("mock:unmarshaldsl");
                from("direct:unmarshalthenmarshaldsl").unmarshal().asn1("org.apache.camel.dataformat.asn1.model.testsmscbercdr.SmsCdr").split(bodyAs(Iterator.class)).streaming()
                    .marshal().asn1("org.apache.camel.dataformat.asn1.model.testsmscbercdr.SmsCdr").to("mock:marshaldsl");

                from("direct:doubleunmarshaldsl").unmarshal().asn1("org.apache.camel.dataformat.asn1.model.testsmscbercdr.SmsCdr").split(bodyAs(Iterator.class)).streaming()
                    .wireTap("direct:secondunmarshaldsl").to("mock:firstunmarshaldsldsl");
                from("direct:secondunmarshaldsl").marshal().asn1("org.apache.camel.dataformat.asn1.model.testsmscbercdr.SmsCdr").unmarshal()
                    .asn1("org.apache.camel.dataformat.asn1.model.testsmscbercdr.SmsCdr").split(bodyAs(Iterator.class)).streaming().to("mock:secondunmarshaldsl");
            }
        };
    }

}
