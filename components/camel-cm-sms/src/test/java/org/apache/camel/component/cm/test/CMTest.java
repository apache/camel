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
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.Assert;

import static org.junit.jupiter.api.Assertions.assertThrows;

@CamelSpringTest
@ContextConfiguration(classes = { CamelTestConfiguration.class })
public class CMTest {

    // dependency: camel-spring-javaconfig

    private static final Logger LOGGER = LoggerFactory.getLogger(CMTest.class);

    @Autowired
    ApplicationContext applicationContext;

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

    @BeforeEach
    public void beforeTest() throws Exception {
        mock.reset();
        camelContext.getRouteController().startRoute(CamelTestConfiguration.SIMPLE_ROUTE_ID);
        validNumber = pnu.format(pnu.getExampleNumber("ES"), PhoneNumberFormat.E164);
    }

    @AfterEach
    public void afterTest() {

        try {
            camelContext.getRouteController().stopRoute(CamelTestConfiguration.SIMPLE_ROUTE_ID);
        } catch (Exception e) {
            LOGGER.error("Exception trying to stop de routes", e);
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

    @Test
    public void testNotRequiredProductToken() throws Throwable {
        String schemedUri
                = "cm-sms://sgw01.cm.nl/gateway.ashx?defaultFrom=MyBusiness&defaultMaxNumberOfParts=8&testConnectionOnStartup=true";
        assertThrows(ResolveEndpointFailedException.class,
                () -> camelContext.getEndpoint(schemedUri).start());
    }

    @Test
    public void testNotRequiredDefaultFrom() throws Throwable {
        String schemedUri
                = "cm-sms://sgw01.cm.nl/gateway.ashx?defaultFrom=MyBusiness&defaultMaxNumberOfParts=8&testConnectionOnStartup=true";
        assertThrows(ResolveEndpointFailedException.class,
                () -> camelContext.getEndpoint(schemedUri).start());
    }

    @Test
    public void testHostUnavailableException() throws Throwable {
        // cm-sms://sgw01.cm.nl/gateway.ashx?defaultFrom=MyBusiness&defaultMaxNumberOfParts=8&productToken=ea723fd7-da81-4826-89bc-fa7144e71c40&testConnectionOnStartup=true
        String schemedUri
                = "cm-sms://dummy.sgw01.cm.nl/gateway.ashx?defaultFrom=MyBusiness&defaultMaxNumberOfParts=8&productToken=ea723fd7-da81-4826-89bc-fa7144e71c40&testConnectionOnStartup=true";
        Service service = camelContext.getEndpoint(schemedUri).createProducer();
        assertThrows(HostUnavailableException.class,
                () -> service.start());
    }

    @Test
    public void testInvalidHostDuplicateScheme() throws Throwable {
        // cm-sms://sgw01.cm.nl/gateway.ashx?defaultFrom=MyBusiness&defaultMaxNumberOfParts=8&productToken=ea723fd7-da81-4826-89bc-fa7144e71c40&testConnectionOnStartup=true
        String schemedUri = "cm-sms://https://demo.com";
        assertThrows(ResolveEndpointFailedException.class,
                () -> camelContext.getEndpoint(schemedUri));
    }

    /*
     * 2. Invalid Payload
     */

    @Test
    public void testNullPayload() throws Throwable {
        assertThrows(RuntimeException.class,
                () -> cmProxy.send(null));
    }

    // @DirtiesContext
    @Test
    public void testAsPartOfARoute() throws Exception {

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateUnicodeMessage(), validNumber, null);
        assertThrows(NoAccountFoundForProductTokenException.class,
                () -> cmProxy.send(smsMessage));
    }

    @Test
    public void testNoAccountFoundForProductTokenException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint
                = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new NoAccountFoundForProductTokenExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateGSM0338Message(), validNumber, null);
        assertThrows(NoAccountFoundForProductTokenException.class,
                () -> send(producer, smsMessage));
    }

    /*
     * 3. CM Responses (Faking Exceptions)
     */

    @Test
    public void testCMResponseException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint
                = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new CMResponseExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateUnicodeMessage(), validNumber, null);
        assertThrows(CMResponseException.class,
                () -> send(producer, smsMessage));
    }

    @Test
    public void testInsufficientBalanceException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint
                = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new InsufficientBalanceExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateGSM0338Message(), validNumber, null);
        assertThrows(InsufficientBalanceException.class,
                () -> send(producer, smsMessage));
    }

    @Test
    public void testInvalidMSISDNException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint
                = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new InvalidMSISDNExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateUnicodeMessage(), validNumber, null);
        assertThrows(InvalidMSISDNException.class,
                () -> send(producer, smsMessage));
    }

    @Test
    public void testInvalidProductTokenException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint
                = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new InvalidProductTokenExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateUnicodeMessage(), validNumber, null);
        assertThrows(InvalidProductTokenException.class,
                () -> send(producer, smsMessage));
    }

    @Test
    public void testNoMessageException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint
                = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new NoMessageExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateGSM0338Message(), validNumber, null);
        assertThrows(NoMessageException.class,
                () -> send(producer, smsMessage));
    }

    @Test
    public void testNotPhoneNumberFoundException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint
                = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new NotPhoneNumberFoundExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateUnicodeMessage(), validNumber, null);
        assertThrows(NotPhoneNumberFoundException.class,
                () -> send(producer, smsMessage));
    }

    @Test
    public void testUnknownErrorException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint
                = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new UnknownErrorExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateGSM0338Message(), validNumber, null);
        assertThrows(UnknownErrorException.class,
                () -> send(producer, smsMessage));
    }

    @Test
    public void testUnroutableMessageException() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint
                = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        CMProducer producer = endpoint.createProducer();
        producer.setSender(new UnroutableMessageExceptionSender());

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateUnicodeMessage(), validNumber, null);
        assertThrows(UnroutableMessageException.class,
                () -> send(producer, smsMessage));
    }

    @Test
    public void testCMEndpointIsForProducing() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint
                = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        assertThrows(UnsupportedOperationException.class,
                () -> endpoint.createConsumer(null));
    }

    @Test
    public void testCMEndpointGetHost() throws Exception {

        // Change sending strategy
        CMEndpoint endpoint
                = (CMEndpoint) camelContext.getEndpoint(applicationContext.getBean(CamelTestConfiguration.class).getUri());
        Assert.isTrue(endpoint.getHost().equals(applicationContext.getEnvironment().getRequiredProperty("cm.url")),
                "Endpoint host and environment property do not match");
    }

    @Test
    public void testSendInvalidPayload() throws Exception {

        // Body
        final SMSMessage smsMessage = new SMSMessage(generateIdAsString(), generateGSM0338Message(), null, null);
        assertThrows(InvalidPayloadRuntimeException.class,
                () -> cmProxy.send(smsMessage));
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
        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < msgLength; index++) {
            sb.append(ch);
        }
        return sb.toString();
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
