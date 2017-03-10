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
package org.apache.camel.dataformat.bindy.csv;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.util.Assert;

import static org.junit.Assert.fail;

@ContextConfiguration
public class BindySimpleCsvMandatoryFieldsUnmarshallTest extends AbstractJUnit4SpringContextTests {
    
    @EndpointInject(uri = "mock:result1")
    protected MockEndpoint resultEndpoint1;

    @EndpointInject(uri = "mock:result2")
    protected MockEndpoint resultEndpoint2;
    
    @EndpointInject(uri = "mock:result3")
    protected MockEndpoint resultEndpoint3;

    @Produce(uri = "direct:start1")
    protected ProducerTemplate template1;

    @Produce(uri = "direct:start2")
    protected ProducerTemplate template2;
    
    @Produce(uri = "direct:start3")
    protected ProducerTemplate template3;

    String header = "order nr,client ref,first name, last name,instrument code,instrument name,order type, instrument type, quantity,currency,date\r\n";

    // String record5 = ",,,,,,,,,,"; // record with no data

    @DirtiesContext
    @Test
    public void testEmptyRecord() throws Exception {

        String record1 = ""; // empty records

        resultEndpoint1.expectedMessageCount(0);

        try {
            template1.sendBody(record1);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            Assert.isInstanceOf(Exception.class, e.getCause());
            // LOG.info(">> Error : " + e);
        }

        resultEndpoint1.assertIsSatisfied();
    }

    @DirtiesContext
    @Test
    public void testEmptyFields() throws Exception {

        String record2 = ",,blabla,,,,,,,,"; // optional fields

        resultEndpoint1.expectedMessageCount(1);
        template1.sendBody(record2);

        resultEndpoint1.assertIsSatisfied();
    }

    @DirtiesContext
    @Test
    public void testOneOptionalField() throws Exception {

        String record2 = ",,blabla,,,,,,,,"; // optional fields

        resultEndpoint1.expectedMessageCount(1);

        template1.sendBody(record2);
        resultEndpoint1.assertIsSatisfied();
    }

    @DirtiesContext
    @Test
    public void testSeveralOptionalFields() throws Exception {

        String record3 = "1,A1,Charles,Moulliard,ISIN,LU123456789,,,,,"; // mandatory
        // fields
        // present
        // (A1,
        // Charles,
        // Moulliard)

        resultEndpoint1.expectedMessageCount(1);

        template1.sendBody(record3);
        resultEndpoint1.assertIsSatisfied();
    }

    @DirtiesContext
    @Test
    public void testTooMuchFields() throws Exception {

        String record6 = ",,,,,,,,,,,,,,"; // too much data in the record (only
        // 11 are accepted by the model

        resultEndpoint1.expectedMessageCount(0);

        try {
            template1.sendBody(record6);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            // expected
            Assert.isInstanceOf(IllegalArgumentException.class, e.getCause());
        }

        resultEndpoint1.assertIsSatisfied();
    }
    
    @DirtiesContext
    @Test
    public void testEmptyLineWithAllowEmptyStreamEqualsTrue() throws Exception {
        String record6 = ""; // empty line
        resultEndpoint3.expectedMessageCount(1);
        template3.sendBody(record6);
        resultEndpoint3.assertIsSatisfied();
    }
    
    @DirtiesContext
    @Test
    public void testNonEmptyLineWithAllowEmptyStreamEqualsTrue() throws Exception {
        String record3 = "1,A1,Onder,Sezgin,MYC,BB123456789,,,,,"; // mandatory
        resultEndpoint3.expectedMessageCount(1);
        template3.sendBody(record3);
        resultEndpoint3.assertIsSatisfied();
    }

    @DirtiesContext
    @Test
    public void testMandatoryFields() throws Exception {

        String record3 = "1,A1,Charles,Moulliard,ISIN,LU123456789,,,,,"; // mandatory
        // fields
        // present
        // (A1,
        // Charles,
        // Moulliard)

        resultEndpoint2.expectedMessageCount(1);

        template2.sendBody(header + record3);
        resultEndpoint2.assertIsSatisfied();
    }

    @DirtiesContext
    @Test
    public void testMissingMandatoryFields() throws Exception {

        String record4 = "1,A1,Charles,,ISIN,LU123456789,,,,,"; // mandatory
        // field missing

        resultEndpoint2.expectedMessageCount(1);

        try {
            template2.sendBody(header + record4);
            resultEndpoint2.assertIsSatisfied();
        } catch (CamelExecutionException e) {
            // LOG.info(">> Error : " + e);
        }
    }

    public static class ContextConfig extends RouteBuilder {
        BindyCsvDataFormat formatOptional = new BindyCsvDataFormat(org.apache.camel.dataformat.bindy.model.simple.oneclass.Order.class);
        BindyCsvDataFormat formatMandatory = new BindyCsvDataFormat(org.apache.camel.dataformat.bindy.model.simple.oneclassmandatory.Order.class);
        BindyCsvDataFormat formatEmptyStream = new BindyCsvDataFormat(org.apache.camel.dataformat.bindy.model.simple.oneclassemptystream.Order.class);

        public void configure() {
            from("direct:start1").unmarshal(formatOptional).to("mock:result1");
            from("direct:start2").unmarshal(formatMandatory).to("mock:result2");
            from("direct:start3").unmarshal(formatEmptyStream).to("mock:result3");
        }
         
    }
}
