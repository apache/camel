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
package org.apache.camel.dataformat.beanio;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.beanio.InvalidRecordException;
import org.beanio.UnexpectedRecordException;
import org.beanio.UnidentifiedRecordException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class BeanIODataFormatComplexTest extends CamelTestSupport {

    private static Locale defaultLocale;

    private final String recordData = "0001917A112345.678900           " + LS
            + "0002374A159303290.020           " + LS
            + "0015219B1SECURITY ONE           " + LS
            + "END OF SECTION 1                " + LS
            + "0076647A10.0000000001           " + LS
            + "0135515A1999999999999           " + LS
            + "2000815B1SECURITY TWO           " + LS
            + "2207122B1SECURITY THR           " + LS
            + "END OF FILE 000007              " + LS;

    private final String data = "0000000A1030808PRICE            " + LS
            + "0000000B1030808SECURITY         " + LS
            + "HEADER END                      " + LS
            + recordData;

    private final String unExpectedData = "0000000A1030808PRICE            " + LS
            + "0000000B1030808SECURITY         " + LS
            + "0000000B1030808SECURITY         " + LS
            + "HEADER END                      " + LS
            + recordData;

    private final String invalidData = "0000000A1030808PRICE            " + LS
            + "0000000B1030808SECURITY         EXTRA DATA" + LS
            + "0000000B1030808SECURITY         " + LS
            + "HEADER END                      " + LS
            + recordData;

    private final String unidentifiedData = "0000000A1030808PRICE            " + LS
            + "0000000C1030808SECURITY         " + LS
            + "0000000B1030808SECURITY         " + LS
            + "HEADER END                      " + LS
            + recordData;

    @BeforeClass
    public static void setLocale() {
        if (!Locale.getDefault().equals(Locale.ENGLISH)) {

            // the Locale used for the number formatting of the above data is
            // english which could be other than the default locale
            defaultLocale = Locale.getDefault();
            Locale.setDefault(Locale.ENGLISH);
        }
    }

    @AfterClass
    public static void resetLocale() {
        if (defaultLocale != null) {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testMarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:beanio-marshal");
        mock.expectedBodiesReceived(data);

        template.sendBody("direct:marshal", createTestData(false));

        mock.assertIsSatisfied();
    }

    @Test
    public void testUnmarshal() throws Exception {
        context.setTracing(true);
        MockEndpoint mock = getMockEndpoint("mock:beanio-unmarshal");
        mock.expectedBodiesReceived(createTestData(false));

        template.sendBody("direct:unmarshal", data);

        mock.assertIsSatisfied();
    }

    @Test
    public void testUnmarshalUnexpected() throws Exception {
        Throwable ex = null;

        try {
            template.sendBody("direct:unmarshal", unExpectedData);
        } catch (Exception e) {
            ex = e.getCause();
        }

        assertIsInstanceOf(UnexpectedRecordException.class, ex);
    }

    @Test
    public void testUnmarshalInvalid() throws Exception {
        Throwable ex = null;

        try {
            template.sendBody("direct:unmarshal", invalidData);
        } catch (Exception e) {
            ex = e.getCause();
        }

        assertIsInstanceOf(InvalidRecordException.class, ex);
    }

    @Test
    public void testUnmarshalUnidentifiedIgnore() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:beanio-unmarshal");
        mock.expectedBodiesReceived(createTestData(false));
        template.sendBody("direct:unmarshal-forgiving", unidentifiedData);
        mock.assertIsSatisfied();
    }

    @Test
    public void testUnmarshalUnexpectedIgnore() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:beanio-unmarshal");
        mock.expectedBodiesReceived(createTestData(false));
        template.sendBody("direct:unmarshal-forgiving", unExpectedData);
        mock.assertIsSatisfied();
    }

    @Test
    public void testUnmarshalInvalidIgnore() throws Exception {
        context.setTracing(true);
        MockEndpoint mock = getMockEndpoint("mock:beanio-unmarshal");
        mock.expectedBodiesReceived(createTestData(true));
        template.sendBody("direct:unmarshal-forgiving", invalidData);
        mock.assertIsSatisfied();
    }

    @Test
    public void testUnmarshalUnidentified() throws Exception {
        Throwable ex = null;

        try {
            template.sendBody("direct:unmarshal", unidentifiedData);
        } catch (Exception e) {
            ex = e.getCause();
        }

        assertIsInstanceOf(UnidentifiedRecordException.class, ex);
    }

    private List<Object> createTestData(boolean skipB1header) throws ParseException {
        String source = "camel-beanio";
        List<Object> body = new ArrayList<Object>();

        Date date = new SimpleDateFormat("ddMMyy").parse("030808");
        Header hFirst = new Header("A1", date, "PRICE");
        Header hSecond = new Header("B1", date, "SECURITY");
        Separator headerEnd = new Separator("HEADER END");

        A1Record first = new A1Record("0001917", source, 12345.678900);
        A1Record second = new A1Record("0002374", source, 59303290.020);
        B1Record third = new B1Record("0015219", source, "SECURITY ONE");
        Separator sectionEnd = new Separator("END OF SECTION 1");
        A1Record fourth = new A1Record("0076647", source, 0.0000000001);
        A1Record fifth = new A1Record("0135515", source, 999999999999d);
        B1Record sixth = new B1Record("2000815", source, "SECURITY TWO");
        B1Record seventh = new B1Record("2207122", source, "SECURITY THR");

        body.add(hFirst);
        if (!skipB1header) {
            body.add(hSecond);
        }
        body.add(headerEnd);
        body.add(first);
        body.add(second);
        body.add(third);
        body.add(sectionEnd);
        body.add(fourth);
        body.add(fifth);
        body.add(sixth);
        body.add(seventh);

        Trailer trailer = new Trailer(7);
        body.add(trailer);

        return body;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BeanIODataFormat format = new BeanIODataFormat("org/apache/camel/dataformat/beanio/mappings.xml", "securityData");

                BeanIODataFormat forgivingFormat = new BeanIODataFormat("org/apache/camel/dataformat/beanio/mappings.xml", "securityData");
                forgivingFormat.setIgnoreInvalidRecords(true);
                forgivingFormat.setIgnoreUnexpectedRecords(true);
                forgivingFormat.setIgnoreUnidentifiedRecords(true);

                from("direct:unmarshal").unmarshal(format).split(simple("body")).to("mock:beanio-unmarshal");

                from("direct:unmarshal-forgiving").unmarshal(forgivingFormat).split(simple("body")).to("mock:beanio-unmarshal");

                from("direct:marshal").marshal(format).to("mock:beanio-marshal");
            }
        };
    }
}
