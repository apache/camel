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
package org.apache.camel.test.perf;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

public class SplitterPerformanceTest extends AbstractBasePerformanceTest {

    protected static final String HEADER = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soapenv:Header><routing xmlns=\"http://someuri\">xadmin;server1;community#1.0##</routing></soapenv:Header>"
            + "<soapenv:Body>"
            + "<m:buyStocks xmlns:m=\"http://services.samples/xsd\">";

    protected static final String BODY = "<order><symbol>IBM</symbol><buyerID>asankha</buyerID><price>140.34</price><volume>2000</volume></order>\n"
            + "<order><symbol>MSFT</symbol><buyerID>ruwan</buyerID><price>23.56</price><volume>8030</volume></order>\n"
            + "<order><symbol>SUN</symbol><buyerID>indika</buyerID><price>14.56</price><volume>500</volume></order>\n"
            + "<order><symbol>GOOG</symbol><buyerID>chathura</buyerID><price>60.24</price><volume>40000</volume></order>\n"
            + "<order><symbol>IBM</symbol><buyerID>asankha</buyerID><price>140.34</price><volume>2000</volume></order>\n"
            + "<order><symbol>MSFT</symbol><buyerID>ruwan</buyerID><price>23.56</price><volume>803000</volume></order>\n"
            + "<order><symbol>SUN</symbol><buyerID>indika</buyerID><price>14.56</price><volume>5000</volume></order>\n"
            + "<order><symbol>GOOG</symbol><buyerID>chathura</buyerID><price>60.24</price><volume>40000</volume></order>\n"
            + "<order><symbol>IBM</symbol><buyerID>asankha</buyerID><price>140.34</price><volume>2000</volume></order>\n"
            + "<order><symbol>MSFT</symbol><buyerID>ruwan</buyerID><price>23.56</price><volume>803000</volume></order>\n";

    protected static final String TRAILER = "</m:buyStocks>"
            + "</soapenv:Body>"
            + "</soapenv:Envelope>";

    protected static final String PAYLOAD;

    static {
        StringBuilder builder = new StringBuilder(HEADER);
        
        for (int i = 0; i < 2000; i++) {
            builder.append(BODY);
        }
        
        builder.append(TRAILER);
        PAYLOAD = builder.toString();
    }
    
    private final int count = 20001;

    @Test
    public void testTokenize() throws InterruptedException {
        template.setDefaultEndpointUri("direct:tokenize");

        // warm up with 1 message so that the JIT compiler kicks in
        execute(1);

        resetMock(count);

        StopWatch watch = new StopWatch();
        execute(1);

        assertMockEndpointsSatisfied();
        log.warn("Ran {} tests in {}ms", count, watch.taken());
    }

    @Override
    protected String getPayload() {
        return PAYLOAD;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:tokenize")
                    .split(body().tokenize("\n"))
                        .to("mock:end");
            }
        };
    }
}