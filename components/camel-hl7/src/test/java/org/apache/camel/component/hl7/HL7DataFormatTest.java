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
package org.apache.camel.component.hl7;

import static org.apache.camel.Exchange.CHARSET_NAME;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v24.message.ADR_A19;
import ca.uhn.hl7v2.model.v24.segment.MSA;
import ca.uhn.hl7v2.model.v24.segment.MSH;
import ca.uhn.hl7v2.model.v24.segment.PID;
import ca.uhn.hl7v2.model.v24.segment.QRD;

/**
 * Unit test for HL7 DataFormat.
 */
public class HL7DataFormatTest extends CamelTestSupport {

	@Test
	public void testMarshal() throws Exception {
		MockEndpoint mock = getMockEndpoint("mock:marshal");
		mock.expectedMessageCount(1);
		mock.message(0).body().isInstanceOf(byte[].class);
		mock.message(0).body(String.class).contains("MSA|AA|123");
		mock.message(0).body(String.class).contains("QRD|20080805120000");

		Message message = createHL7AsMessage();
		template.sendBody("direct:marshal", message);

		assertMockEndpointsSatisfied();
	}

	@Test
	public void testMarshalISO8859() throws Exception {
		MockEndpoint mock = getMockEndpoint("mock:marshal");
		mock.expectedMessageCount(1);
		mock.message(0).body().isInstanceOf(byte[].class);
		mock.message(0).body(String.class).contains("MSA|AA|123");
		mock.message(0).body(String.class).contains("QRD|20080805120000");
		mock.message(0).body(String.class).not().contains("ÀÈÌÒÙŐ");

		Message message = createHL7AsMessage();
		template.sendBodyAndProperty("direct:marshal", message, CHARSET_NAME, "ISO-8859-1");

		assertMockEndpointsSatisfied();
	}

	@Test
	public void testMarshalUTF8() throws Exception {
		MockEndpoint mock = getMockEndpoint("mock:marshal");
		mock.expectedMessageCount(1);
		mock.message(0).body().isInstanceOf(byte[].class);
		mock.message(0).body(String.class).contains("MSA|AA|123");
		mock.message(0).body(String.class).contains("QRD|20080805120000");
		mock.message(0).body(String.class).contains("ÀÈÌÒÙŐ");

		Message message = createHL7AsMessage();
		template.sendBodyAndProperty("direct:marshal", message, CHARSET_NAME, "UTF-8");

		assertMockEndpointsSatisfied();
	}

	@Test
	public void testUnmarshal() throws Exception {
		MockEndpoint mock = getMockEndpoint("mock:unmarshal");
		mock.expectedMessageCount(1);
		mock.message(0).body().isInstanceOf(Message.class);

		mock.expectedHeaderReceived(HL7Constants.HL7_SENDING_APPLICATION, "MYSENDER");
		mock.expectedHeaderReceived(HL7Constants.HL7_SENDING_FACILITY, "MYSENDERAPP");
		mock.expectedHeaderReceived(HL7Constants.HL7_RECEIVING_APPLICATION, "MYCLIENT");
		mock.expectedHeaderReceived(HL7Constants.HL7_RECEIVING_FACILITY, "MYCLIENTAPP");
		mock.expectedHeaderReceived(HL7Constants.HL7_TIMESTAMP, "200612211200");
		mock.expectedHeaderReceived(HL7Constants.HL7_SECURITY, null);
		mock.expectedHeaderReceived(HL7Constants.HL7_MESSAGE_TYPE, "QRY");
		mock.expectedHeaderReceived(HL7Constants.HL7_TRIGGER_EVENT, "A19");
		mock.expectedHeaderReceived(HL7Constants.HL7_MESSAGE_CONTROL, "1234");
		mock.expectedHeaderReceived(HL7Constants.HL7_PROCESSING_ID, "P");
		mock.expectedHeaderReceived(HL7Constants.HL7_VERSION_ID, "2.4");

		String body = createHL7AsString();
		template.sendBody("direct:unmarshal", body);

		assertMockEndpointsSatisfied();

		Message msg = mock.getExchanges().get(0).getIn().getBody(Message.class);
		assertEquals("2.4", msg.getVersion());
		QRD qrd = (QRD) msg.get("QRD");
		assertEquals("0101701234", qrd.getWhoSubjectFilter(0).getIDNumber().getValue());
	}

	@Test
	public void testUnmarshalUTF8() throws Exception {
		MockEndpoint mock = getMockEndpoint("mock:unmarshal");
		mock.expectedMessageCount(1);
		mock.message(0).body().isInstanceOf(Message.class);

		String body = createHL7AsStringWithNotISO8859Char();
		template.sendBodyAndProperty("direct:unmarshal", body, CHARSET_NAME, "UTF-8");

		assertMockEndpointsSatisfied();

		Message msg = mock.getExchanges().get(0).getIn().getBody(Message.class);
		assertEquals("2.4", msg.getVersion());
		PID pid = (PID) msg.get("PID");
		assertEquals("ÀÈÌÒÙŐ", pid.getPid5_PatientName(0).getXpn1_FamilyName().getFn1_Surname().getValue());
	}
	
	@Test
	public void testUnmarshalISO8859() throws Exception {
		MockEndpoint mock = getMockEndpoint("mock:unmarshal");
		mock.expectedMessageCount(1);
		mock.message(0).body().isInstanceOf(Message.class);

		String body = createHL7AsStringWithNotISO8859Char();
		template.sendBodyAndProperty("direct:unmarshal", body, CHARSET_NAME, "ISO-8859-1");

		assertMockEndpointsSatisfied();

		Message msg = mock.getExchanges().get(0).getIn().getBody(Message.class);
		assertEquals("2.4", msg.getVersion());
		PID pid = (PID) msg.get("PID");
		assertNotEquals("ÀÈÌÒÙŐ", pid.getPid5_PatientName(0).getXpn1_FamilyName().getFn1_Surname().getValue());
	}
	
	protected RouteBuilder createRouteBuilder() throws Exception {
		return new RouteBuilder() {
			public void configure() throws Exception {
				from("direct:marshal").marshal().hl7().to("mock:marshal");
				from("direct:unmarshal").unmarshal().hl7().to("mock:unmarshal");
			}
		};
	}

	private static String createHL7AsString() {
		String line1 = "MSH|^~\\&|MYSENDER|MYSENDERAPP|MYCLIENT|MYCLIENTAPP|200612211200||QRY^A19|1234|P|2.4";
		String line2 = "QRD|200612211200|R|I|GetPatient|||1^RD|0101701234|DEM||";

		StringBuilder body = new StringBuilder();
		body.append(line1);
		body.append("\r");
		body.append(line2);
		return body.toString();
	}

	private static String createHL7AsStringWithNotISO8859Char() {
		String line1 = "MSH|^~\\&|EPIC|EPICADT|SMS|SMSADT|199912271408|CHARRIS|ADT^A04|1817457|D|2.4";
		String line2 = "PID||0493575^^^2^ID 1|454721||ÀÈÌÒÙŐ^JOHN^^^^|ÀÈÌÒÙŐ^JOHN^^^^|19480203|M||B|254 MYSTREET AVE^^MYTOWN^OH^44123^USA||(216)123-4567|||M|NON|400003403~1129086|NK1||ROE^MARIE^^^^|SPO||(216)123-4567||EC|||||||||||||||||||||||||||";
		String line3 = "PV1||O|168 ~219~C~PMA^^^^^^^^^||||277^ALLEN MYLASTNAME^BONNIE^^^^|||||||||| ||2688684|||||||||||||||||||||||||199912271408||||||002376853";

		StringBuilder body = new StringBuilder();
		body.append(line1);
		body.append("\r");
		body.append(line2);
		body.append("\r");
		body.append(line3);
		return body.toString();
	}
	
	private static Message createHL7AsMessage() throws Exception {
		ADR_A19 adr = new ADR_A19();

		// Populate the MSH Segment
		MSH mshSegment = adr.getMSH();
		mshSegment.getFieldSeparator().setValue("|");
		mshSegment.getEncodingCharacters().setValue("^~\\&");
		mshSegment.getDateTimeOfMessage().getTimeOfAnEvent().setValue("200701011539");
		mshSegment.getSendingApplication().getNamespaceID().setValue("MYSENDER");
		mshSegment.getSequenceNumber().setValue("123");
		mshSegment.getMessageType().getMessageType().setValue("ADR");
		mshSegment.getMessageType().getTriggerEvent().setValue("A19");

		// Populate the PID Segment
		MSA msa = adr.getMSA();
		msa.getAcknowledgementCode().setValue("AA");
		msa.getMessageControlID().setValue("123");
		msa.getMsa3_TextMessage().setValue("ÀÈÌÒÙŐ");

		QRD qrd = adr.getQRD();
		qrd.getQueryDateTime().getTimeOfAnEvent().setValue("20080805120000");

		return adr.getMessage();
	}

}
