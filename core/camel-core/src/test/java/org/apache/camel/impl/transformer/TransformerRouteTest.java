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
package org.apache.camel.impl.transformer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Converter;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.TypeConverters;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeAware;
import org.apache.camel.spi.Transformer;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultDataFormat;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A TransformerTest demonstrates contract based declarative transformation via
 * Java DSL.
 */
public class TransformerRouteTest extends ContextTestSupport {

    protected static final Logger LOG = LoggerFactory.getLogger(TransformerRouteTest.class);

    @Test
    public void testJavaTransformer() throws Exception {
        MockEndpoint abcresult = getMockEndpoint("mock:abcresult");
        abcresult.expectedMessageCount(1);
        abcresult.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                LOG.info("Asserting String -> XOrderResponse convertion");
                assertEquals(XOrderResponse.class, exchange.getIn().getBody().getClass());
            }

        });

        MockEndpoint xyzresult = getMockEndpoint("mock:xyzresult");
        xyzresult.expectedMessageCount(1);
        xyzresult.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                LOG.info("Asserting String -> XOrderResponse convertion is not yet performed");
                assertEquals("response", exchange.getIn().getBody());
            }
        });

        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setBody(new AOrder());
        Exchange answerEx = template.send("direct:abc", exchange);
        if (answerEx.getException() != null) {
            throw answerEx.getException();
        }
        assertEquals(AOrderResponse.class, answerEx.getMessage().getBody().getClass());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDataFormatTransformer() throws Exception {
        MockEndpoint xyzresult = getMockEndpoint("mock:xyzresult");
        xyzresult.expectedMessageCount(1);
        xyzresult.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                LOG.info("Asserting String -> XOrderResponse convertion is not yet performed");
                assertEquals("response", exchange.getIn().getBody());
            }
        });

        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        ((DataTypeAware)exchange.getIn()).setBody("{name:XOrder}", new DataType("json:JsonXOrder"));
        Exchange answerEx = template.send("direct:dataFormat", exchange);
        if (answerEx.getException() != null) {
            throw answerEx.getException();
        }
        assertEquals("{name:XOrderResponse}", answerEx.getMessage().getBody(String.class));
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEndpointTransformer() throws Exception {
        MockEndpoint xyzresult = getMockEndpoint("mock:xyzresult");
        xyzresult.expectedMessageCount(1);
        xyzresult.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                LOG.info("Asserting String -> XOrderResponse convertion is not yet performed");
                assertEquals("response", exchange.getIn().getBody());
            }
        });

        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setBody("<XOrder/>");
        Exchange answerEx = template.send("direct:endpoint", exchange);
        if (answerEx.getException() != null) {
            throw answerEx.getException();
        }
        assertEquals("<XOrderResponse/>", answerEx.getMessage().getBody(String.class));
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCustomTransformer() throws Exception {
        MockEndpoint xyzresult = getMockEndpoint("mock:xyzresult");
        xyzresult.expectedMessageCount(1);
        xyzresult.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                LOG.info("Asserting String -> XOrderResponse convertion is not yet performed");
                assertEquals("response", exchange.getIn().getBody());
            }
        });

        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setBody("name=XOrder");
        Exchange answerEx = template.send("direct:custom", exchange);
        if (answerEx.getException() != null) {
            throw answerEx.getException();
        }
        assertEquals("name=XOrderResponse", answerEx.getMessage().getBody(String.class));
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getTypeConverterRegistry().addTypeConverters(new MyTypeConverters());
                from("direct:abc").inputType(AOrder.class).outputType(AOrderResponse.class).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        LOG.info("Asserting input -> AOrder convertion");
                        assertEquals(AOrder.class, exchange.getIn().getBody().getClass());
                    }
                }).inOut("direct:xyz").to("mock:abcresult");

                from("direct:xyz").inputType(XOrder.class).outputType(XOrderResponse.class).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        LOG.info("Asserting input -> XOrder convertion");
                        assertEquals(XOrder.class, exchange.getIn().getBody().getClass());
                        exchange.getIn().setBody("response");
                    }
                }).to("mock:xyzresult");

                transformer().scheme("json").withDataFormat(new MyJsonDataFormatDefinition());
                from("direct:dataFormat").inputType("json:JsonXOrder").outputType("json:JsonXOrderResponse").inOut("direct:xyz");

                context.addComponent("myxml", new MyXmlComponent());
                transformer().fromType("xml:XmlXOrder").toType(XOrder.class).withUri("myxml:endpoint");
                transformer().fromType(XOrderResponse.class).toType("xml:XmlXOrderResponse").withUri("myxml:endpoint");
                from("direct:endpoint").inputType("xml:XmlXOrder").outputType("xml:XmlXOrderResponse").inOut("direct:xyz");

                transformer().fromType("other:OtherXOrder").toType(XOrder.class).withJava(OtherToXOrderTransformer.class);
                transformer().fromType(XOrderResponse.class).toType("other:OtherXOrderResponse").withJava(XOrderResponseToOtherTransformer.class);
                from("direct:custom").inputType("other:OtherXOrder").outputType("other:OtherXOrderResponse").inOut("direct:xyz");
            }
        };
    }

    public static class MyTypeConverters implements TypeConverters {
        @Converter
        public AOrder toAOrder(String order) {
            LOG.info("TypeConverter: String -> AOrder");
            return new AOrder();
        }

        @Converter
        public XOrder toXOrder(AOrder aorder) {
            LOG.info("TypeConverter: AOrder -> XOrder");
            return new XOrder();
        }

        @Converter
        public XOrderResponse toXOrderResponse(String res) {
            LOG.info("TypeConverter: String -> XOrderResponse");
            return new XOrderResponse();
        }

        @Converter
        public AOrderResponse toAOrderResponse(XOrderResponse xres) {
            LOG.info("TypeConverter: XOrderResponse -> AOrderResponse");
            return new AOrderResponse();
        }
    }

    public static class MyJsonDataFormatDefinition extends DataFormatDefinition {

        public MyJsonDataFormatDefinition() {
            super(new DefaultDataFormat() {
                @Override
                public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
                    assertEquals(graph.toString(), XOrderResponse.class, graph.getClass());
                    LOG.info("DataFormat: XOrderResponse -> JSON");
                    stream.write("{name:XOrderResponse}".getBytes());
                }

                @Override
                public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                    String line = "";
                    String input = "";
                    while ((line = reader.readLine()) != null) {
                        input += line;
                    }
                    reader.close();
                    assertEquals("{name:XOrder}", input);
                    LOG.info("DataFormat: JSON -> XOrder");
                    return new XOrder();
                }
            });
        }
    }

    public static class MyXmlComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new MyXmlEndpoint();
        }
    }

    public static class MyXmlEndpoint extends DefaultEndpoint {
        @Override
        public Producer createProducer() throws Exception {
            return new DefaultAsyncProducer(this) {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    Object input = exchange.getIn().getBody();
                    if (input instanceof XOrderResponse) {
                        LOG.info("Endpoint: XOrderResponse -> XML");
                        exchange.getIn().setBody("<XOrderResponse/>");
                    } else {
                        assertEquals("<XOrder/>", input);
                        LOG.info("Endpoint: XML -> XOrder");
                        exchange.getIn().setBody(new XOrder());

                    }
                    callback.done(true);
                    return true;
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        @Override
        public boolean isSingleton() {
            return false;
        }

        @Override
        protected String createEndpointUri() {
            return "myxml:endpoint";
        }
    }

    public static class OtherToXOrderTransformer extends Transformer {
        @Override
        public void transform(Message message, DataType from, DataType to) throws Exception {
            assertEquals("name=XOrder", message.getBody());
            LOG.info("Bean: Other -> XOrder");
            message.setBody(new XOrder());
        }
    }

    public static class XOrderResponseToOtherTransformer extends Transformer {
        @Override
        public void transform(Message message, DataType from, DataType to) throws Exception {
            LOG.info("Bean: XOrderResponse -> Other");
            message.setBody("name=XOrderResponse");
        }
    }

    public static class AOrder {
    }

    public static class AOrderResponse {
    }

    public static class XOrder {
    }

    public static class XOrderResponse {
    }
}
