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
package org.apache.camel.component.yql;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpStatus;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.apache.camel.component.yql.YqlProducer.CAMEL_YQL_HTTP_REQUEST;
import static org.apache.camel.component.yql.YqlProducer.CAMEL_YQL_HTTP_STATUS;
import static org.hamcrest.CoreMatchers.containsString;

public class YqlComponentIntegrationTest extends CamelTestSupport {

    private static final String FINANCE_QUERY = "select symbol, Ask, Bid,  from yahoo.finance.quotes where symbol in ('GOOG')";
    private static final String WEATHER_QUERY = "select wind, atmosphere from weather.forecast where woeid in (select woeid from geo.places(1) where text='chicago, il')";
    private static final String BOOK_QUERY = "select * from google.books where q='barack obama' and maxResults=1";
    private static final String CALLBACK = "yqlCallback";
    private static final String ENV = "store://datatables.org/alltableswithkeys";

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Produce(uri = "direct:startFinance")
    private ProducerTemplate templateFinance;

    @EndpointInject(uri = "mock:resultFinance")
    private MockEndpoint endFinance;

    @Produce(uri = "direct:startWeather")
    private ProducerTemplate templateWeather;

    @EndpointInject(uri = "mock:resultWeather")
    private MockEndpoint endWeather;

    @Produce(uri = "direct:startBook")
    private ProducerTemplate templateBook;

    @EndpointInject(uri = "mock:resultBook")
    private MockEndpoint endBook;

    @Produce(uri = "direct:startFail")
    private ProducerTemplate templateFail;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:startFinance")
                        .to("yql://" + FINANCE_QUERY + "?format=json&callback=" + CALLBACK + "&https=false&env=" + ENV)
                        .to("mock:resultFinance");

                from("direct:startWeather")
                        .to("yql://" + WEATHER_QUERY)
                        .to("mock:resultWeather");

                from("direct:startBook")
                        .to("yql://" + BOOK_QUERY + "?format=xml&crossProduct=optimized&env=" + ENV)
                        .to("mock:resultBook");

                from("direct:startFail")
                        .to("yql://" + FINANCE_QUERY)
                        .to("mock:resultBook");
            }
        };
    }

    @Test
    public void testFinanceQuote() throws UnsupportedEncodingException {
        // when
        templateFinance.sendBody("");

        // then
        final Exchange exchange = endFinance.getReceivedExchanges().get(0);
        final String body = exchange.getIn().getBody(String.class);
        final Integer status = exchange.getIn().getHeader(CAMEL_YQL_HTTP_STATUS, Integer.class);
        final String httpRequest = exchange.getIn().getHeader(CAMEL_YQL_HTTP_REQUEST, String.class);
        assertThat(httpRequest, containsString("http"));
        assertThat(httpRequest, containsString("q=" + URLEncoder.encode(FINANCE_QUERY, "UTF-8")));
        assertThat(httpRequest, containsString("format=json"));
        assertThat(httpRequest, containsString("callback=" + CALLBACK));
        assertThat(httpRequest, containsString("diagnostics=false"));
        assertThat(httpRequest, containsString("debug=false"));
        assertThat(httpRequest, containsString("env=" + URLEncoder.encode(ENV, "UTF-8")));
        assertNotNull(body);
        assertThat(body, containsString(CALLBACK + "("));
        assertEquals(HttpStatus.SC_OK, status.intValue());
    }

    @Test
    public void testWeather() throws UnsupportedEncodingException {
        // when
        templateWeather.sendBody("");

        // then
        final Exchange exchange = endWeather.getReceivedExchanges().get(0);
        final String body = exchange.getIn().getBody(String.class);
        final Integer status = exchange.getIn().getHeader(CAMEL_YQL_HTTP_STATUS, Integer.class);
        final String httpRequest = exchange.getIn().getHeader(CAMEL_YQL_HTTP_REQUEST, String.class);
        assertThat(httpRequest, containsString("https"));
        assertThat(httpRequest, containsString("q=" + URLEncoder.encode(WEATHER_QUERY, "UTF-8")));
        assertThat(httpRequest, containsString("format=json"));
        assertThat(httpRequest, containsString("diagnostics=false"));
        assertThat(httpRequest, containsString("debug=false"));
        assertNotNull(body);
        assertEquals(HttpStatus.SC_OK, status.intValue());
    }

    @Test
    public void testBook() throws UnsupportedEncodingException {
        // when
        templateBook.sendBody("");

        // then
        final Exchange exchange = endBook.getReceivedExchanges().get(0);
        final String body = exchange.getIn().getBody(String.class);
        final Integer status = exchange.getIn().getHeader(CAMEL_YQL_HTTP_STATUS, Integer.class);
        final String httpRequest = exchange.getIn().getHeader(CAMEL_YQL_HTTP_REQUEST, String.class);
        assertThat(httpRequest, containsString("https"));
        assertThat(httpRequest, containsString("q=" + URLEncoder.encode(BOOK_QUERY, "UTF-8")));
        assertThat(httpRequest, containsString("format=xml"));
        assertThat(httpRequest, containsString("diagnostics=false"));
        assertThat(httpRequest, containsString("debug=false"));
        assertThat(httpRequest, containsString("crossProduct=optimized"));
        assertNotNull(body);
        assertEquals(HttpStatus.SC_OK, status.intValue());
    }

    @Test
    public void testFail() throws UnsupportedEncodingException {
        // then
        thrown.expect(CamelExecutionException.class);

        // when
        templateFail.sendBody("");
    }
}
