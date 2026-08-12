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
package org.apache.camel.component.ubl;

import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.IDType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.InvoiceTypeCodeType;
import oasis.names.specification.ubl.schema.xsd.creditnote_21.CreditNoteType;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import oasis.names.specification.ubl.schema.xsd.order_21.OrderType;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UblDataFormatTest extends CamelTestSupport {

    @Test
    void testMarshalAndUnmarshalInvoice() throws Exception {
        InvoiceType invoice = createTestInvoice();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(InvoiceType.class);

        String xml = template.requestBody("direct:marshal", invoice, String.class);
        assertThat(xml).contains("<Invoice");
        assertThat(xml).contains("INV-001");

        template.sendBody("direct:unmarshal", xml);
        mock.assertIsSatisfied();

        InvoiceType result = mock.getExchanges().get(0).getIn().getBody(InvoiceType.class);
        assertThat(result.getIDValue()).isEqualTo("INV-001");
        assertThat(result.getInvoiceTypeCodeValue()).isEqualTo("380");
    }

    @Test
    void testMarshalAndUnmarshalCreditNote() throws Exception {
        CreditNoteType creditNote = new CreditNoteType();
        IDType id = new IDType();
        id.setValue("CN-001");
        creditNote.setID(id);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(CreditNoteType.class);

        String xml = template.requestBody("direct:marshal", creditNote, String.class);
        assertThat(xml).contains("<CreditNote");
        assertThat(xml).contains("CN-001");

        template.sendBody("direct:unmarshal", xml);
        mock.assertIsSatisfied();

        CreditNoteType result = mock.getExchanges().get(0).getIn().getBody(CreditNoteType.class);
        assertThat(result.getIDValue()).isEqualTo("CN-001");
    }

    @Test
    void testMarshalAndUnmarshalOrder() throws Exception {
        OrderType order = new OrderType();
        IDType id = new IDType();
        id.setValue("ORD-001");
        order.setID(id);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(OrderType.class);

        String xml = template.requestBody("direct:marshal", order, String.class);
        assertThat(xml).contains("<Order");
        assertThat(xml).contains("ORD-001");

        template.sendBody("direct:unmarshal", xml);
        mock.assertIsSatisfied();

        OrderType result = mock.getExchanges().get(0).getIn().getBody(OrderType.class);
        assertThat(result.getIDValue()).isEqualTo("ORD-001");
    }

    @Test
    void testUnmarshalFromXml() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>TEST-999</cbc:ID>
                    <cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>
                </Invoice>
                """;

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(InvoiceType.class);

        template.sendBody("direct:unmarshal", xml);
        mock.assertIsSatisfied();

        InvoiceType result = mock.getExchanges().get(0).getIn().getBody(InvoiceType.class);
        assertThat(result.getIDValue()).isEqualTo("TEST-999");
    }

    @Test
    void testMarshalWithPrettyPrint() throws Exception {
        InvoiceType invoice = createTestInvoice();

        String xml = template.requestBody("direct:marshalPretty", invoice, String.class);
        assertThat(xml).contains("<Invoice");
        assertThat(xml).contains("INV-001");
        assertThat(xml).contains("\n");
    }

    @Test
    void testMarshalUnsupportedType() {
        assertThatThrownBy(() -> template.requestBody("direct:marshal", "not a UBL type", String.class))
                .isInstanceOf(CamelExecutionException.class)
                .rootCause()
                .hasMessageContaining("Unsupported UBL document type");
    }

    private InvoiceType createTestInvoice() {
        InvoiceType invoice = new InvoiceType();

        IDType id = new IDType();
        id.setValue("INV-001");
        invoice.setID(id);

        InvoiceTypeCodeType typeCode = new InvoiceTypeCodeType();
        typeCode.setValue("380");
        invoice.setInvoiceTypeCode(typeCode);

        return invoice;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:marshal").marshal().ubl();
                from("direct:marshalPretty").marshal().ubl(true);
                from("direct:unmarshal").unmarshal().ubl().to("mock:result");
            }
        };
    }
}
