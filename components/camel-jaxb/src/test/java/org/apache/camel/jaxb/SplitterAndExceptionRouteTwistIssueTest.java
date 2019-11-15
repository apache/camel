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
package org.apache.camel.jaxb;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 *
 */
public class SplitterAndExceptionRouteTwistIssueTest extends CamelTestSupport {

    @Produce("direct:error")
    protected ProducerTemplate templateError;

    @Produce("direct:error2")
    protected ProducerTemplate templateError2;

    @EndpointInject("mock:mockReject")
    protected MockEndpoint mockRejectEndpoint;

    @EndpointInject("mock:mock_output")
    protected MockEndpoint mockOutput;

    @Test
    public void testErrorHandlingJaxb() throws Exception {
        String correctExample = "abcdef";
        String errorExample = "myerror\u0010";

        mockRejectEndpoint.expectedMessageCount(1);
        mockOutput.expectedMessageCount(4);

        templateError.sendBody(correctExample);
        templateError.sendBody(errorExample);
        templateError.sendBody(correctExample);
        templateError.sendBody(correctExample);
        templateError.sendBody(correctExample);

        mockRejectEndpoint.assertIsSatisfied();
        mockOutput.assertIsSatisfied();
    }

    @Test
    public void testErrorHandlingPlumber() throws Exception {
        String correctExample = "abcdef";
        String errorExample = "myerror\u0010";

        mockRejectEndpoint.expectedMessageCount(1);
        mockOutput.expectedMessageCount(4);

        templateError2.sendBody(correctExample);
        templateError2.sendBody(errorExample);
        templateError2.sendBody(correctExample);
        templateError2.sendBody(correctExample);
        templateError2.sendBody(correctExample);

        mockRejectEndpoint.assertIsSatisfied();
        mockOutput.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {

                errorHandler(
                    deadLetterChannel(mockRejectEndpoint)
                        .useOriginalMessage()
                        .maximumRedeliveries(0)
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .logExhausted(true)
                        .logStackTrace(true)
                        .logRetryStackTrace(true)
                );

                from("direct:error")
                    .convertBodyTo(String.class, "UTF-8")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            String text = (String) exchange.getIn().getBody();
                            Twits twits = new Twits();

                            Twit twit1 = new Twit();
                            twit1.setText(text);
                            twits.getTwits().add(twit1);

                            exchange.getIn().setBody(twits);
                        }
                    })
                    .split().xpath("//twits/twit").streaming()
                    .to(mockOutput);


                from("direct:error2")
                    .convertBodyTo(String.class, "UTF-8")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            String text = (String) exchange.getIn().getBody();

                            StringBuilder twits = new StringBuilder();
                            twits.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");

                            twits.append("<twits>");
                            twits.append("<twit>");
                            twits.append(text);
                            twits.append("</twit>");
                            twits.append("</twits>");

                            exchange.getIn().setBody(twits.toString());
                        }
                    })
                    .split().xpath("//twits/twit").streaming()
                    .to(mockOutput);
            }
        };
    }
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {"twits"})
@XmlRootElement(name = "twits")
class Twits implements Serializable {

    private static final long serialVersionUID = 1L;

    @XmlElement(name = "twit", required = true)
    protected List<Twit> twits;

    public List<Twit> getTwits() {
        if (twits == null) {
            twits = new ArrayList<>();
        }
        return this.twits;
    }

    @Override
    public String toString() {
        if (twits == null || twits.isEmpty()) {
            return super.toString();
        }
        return super.toString() + "[" + twits.get(0).toString() + "]";
    }
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Twit", propOrder = {"text"})
@XmlRootElement(name = "twit")
class Twit implements Serializable {

    private static final long serialVersionUID = 1L;

    @XmlElement(required = true)
    protected String text;

    public String getText() {
        return text;
    }

    public void setText(String value) {
        this.text = value;
    }

    @Override
    public String toString() {
        return text;
    }
}



