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
package org.apache.camel.component.jmx;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Waits for messages to arrive on the mock endpoint and performs assertions on the message bodies.
 * 
 */
public class MockEndpointFixture {
    MockEndpoint mMockEndpoint;
    
    public MockEndpointFixture(MockEndpoint aMockEndpoint) {
        setMockEndpoint(aMockEndpoint);
    }
    
    protected void waitForMessages() throws InterruptedException {
        waitForMessages(mMockEndpoint);
    }

    protected void waitForMessages(MockEndpoint aMockEndpoint) throws InterruptedException {
        mMockEndpoint.await(10, TimeUnit.SECONDS);
        assertEquals("Expected number of messages didn't arrive before timeout", aMockEndpoint.getExpectedCount(), aMockEndpoint.getReceivedCounter());
    }
    
    protected MockEndpoint getMockEndpoint() {
        return mMockEndpoint;
    }

    protected void setMockEndpoint(MockEndpoint aMockEndpoint) {
        mMockEndpoint = aMockEndpoint;
    }

    /**
     * Assert that we've received the message and resets the mock endpoint
     */
    protected void assertMessageReceived(File aExpectedFile) throws Exception {
        Document expectedDoc = XmlFixture.toDoc(aExpectedFile);
        assertMessageReceived(expectedDoc);
    }

    protected void assertMessageReceived(Document aExpectedDoc) throws Exception {
        Document actual = XmlFixture.toDoc(getBody(0, String.class));
        assertMessageReceived(aExpectedDoc, actual);
    }

    protected void assertMessageReceived(Document aExpectedDoc, Document aActual) throws Exception,
            XPathExpressionException {
        Document noTime = XmlFixture.stripTimestamp(aActual);
        Document noUUID = XmlFixture.stripUUID(noTime);
        XmlFixture.assertXMLIgnorePrefix("failed to match",
                aExpectedDoc,
                noUUID);
        // assert that we have a timestamp and datetime
        // can't rely on the datetime being the same due to timezone differences
        // instead, we'll assert that the values exist.
        XPathFactory xpf = XPathFactory.newInstance();
        XPath xp = xpf.newXPath();
        xp.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String aArg0) {
                return "urn:org.apache.camel.component:jmx";
            }
            public String getPrefix(String aArg0) {
                return "jmx";
            }
            public Iterator<Object> getPrefixes(String aArg0) {
                return null;
            }
        });
        assertEquals("1", xp.evaluate("count(//jmx:timestamp)", aActual));
        assertEquals("1", xp.evaluate("count(//jmx:dateTime)", aActual));
        resetMockEndpoint();
    }

    /**
     * Resets the mock endpoint so we can run another test. This will clear out any
     * previously received messages.
     */
    protected void resetMockEndpoint() {
        getMockEndpoint().reset();
    }

    /**
     * Gets the body of the received message at the specified index
     */
    protected <T> T getBody(int aIndex, Class<T> aType) {
        Message in = getMessage(aIndex);
        T body = in.getBody(aType);
        assertNotNull(body);
        return body;
    }

    /**
     * Gets the received message at the specified index
     */
    protected Message getMessage(int aIndex) {
        Exchange exchange = getExchange(aIndex);
        Message in = exchange.getIn();
        return in;
    }

    /**
     * Gets the received exchange at the specified index
     */
    protected Exchange getExchange(int aIndex) {
        List<Exchange> exchanges = mMockEndpoint.getReceivedExchanges();
        Exchange exchange = exchanges.get(aIndex);
        return exchange;
    }
}
