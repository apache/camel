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
package org.apache.camel.processor.aggregator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.AggregatorType;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class AlbertoAggregatorTest extends ContextTestSupport {
    private static final String SURNAME_HEADER = "surname";
    private static final String TYPE_HEADER = "type";
    private static final String BROTHERS_TYPE = "brothers";
    private Log log = LogFactory.getLog(this.getClass());

    public void testAggregator() throws Exception {

        String allNames = "Harpo Marx,Fiodor Karamazov,Chico Marx,Ivan Karamazov,Groucho Marx,Alexei Karamazov,Dimitri Karamazov";

        List<String> marxBrothers = new ArrayList<String>();
        marxBrothers.add("Harpo");
        marxBrothers.add("Chico");
        marxBrothers.add("Groucho");

        List<String> karamazovBrothers = new ArrayList<String>();
        karamazovBrothers.add("Fiodor");
        karamazovBrothers.add("Ivan");
        karamazovBrothers.add("Alexei");
        karamazovBrothers.add("Dimitri");

        Map<String, List> allBrothers = new HashMap<String, List>();
        allBrothers.put("Marx", marxBrothers);
        allBrothers.put("Karamazov", karamazovBrothers);

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedBodiesReceived(allBrothers);

        ProducerTemplate template = context.createProducerTemplate();
        template.sendBody("direct:start", allNames);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            AggregationStrategy surnameAggregator = new AggregationStrategy() {
                public Exchange aggregate(Exchange oldExchange,
                        Exchange newExchange) {

                    debugIn("Surname Aggregator", oldExchange, newExchange);

                    Message oldIn = oldExchange.getIn();
                    Message newIn = newExchange.getIn();

                    List<String> brothers = null;
                    if (oldIn.getBody() instanceof List) {

                        brothers = oldIn.getBody(List.class);
                        brothers.add(newIn.getBody(String.class));
                    } else {

                        brothers = new ArrayList<String>();
                        brothers.add(oldIn.getBody(String.class));
                        brothers.add(newIn.getBody(String.class));
                        oldExchange.getIn().setBody(brothers);
                    } // else

                    debugOut("Surname Aggregator", oldExchange);

                    return oldExchange;
                }
            };
            AggregationStrategy brothersAggregator = new AggregationStrategy() {
                public Exchange aggregate(Exchange oldExchange,
                        Exchange newExchange) {

                    debugIn("Brothers Aggregator", oldExchange, newExchange);

                    Message oldIn = oldExchange.getIn();
                    Message newIn = newExchange.getIn();

                    Map<String, List> brothers = null;
                    if (oldIn.getBody() instanceof Map) {

                        brothers = oldIn.getBody(Map.class);
                        brothers.put(newIn.getHeader(SURNAME_HEADER,
                                String.class), newIn.getBody(List.class));
                    } else {

                        brothers = new HashMap<String, List>();
                        brothers.put(oldIn.getHeader(SURNAME_HEADER, String.class),
                                oldIn.getBody(List.class));
                        brothers.put(newIn.getHeader(SURNAME_HEADER,
                                String.class), newIn.getBody(List.class));
                        oldExchange.getIn().setBody(brothers);
                    } // else

                    debugOut("Brothers Aggregator", oldExchange);

                    return oldExchange;
                }
            };

            private void debugIn(String stringId, Exchange oldExchange,
                    Exchange newExchange) {

                log.debug(stringId + " old headers in: "
                        + oldExchange.getIn().getHeaders());
                log.debug(stringId + " old body in: "
                        + oldExchange.getIn().getBody());
                log.debug(stringId + " new headers in: "
                        + newExchange.getIn().getHeaders());
                log.debug(stringId + " new body in: "
                        + newExchange.getIn().getBody());
            }

            private void debugOut(String stringId, Exchange exchange) {

                log.debug(stringId + " old headers out: "
                        + exchange.getIn().getHeaders());
                log.debug(stringId + " old body out: "
                        + exchange.getIn().getBody());
            }

            @Override
            public void configure() throws Exception {

                from("direct:start")
                        // Separate people
                        .splitter(bodyAs(String.class).tokenize(",")).process(

                            // Split the name, erase the surname and put it in a
                            // header
                            new Processor() {
                                public void process(Exchange exchange) throws Exception {

                                    String[] parts = exchange.getIn()
                                            .getBody(String.class).split(
                                            " ");
                                    exchange.getIn().setBody(parts[0]);
                                    exchange.getIn().setHeader(
                                            SURNAME_HEADER, parts[1]);
                                } // process
                            }) // Processor

                        .to("direct:joinSurnames");

                from("direct:joinSurnames")
                        .aggregator(header(SURNAME_HEADER),
                                surnameAggregator).setHeader(TYPE_HEADER,
                        constant(BROTHERS_TYPE)).to("direct:joinBrothers");

                // Join all brothers lists and remove surname and type headers
                AggregatorType agg =
                        from("direct:joinBrothers").aggregator(header(TYPE_HEADER),
                                brothersAggregator);

                agg.setBatchTimeout(2000L);
                agg.removeHeader(SURNAME_HEADER)
                        .removeHeader(TYPE_HEADER)
                        .to("mock:result");
            }
        };
    }
}

