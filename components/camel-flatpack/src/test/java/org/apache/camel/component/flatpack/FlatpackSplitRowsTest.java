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
package org.apache.camel.component.flatpack;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import net.sf.flatpack.DataSet;
import net.sf.flatpack.DefaultParserFactory;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With splitRows every row message has its own row, an exception in the consumer route fails the producer, a DataSet
 * that is not on a row does not convert to a Map, and a File or Path body is read.
 */
class FlatpackSplitRowsTest extends CamelTestSupport {

    private static final String SPLIT = "flatpack:delim:INVENTORY-Delimited.pzmap.xml";
    private static final String NO_SPLIT = "flatpack:delim:INVENTORY-Delimited.pzmap.xml?splitRows=false";
    private static final String FAIL = "flatpack:delim:INVENTORY-Delimited.pzmap.xml?allowShortLines=true";
    private static final File DATA = new File("src/test/data/delim/INVENTORY-CommaDelimitedWithQualifier.txt");
    private static final List<String> ITEMS = List.of("SOME VALVE", "AN ENGINE", "A BELT", "A BOLT");

    @Test
    void eachRowKeepsItsRowAfterTheNextRows() throws Exception {
        MockEndpoint rows = getMockEndpoint("mock:rows");
        rows.expectedMessageCount(4);

        template.sendBody(SPLIT, DATA);

        rows.assertIsSatisfied();
        // all the rows were sent before the bodies are read, as with a seda queue between
        for (int i = 0; i < ITEMS.size(); i++) {
            Message in = rows.getReceivedExchanges().get(i).getIn();
            assertEquals(i, in.getHeader(FlatpackConstants.FLATPACK_COUNTER));
            assertInstanceOf(DataSet.class, in.getBody());
            assertEquals(ITEMS.get(i), in.getBody(Map.class).get("ITEM_DESC"));
            assertEquals(ITEMS.get(i), in.getBody(DataSet.class).getString("ITEM_DESC"));
        }
    }

    @Test
    void listOfOneRowDoesNotDropTheOtherRows() throws Exception {
        MockEndpoint lists = getMockEndpoint("mock:lists");
        lists.expectedMessageCount(4);

        template.sendBody("direct:list", DATA);

        lists.assertIsSatisfied();
        for (int i = 0; i < ITEMS.size(); i++) {
            List<?> list = lists.getReceivedExchanges().get(i).getIn().getBody(List.class);
            assertEquals(1, list.size());
            assertEquals(ITEMS.get(i), ((Map<?, ?>) list.get(0)).get("ITEM_DESC"));
        }
    }

    @Test
    void exceptionInConsumerRouteFailsProducer() throws Exception {
        MockEndpoint failRows = getMockEndpoint("mock:fail-rows");
        failRows.expectedMessageCount(2);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody(FAIL, DATA));

        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Cannot process AN ENGINE", e.getCause().getMessage());
        // the rows after the failing row are not sent
        failRows.assertIsSatisfied();
        assertEquals(1, failRows.getReceivedExchanges().get(1).getIn().getHeader(FlatpackConstants.FLATPACK_COUNTER));
    }

    @Test
    void dataSetNotOnRowDoesNotConvertToMap() throws Exception {
        MockEndpoint all = getMockEndpoint("mock:all");
        all.expectedMessageCount(1);

        template.sendBody(NO_SPLIT, DATA);

        all.assertIsSatisfied();
        Message in = all.getReceivedExchanges().get(0).getIn();
        assertEquals(4, in.getBody(List.class).size());
        TypeConversionException e = assertThrows(TypeConversionException.class, () -> in.getBody(Map.class));
        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains("not on a row"), e.getCause().getMessage());
    }

    @Test
    void emptyInputWithoutSplitRows() throws Exception {
        MockEndpoint all = getMockEndpoint("mock:all");
        all.expectedMessageCount(1);

        template.sendBody(NO_SPLIT, "\"ITEM_DESC\",\"IN_STOCK\",\"PRICE\",\"LAST_RECV_DT\"\n");

        all.assertIsSatisfied();
        Message in = all.getReceivedExchanges().get(0).getIn();
        assertEquals(0, in.getHeader(FlatpackConstants.FLATPACK_COUNTER));
        assertTrue(in.getBody(List.class).isEmpty());
        TypeConversionException e = assertThrows(TypeConversionException.class, () -> in.getBody(Map.class));
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void pathBody() throws Exception {
        MockEndpoint rows = getMockEndpoint("mock:rows");
        rows.expectedMessageCount(4);

        template.sendBody(SPLIT, DATA.toPath());

        rows.assertIsSatisfied();
        assertEquals("A BOLT", rows.getReceivedExchanges().get(3).getIn().getBody(Map.class).get("ITEM_DESC"));
    }

    @Test
    void fileBodyWithFixedLength() throws Exception {
        MockEndpoint fixed = getMockEndpoint("mock:fixed");
        fixed.expectedMessageCount(4);

        template.sendBody("flatpack:fixed:PEOPLE-FixedLength.pzmap.xml",
                Path.of("src/test/data/fixed/PEOPLE-FixedLength.txt").toFile());

        fixed.assertIsSatisfied();
        Exchange last = fixed.getReceivedExchanges().get(3);
        assertEquals("FRED", last.getIn().getBody(Map.class).get("FIRSTNAME"));
    }

    @Test
    void dataSetRowIsOneReadOnlyRow() {
        DataSet dataSet = DefaultParserFactory.getInstance()
                .newDelimitedParser(new StringReader("A,B\n1,2\n3,4\n"), ',', '"').parse();
        assertTrue(dataSet.next());
        DataSetRow row = new DataSetRow(dataSet);
        assertTrue(dataSet.next());

        assertEquals(0, row.getIndex());
        assertEquals(1, row.getRowCount());
        assertEquals("1", row.getString("A"));
        assertFalse(row.next());
        assertTrue(row.getRecord().isEmpty());
        row.goTop();
        assertEquals(-1, row.getIndex());
        assertThrows(IllegalStateException.class, () -> row.getString("A"));
        assertTrue(row.next());
        assertEquals("2", row.getString("B"));
        assertThrows(UnsupportedOperationException.class, () -> row.setValue("A", "5"));
        assertEquals("3", dataSet.getString("A"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(SPLIT).to("mock:rows");

                from("direct:list").to("flatpack:delim:INVENTORY-Delimited.pzmap.xml?ignoreExtraColumns=true");
                from("flatpack:delim:INVENTORY-Delimited.pzmap.xml?ignoreExtraColumns=true")
                        .convertBodyTo(List.class)
                        .to("mock:lists");

                from(FAIL)
                        .to("mock:fail-rows")
                        .filter(exchange -> "AN ENGINE".equals(exchange.getIn().getBody(DataSet.class).getString("ITEM_DESC")))
                        .throwException(new IllegalArgumentException("Cannot process AN ENGINE"));

                from(NO_SPLIT).to("mock:all");

                from("flatpack:fixed:PEOPLE-FixedLength.pzmap.xml").to("mock:fixed");
            }
        };
    }
}
