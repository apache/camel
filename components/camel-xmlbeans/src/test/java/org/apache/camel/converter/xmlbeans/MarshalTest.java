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
package org.apache.camel.converter.xmlbeans;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import samples.services.xsd.BuyStocksDocument;
import samples.services.xsd.BuyStocksDocument.BuyStocks;
import samples.services.xsd.Order;

import static org.junit.Assert.assertTrue;

public class MarshalTest {

    private XmlBeansDataFormat dataFormat;
    private Exchange exchange;

    @Before
    public void setUp() {
        this.dataFormat = new XmlBeansDataFormat();
        this.exchange = new DefaultExchange(new DefaultCamelContext());
    }

    @Test
    public void marshal() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        dataFormat.marshal(exchange, createBuyStocksDocument(), outputStream);

        assertBuyStocksXml(new String(outputStream.toByteArray()));
    }

    @Test
    public void marshalConcurrent() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        final CountDownLatch latch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                    dataFormat.marshal(exchange, createBuyStocksDocument(), outputStream);

                    assertBuyStocksXml(new String(outputStream.toByteArray()));
                    latch.countDown();

                    return null;
                }
            });
        }

        // make sure all results are checked and right
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    private void assertBuyStocksXml(String result) {
        assertTrue(result.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(result.contains("<xsd:buyStocks xmlns:xsd=\"http://services.samples/xsd\">"));
        assertTrue(result.contains("<order>"));
        assertTrue(result.contains("<symbol>IBM</symbol>"));
        assertTrue(result.contains("<buyerID>cmueller</buyerID>"));
        assertTrue(result.contains("<price>140.34</price>"));
        assertTrue(result.contains("volume>2000</volume>"));
        assertTrue(result.contains("</order>"));
        assertTrue(result.contains("</xsd:buyStocks>"));
    }

    private BuyStocksDocument createBuyStocksDocument() {
        BuyStocksDocument document = BuyStocksDocument.Factory.newInstance();
        BuyStocks payload = document.addNewBuyStocks();
        Order order = payload.addNewOrder();
        order.setSymbol("IBM");
        order.setBuyerID("cmueller");
        order.setPrice(140.34);
        order.setVolume(2000);

        return document;
    }
}