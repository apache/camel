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
package org.apache.camel.component.cm.test;

import java.math.BigInteger;
import java.security.SecureRandom;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadRuntimeException;
import org.apache.camel.Produce;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Service;
import org.apache.camel.component.cm.CMEndpoint;
import org.apache.camel.component.cm.CMProducer;
import org.apache.camel.component.cm.client.SMSMessage;
import org.apache.camel.component.cm.exceptions.HostUnavailableException;
import org.apache.camel.component.cm.exceptions.cmresponse.CMResponseException;
import org.apache.camel.component.cm.exceptions.cmresponse.InsufficientBalanceException;
import org.apache.camel.component.cm.exceptions.cmresponse.InvalidMSISDNException;
import org.apache.camel.component.cm.exceptions.cmresponse.InvalidProductTokenException;
import org.apache.camel.component.cm.exceptions.cmresponse.NoAccountFoundForProductTokenException;
import org.apache.camel.component.cm.exceptions.cmresponse.NoMessageException;
import org.apache.camel.component.cm.exceptions.cmresponse.NotPhoneNumberFoundException;
import org.apache.camel.component.cm.exceptions.cmresponse.UnknownErrorException;
import org.apache.camel.component.cm.exceptions.cmresponse.UnroutableMessageException;
import org.apache.camel.component.cm.test.mocks.cmsender.CMResponseExceptionSender;
import org.apache.camel.component.cm.test.mocks.cmsender.InsufficientBalanceExceptionSender;
import org.apache.camel.component.cm.test.mocks.cmsender.InvalidMSISDNExceptionSender;
import org.apache.camel.component.cm.test.mocks.cmsender.InvalidProductTokenExceptionSender;
import org.apache.camel.component.cm.test.mocks.cmsender.NoAccountFoundForProductTokenExceptionSender;
import org.apache.camel.component.cm.test.mocks.cmsender.NoMessageExceptionSender;
import org.apache.camel.component.cm.test.mocks.cmsender.NotPhoneNumberFoundExceptionSender;
import org.apache.camel.component.cm.test.mocks.cmsender.UnknownErrorExceptionSender;
import org.apache.camel.component.cm.test.mocks.cmsender.UnroutableMessageExceptionSender;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.util.Assert;

@RunWith(CamelSpringRunner.class)
@ContextConfiguration(classes = {CamelTestConfiguration.class })
public class CMTest extends AbstractJUnit4SpringContextTests {

    // dependency: camel-spring-javaconfig

    @Autowired
    private CamelContext camelContext;

    private SecureRandom random = new SecureRandom();

    private final PhoneNumberUtil pnu = PhoneNumberUtil.getInstance();
    private String validNumber;

    @Produce("direct:sms")
    private CMProxy cmProxy;

    @EndpointInject("mock:test")
    private MockEndpoint mock;

    // private StopWatch stopWatch = new StopWatch(getClass().getSimpleName());

    @Before
    public void beforeTest() throws Exception {
        mock.reset();
        camelContext.getRouteController().startRoute(CamelTestConfiguration.SIMPLE_ROUTE_ID);
        validNumber = pnu.format(pnu.getExampleNumber("ES"), PhoneNumberFormat.E164);
    }

    @After
    public void afterTest() {

        try {
            camelContext.getRouteController().stopRoute(CamelTestConfiguration.SIMPLE_ROUTE_ID);
        } catch (Exception e) {
            logger.error("Exception trying to stop de routes", e);
        }

        // Stop all routes
        // for (Route route : camelContext.getRoutes()) {
        // try {
        // camelContext.getRouteController().stopRoute(route.getId());
        // } catch (Exception e) {
        // logger.error("Exception trying to stop de routes", e);
        // }
        // }
    }

    /*
     * 1. Invalid URI
     */

    @Test(expected = ResolveEndpointFailedException.class)
    public void testNotRequiredProductToken() throws Throwable {
        try {
            String schemedUri = "cm-sms://sgw01.cm.nl/gateway.ashx?defaultFrom=MyBusiness&defaultMaxNumberOfParts=8&testConnectionOnStartup=true";
            camelContext.getEndpoint(schemedUri).start();
        } catch (Throwable t) {
            throw t.getCause();
        }

    }

    @Test(expected = ResolveEndpointFailedException.class)
    public void testNotRequiredDefaultFrom() throws Throwable {
        try {
            String schemedUri = "cm-sms://sgw01.cm.nl/gateway.ashx?defaultFrom=MyBusiness&defaultMaxNumberOfParts=8&testConnectionOnStartup=true";
            camelContext.getEndpoint(schemedUri).start();
        } catch (Throwable t) {
            throw t.getCause();
        }

    }

    @Test(expected = HostUnavailableException.class)
    public void testHostUnavailableException() throws Throwable {
        // cm-sms://sgw01.cm.nl/gateway.ashx?defaultFrom=MyBusiness&defaultMaxNumberOfParts=8&productToken=ea723fd7-da81-4826-89bc-fa7144e71c40&testConnectionOnStartup=true
        String schemedUri = "cm-sms://dummy.sgw01.cm.nl/gateway.ashx?defaultFrom=MyBusiness&defaultMaxNumberOfParts=8&productToken=ea723fd7-da81-4826-89bc-fa7144e71c40&testConnectionOnStartup=true";
        Service service = camelContext.getEndpoint(schemedUri).createProducer();
        service.start();
    }

    @Test(expected = ResolveEndpointFailedException.class)
    public void testInvalidHostDuplicateScheme() throws Throwable {
        // cm-sms://sgw01.cm.nl/gateway.ashx?defaultFrom=MyBusiness&defaultMaxNumberOfParts=8&productToken=ea723fd7-da81-4826-89bc-fa7144e71c40&testConnectionOnStartup=true
        try {
            String schemedUri = "cm-sms://https://demo.com";
            camelContext.getEndpoint(schemedUri);
        } catch (Throwable t) {
            throw t.getCause();
        }
    }

    /*
     * 2. Invalid Payload
     */

    @Test(expected = RuntimeException.class)
    public void testNullPayload() throws Throwable {
        cmProxy.send(null);
    }

    // @DirtiesContext
    @Test(expected = NoAccountFoundForProductTokenException.class)
    public void testAsPartOfARoute() throws Exception {

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateUnicodeMessage(), validNumber, null);
        cmProxy.send(smsMessage);
    }

    @Test(expected = NoAccountFoundForProductTokenException.class)
    public void testNoAccountFoundForProductTokenException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new NoAccountFoundForProductTokenExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateGSM0338Message(), validNumber, null);
        send(producer, smsMessage);
    }

    /*
     * 3. CM Responses (Faking Exceptions)
     */

    @Test(expected = CMResponseException.class)
    public void testCMResponseException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new CMResponseExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateUnicodeMessage(), validNumber, null);
        send(producer, smsMessage);
    }

    @Test(expected = InsufficientBalanceException.class)
    public void testInsufficientBalanceException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new InsufficientBalanceExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateGSM0338Message(), validNumber, null);
        send(producer, smsMessage);
    }

    @Test(expected = InvalidMSISDNException.class)
    public void testInvalidMSISDNException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new InvalidMSISDNExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateUnicodeMessage(), validNumber, null);
        send(producer, smsMessage);
    }

    @Test(expected = InvalidProductTokenException.class)
    public void testInvalidProductTokenException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new InvalidProductTokenExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateUnicodeMessage(), validNumber, null);
        send(producer, smsMessage);
    }

    @Test(expected = NoMessageException.class)
    public void testNoMessageException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new NoMessageExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateGSM0338Message(), validNumber, null);
        send(producer, smsMessage);
    }

    @Test(expected = NotPhoneNumberFoundException.class)
    public void testNotPhoneNumberFoundException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new NotPhoneNumberFoundExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateUnicodeMessage(), validNumber, null);
        send(producer, smsMessage);
    }

    @Test(expected = UnknownErrorException.class)
    public void testUnknownErrorException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new UnknownErrorExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateGSM0338Message(), validNumber, null);
        send(producer, smsMessage);
    }

    @Test(expected = UnroutableMessageException.class)
    public void testUnroutableMessageException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new UnroutableMessageExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateUnicodeMessage(), validNumber, null);
        send(producer, smsMessage);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCMEndpointIsForProducing() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        endpoint.createConsumer(null);
    }

    @Test
    public void testCMEndpointGetHost() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        Assert.isTrue(endpoint.getHost().equals(applicationContext.getEnvironment().getRequiredProperty("cm.url")));
    }

    @Test(expected = InvalidPayloadRuntimeException.class)
    public void testSendInvalidPayload() throws Exception {

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateGSM0338Message(), null, null);
        cmProxy.send(smsMessage);
    }

    /*
     * CMMessages
     */

    // @Test(expected = RuntimeException.class)
    // public void testSkel() throws Exception {

    // mock.expectedMessageCount(1);
    //
    // // Body
    // final SMSMessage smsMessage = new SMSMessage("Hello CM", validNumber);
    // cmProxy.send(smsMessage);
    //
    // mock.assertIsSatisfied();
    // }

    private String generateUnicodeMessage() {
        String ch = "\uF400";
        return generateRandomLengthMessageByChar(ch);
    }

    private String generateGSM0338Message() {
        String ch = "a";
        return generateRandomLengthMessageByChar(ch);
    }

    private String generateRandomLengthMessageByChar(String ch) {
        // random Length
        int msgLength = (int) (Math.random() * 2000);
        StringBuffer message = new StringBuffer();
        for (int index = 0; index < msgLength; index++) {
            message.append(ch);
        }
        return message.toString();
    }

    //
    private String generateIdAsString() {
        return new BigInteger(130, random).toString(32);
    }

    private static void send(CMProducer producer, SMSMessage smsMessage) throws Exception {
        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.getIn().setBody(smsMessage);
        producer.process(exchange);
    }

}
