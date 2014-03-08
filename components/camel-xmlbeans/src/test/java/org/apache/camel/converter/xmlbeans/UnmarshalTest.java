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

import java.io.ByteArrayInputStream;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UnmarshalTest {

    private static final String PAYLOAD = "<m:buyStocks xmlns:m=\"http://services.samples/xsd\"><order><symbol>IBM</symbol><buyerID>cmueller</buyerID><price>140.34</price><volume>2000</volume>"
        + "</order></m:buyStocks>";
    private XmlBeansDataFormat dataFormat;
    private Exchange exchange;

    @Before
    public void setUp() {
        this.dataFormat = new XmlBeansDataFormat();
        this.exchange = new DefaultExchange(new DefaultCamelContext());
    }

    @Test
    public void unmarshal() throws Exception {
        Object result = dataFormat.unmarshal(exchange, new ByteArrayInputStream(PAYLOAD.getBytes()));

        assertBuyStocks(result);
    }

    @Test
    public void unmarshalConcurrent() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        final CountDownLatch latch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    Object result = dataFormat.unmarshal(exchange, new ByteArrayInputStream(PAYLOAD.getBytes()));

                    assertBuyStocks(result);
                    latch.countDown();

                    return null;
                }
            });
        }

        // make sure all results are checked and right
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    private void assertBuyStocks(Object result) {
        BuyStocks buyStocks = ((BuyStocksDocument) result).getBuyStocks();
        assertEquals(1, buyStocks.getOrderArray().length);
        assertEquals("IBM", buyStocks.getOrderArray(0).getSymbol());
        assertEquals("cmueller", buyStocks.getOrderArray(0).getBuyerID());
        assertEquals(140.34, buyStocks.getOrderArray(0).getPrice(), 0);
        assertEquals(2000, buyStocks.getOrderArray(0).getVolume());
    }
}