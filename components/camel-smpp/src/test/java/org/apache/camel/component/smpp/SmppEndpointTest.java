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

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;


/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppEndpoint</code>
 * 
 * @version 
 */
public class SmppEndpointTest {
    
    private SmppEndpoint endpoint;
    private SmppConfiguration configuration;
    private Component component;
    private SmppBinding binding;

    @Before
    public void setUp() throws Exception {
        configuration = createMock(SmppConfiguration.class);
        component = createMock(Component.class);
        binding = createMock(SmppBinding.class);
        
        expect(component.createConfiguration("smpp://smppclient@localhost:2775")).andReturn(null);
        expect(component.getCamelContext()).andReturn(null);
        replay(component);

        endpoint = new SmppEndpoint("smpp://smppclient@localhost:2775", component, configuration);
        endpoint.setBinding(binding);
    }

    @Test
    public void isLenientPropertiesShouldReturnTrue() {
        assertTrue(endpoint.isLenientProperties());
    }

    @Test
    public void isSingletonShouldReturnTrue() {
        assertTrue(endpoint.isSingleton());
    }

    @Test
    public void createEndpointUriShouldReturnTheEndpointUri() {
        expect(configuration.getUsingSSL()).andReturn(false);
        expect(configuration.getSystemId()).andReturn("smppclient").times(2);
        expect(configuration.getHost()).andReturn("localhost");
        expect(configuration.getPort()).andReturn(new Integer(2775));
        
        replay(configuration);
        
        assertEquals("smpp://smppclient@localhost:2775", endpoint.createEndpointUri());
        
        verify(configuration);
    }

    @Test
    public void createEndpointUriWithoutUserShouldReturnTheEndpointUri() {
        expect(configuration.getUsingSSL()).andReturn(false);
        expect(configuration.getSystemId()).andReturn(null);
        expect(configuration.getHost()).andReturn("localhost");
        expect(configuration.getPort()).andReturn(new Integer(2775));
        
        replay(configuration);
        
        assertEquals("smpp://localhost:2775", endpoint.createEndpointUri());
        
        verify(configuration);
    }

    @Test
    public void createConsumerShouldReturnASmppConsumer() throws Exception {
        Processor processor = createMock(Processor.class);
        
        replay(processor);
        
        Consumer consumer = endpoint.createConsumer(processor);
        
        verify(processor);
        
        assertTrue(consumer instanceof SmppConsumer);
    }

    @Test
    public void createProducerShouldReturnASmppProducer() throws Exception {
        Producer producer = endpoint.createProducer();
        
        assertTrue(producer instanceof SmppProducer);
    }

    @Test
    public void createOnAcceptAlertNotificationExchange() {
        AlertNotification alertNotification = createMock(AlertNotification.class);
        SmppMessage message = createMock(SmppMessage.class);
        expect(binding.createSmppMessage(alertNotification)).andReturn(message);
        message.setExchange(isA(Exchange.class));
        
        replay(alertNotification, binding, message);
        
        Exchange exchange = endpoint.createOnAcceptAlertNotificationExchange(alertNotification);
        
        verify(alertNotification, binding, message);
        
        assertSame(binding, exchange.getProperty(Exchange.BINDING));
        assertSame(message, exchange.getIn());
        assertSame(ExchangePattern.InOnly, exchange.getPattern());
    }

    @Test
    public void createOnAcceptAlertNotificationExchangeWithExchangePattern() {
        AlertNotification alertNotification = createMock(AlertNotification.class);
        SmppMessage message = createMock(SmppMessage.class);
        expect(binding.createSmppMessage(alertNotification)).andReturn(message);
        message.setExchange(isA(Exchange.class));
        
        replay(alertNotification, binding, message);
        
        Exchange exchange = endpoint.createOnAcceptAlertNotificationExchange(ExchangePattern.InOut, alertNotification);
        
        verify(alertNotification, binding, message);
        
        assertSame(binding, exchange.getProperty(Exchange.BINDING));
        assertSame(message, exchange.getIn());
        assertSame(ExchangePattern.InOut, exchange.getPattern());
    }

    @Test
    public void createOnAcceptDeliverSmExchange() throws Exception {
        DeliverSm deliverSm = createMock(DeliverSm.class);
        SmppMessage message = createMock(SmppMessage.class);
        expect(binding.createSmppMessage(deliverSm)).andReturn(message);
        message.setExchange(isA(Exchange.class));
        
        replay(deliverSm, binding, message);
        
        Exchange exchange = endpoint.createOnAcceptDeliverSmExchange(deliverSm);
        
        verify(deliverSm, binding, message);
        
        assertSame(binding, exchange.getProperty(Exchange.BINDING));
        assertSame(message, exchange.getIn());
        assertSame(ExchangePattern.InOnly, exchange.getPattern());
    }

    @Test
    public void createOnAcceptDeliverSmWithExchangePattern() throws Exception {
        DeliverSm deliverSm = createMock(DeliverSm.class);
        SmppMessage message = createMock(SmppMessage.class);
        expect(binding.createSmppMessage(deliverSm)).andReturn(message);
        message.setExchange(isA(Exchange.class));
        
        replay(deliverSm, binding, message);
        
        Exchange exchange = endpoint.createOnAcceptDeliverSmExchange(ExchangePattern.InOut, deliverSm);
        
        verify(deliverSm, binding, message);
        
        assertSame(binding, exchange.getProperty(Exchange.BINDING));
        assertSame(message, exchange.getIn());
        assertSame(ExchangePattern.InOut, exchange.getPattern());
    }
    
    @Test
    public void createOnAcceptDataSm() throws Exception {
        DataSm dataSm = createMock(DataSm.class);
        SmppMessage message = createMock(SmppMessage.class);
        expect(binding.createSmppMessage(eq(dataSm), isA(String.class))).andReturn(message);
        message.setExchange(isA(Exchange.class));
        
        replay(dataSm, binding, message);
        
        Exchange exchange = endpoint.createOnAcceptDataSm(dataSm, "1");
        
        verify(dataSm, binding, message);
        
        assertSame(binding, exchange.getProperty(Exchange.BINDING));
        assertSame(message, exchange.getIn());
        assertSame(ExchangePattern.InOnly, exchange.getPattern());
    }
    
    @Test
    public void createOnAcceptDataSmWithExchangePattern() throws Exception {
        DataSm dataSm = createMock(DataSm.class);
        SmppMessage message = createMock(SmppMessage.class);
        expect(binding.createSmppMessage(eq(dataSm), isA(String.class))).andReturn(message);
        message.setExchange(isA(Exchange.class));
        
        replay(dataSm, binding, message);
        
        Exchange exchange = endpoint.createOnAcceptDataSm(ExchangePattern.InOut, dataSm, "1");
        
        verify(dataSm, binding, message);
        
        assertSame(binding, exchange.getProperty(Exchange.BINDING));
        assertSame(message, exchange.getIn());
        assertSame(ExchangePattern.InOut, exchange.getPattern());
    }

    @Test
    public void getConnectionStringShouldReturnTheConnectionString() {
        expect(configuration.getUsingSSL()).andReturn(false);
        expect(configuration.getSystemId()).andReturn("smppclient").times(2);
        expect(configuration.getHost()).andReturn("localhost");
        expect(configuration.getPort()).andReturn(new Integer(2775));
        
        replay(configuration);
        
        assertEquals("smpp://smppclient@localhost:2775", endpoint.getConnectionString());
        
        verify(configuration);
    }

    @Test
    public void getConnectionStringWithoutUserShouldReturnTheConnectionString() {
        expect(configuration.getUsingSSL()).andReturn(false);
        expect(configuration.getSystemId()).andReturn(null);
        expect(configuration.getHost()).andReturn("localhost");
        expect(configuration.getPort()).andReturn(new Integer(2775));
        
        replay(configuration);
        
        assertEquals("smpp://localhost:2775", endpoint.getConnectionString());
        
        verify(configuration);
    }

    @Test
    public void getConfigurationShouldReturnTheSetValue() {
        assertSame(configuration, endpoint.getConfiguration());
    }
}