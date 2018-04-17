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
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.dataformat.asn1.model.testsmscbercdr.SmsCdr;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringASN1DataFormatRouteTest extends CamelSpringTestSupport {
    
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
    public void testUnmarshalReturnClassObject() throws Exception {
        baseUnmarshalReturnClassObjectTest("mock:unmarshal", "direct:unmarshal");
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/asn1/SpringASN1DataFormatRouteTest.xml");
    }

}
