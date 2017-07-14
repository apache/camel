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
package org.apache.camel.component.smpp;

import java.nio.charset.Charset;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppMessage</code>
 * 
 * @version 
 */
public class SmppMessageTest {
    
    private SmppMessage message;
    private CamelContext camelContext = new DefaultCamelContext();

    @Test
    public void emptyConstructorShouldReturnAnInstanceWithoutACommand() {
        message = new SmppMessage(new SmppConfiguration());
        message.setCamelContext(camelContext);
        
        assertNull(message.getCommand());
        assertTrue(message.getHeaders().isEmpty());
    }

    @Test
    public void alertNotificationConstructorShouldReturnAnInstanceWithACommandAndHeaderAttributes() {
        AlertNotification command = new AlertNotification();
        message = new SmppMessage(command, new SmppConfiguration());
        message.setCamelContext(camelContext);
        
        assertTrue(message.getCommand() instanceof AlertNotification);
        assertTrue(message.getHeaders().isEmpty());
        assertTrue(message.isAlertNotification());
    }
    
    @Test
    public void testSmppMessageDataSm() {
        DataSm command = new DataSm();
        message = new SmppMessage(command, new SmppConfiguration());
        message.setCamelContext(camelContext);
        
        assertTrue(message.getCommand() instanceof DataSm);
        assertTrue(message.getHeaders().isEmpty());
        assertTrue(message.isDataSm());
    }

    @Test
    public void testSmppMessageDeliverSm() {
        DeliverSm command = new DeliverSm();
        message = new SmppMessage(command, new SmppConfiguration());
        message.setCamelContext(camelContext);
        
        assertTrue(message.getCommand() instanceof DeliverSm);
        assertTrue(message.getHeaders().isEmpty());
        assertTrue(message.isDeliverSm());
    }
    
    @Test
    public void testSmppMessageDeliverReceipt() {
        DeliverSm command = new DeliverSm();
        command.setSmscDeliveryReceipt();
        command.setShortMessage("id:2 sub:001 dlvrd:001 submit date:0908312310 done date:0908312311 stat:DELIVRD err:xxx Text:Hello SMPP world!".getBytes());
        message = new SmppMessage(command, new SmppConfiguration());
        message.setCamelContext(camelContext);
        
        assertTrue(message.getCommand() instanceof DeliverSm);
        assertTrue(message.getHeaders().isEmpty());
        assertTrue(message.isDeliveryReceipt());
    }
    
    @Test
    public void newInstanceShouldReturnAnInstanceWithoutACommand() {
        message = new SmppMessage(new SmppConfiguration());
        message.setCamelContext(camelContext);
        SmppMessage msg = message.newInstance();

        assertNotNull(msg);
        assertNull(msg.getCommand());
        assertTrue(msg.getHeaders().isEmpty());
    }

    @Test
    public void createBodyShouldNotMangle8bitDataCodingShortMessage() {
        final Set<String> encodings = Charset.availableCharsets().keySet();

        final byte[] dataCodings = {
            (byte)0x02,
            (byte)0x04,
            (byte)0xF6,
            (byte)0xF4
        };

        byte[] body = {
            (byte)0xFF, 'A', 'B', (byte)0x00,
            (byte)0xFF, (byte)0x7F, 'C', (byte)0xFF
        };

        DeliverSm command = new DeliverSm();
        SmppConfiguration config = new SmppConfiguration();

        for (byte dataCoding : dataCodings) {
            command.setDataCoding(dataCoding);
            command.setShortMessage(body);
            for (String encoding : encodings) {
                config.setEncoding(encoding);
                message = new SmppMessage(command, config);
                
                assertArrayEquals(
                    String.format("data coding=0x%02X; encoding=%s",
                                  dataCoding,
                                  encoding),
                    body, (byte[])message.createBody());
            }
        }
    }
    
    @Test
    public void createBodyShouldReturnNullIfTheCommandIsNull() {
        message = new SmppMessage(new SmppConfiguration());
        message.setCamelContext(camelContext);


        assertNull(message.createBody());
    }
    
    @Test
    public void createBodyShouldReturnNullIfTheCommandIsNotAMessageRequest() {
        AlertNotification command = new AlertNotification();
        message = new SmppMessage(command, new SmppConfiguration());
        message.setCamelContext(camelContext);

        assertNull(message.createBody());
    }
    
    @Test
    public void createBodyShouldReturnTheShortMessageIfTheCommandIsAMessageRequest() {
        DeliverSm command = new DeliverSm();
        command.setShortMessage("Hello SMPP world!".getBytes());
        message = new SmppMessage(command, new SmppConfiguration());
        message.setCamelContext(camelContext);

        assertEquals("Hello SMPP world!", message.createBody());
    }
    
    @Test
    public void toStringShouldReturnTheBodyIfTheCommandIsNull() {
        message = new SmppMessage(new SmppConfiguration());
        message.setCamelContext(camelContext);

        assertEquals("SmppMessage: null", message.toString());
    }
    
    @Test
    public void toStringShouldReturnTheShortMessageIfTheCommandIsNotNull() {
        DeliverSm command = new DeliverSm();
        command.setShortMessage("Hello SMPP world!".getBytes());
        message = new SmppMessage(command, new SmppConfiguration());
        message.setCamelContext(camelContext);

        assertEquals("SmppMessage: PDUHeader(0, 00000000, 00000000, 0)", message.toString());
    }
}