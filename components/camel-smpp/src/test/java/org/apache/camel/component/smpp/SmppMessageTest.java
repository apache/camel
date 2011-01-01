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

import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppMessage</code>
 * 
 * @version $Revision$
 * @author muellerc
 */
public class SmppMessageTest {
    
    private SmppMessage message;

    @Test
    public void emptyConstructorShouldReturnAnInstanceWithoutACommand() {
        message = new SmppMessage(new SmppConfiguration());
        
        assertNull(message.getCommand());
        assertTrue(message.getHeaders().isEmpty());
    }

    @Test
    public void alertNotificationConstructorShouldReturnAnInstanceWithACommandAndHeaderAttributes() {
        AlertNotification command = new AlertNotification();
        message = new SmppMessage(command, new SmppConfiguration());
        
        assertTrue(message.getCommand() instanceof AlertNotification);
        assertTrue(message.getHeaders().isEmpty());
        assertTrue(message.isAlertNotification());
    }
    
    @Test
    public void testSmppMessageDataSm() {
        DataSm command = new DataSm();
        message = new SmppMessage(command, new SmppConfiguration());
        
        assertTrue(message.getCommand() instanceof DataSm);
        assertTrue(message.getHeaders().isEmpty());
        assertTrue(message.isDataSm());
    }

    @Test
    public void testSmppMessageDeliverSm() {
        DeliverSm command = new DeliverSm();
        message = new SmppMessage(command, new SmppConfiguration());
        
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
        
        assertTrue(message.getCommand() instanceof DeliverSm);
        assertTrue(message.getHeaders().isEmpty());
        assertTrue(message.isDeliveryReceipt());
    }
    
    @Test
    public void newInstanceShouldReturnAnInstanceWithoutACommand() {
        message = new SmppMessage(new SmppConfiguration());
        SmppMessage msg = message.newInstance();
        
        assertNotNull(msg);
        assertNull(msg.getCommand());
        assertTrue(msg.getHeaders().isEmpty());
    }
    
    @Test
    public void createBodyShouldReturnNullIfTheCommandIsNull() {
        message = new SmppMessage(new SmppConfiguration());
        
        assertNull(message.createBody());
    }
    
    @Test
    public void createBodyShouldReturnNullIfTheCommandIsNotAMessageRequest() {
        AlertNotification command = new AlertNotification();
        message = new SmppMessage(command, new SmppConfiguration());
        
        assertNull(message.createBody());
    }
    
    @Test
    public void createBodyShouldReturnTheShortMessageIfTheCommandIsAMessageRequest() {
        DeliverSm command = new DeliverSm();
        command.setShortMessage("Hello SMPP world!".getBytes());
        message = new SmppMessage(command, new SmppConfiguration());
        
        assertEquals("Hello SMPP world!", message.createBody());
    }
    
    @Test
    public void toStringShouldReturnTheBodyIfTheCommandIsNull() {
        message = new SmppMessage(new SmppConfiguration());
        
        assertEquals("SmppMessage: null", message.toString());
    }
    
    @Test
    public void toStringShouldReturnTheShortMessageIfTheCommandIsNotNull() {
        DeliverSm command = new DeliverSm();
        command.setShortMessage("Hello SMPP world!".getBytes());
        message = new SmppMessage(command, new SmppConfiguration());
        
        assertEquals("SmppMessage: PDUHeader(0, 00000000, 00000000, 0)", message.toString());
    }
}