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
package org.apache.camel.component.smooks;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.smooks.Smooks;
import org.smooks.SmooksFactory;
import org.smooks.api.ExecutionContext;
import org.smooks.api.NotAppContextScoped;
import org.smooks.cartridges.edi.parser.EdiParser;
import org.smooks.cartridges.javabean.Bean;
import org.smooks.cartridges.javabean.Value;
import org.smooks.engine.lookup.InstanceLookup;
import org.smooks.io.payload.Exports;
import org.smooks.io.sink.JavaSink;
import org.smooks.io.sink.StringSink;
import org.smooks.io.source.StreamSource;
import org.smooks.io.source.StringSource;
import org.smooks.support.StreamUtils;
import org.xmlunit.builder.DiffBuilder;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SmooksProcessorTest extends CamelTestSupport {

    @EndpointInject(value = "mock:result")
    private MockEndpoint result;

    public SmooksProcessorTest() {
        super();
        testConfigurationBuilder.withUseRouteBuilder(false);
        testConfigurationBuilder.withJMX(true);
    }

    private void assertOneProcessedMessage() throws Exception {
        result.expectedMessageCount(1);
        template.sendBody("direct://input", getOrderEdi());

        assertPostCondition();
    }

    private void assertPostCondition() throws InterruptedException, IOException {
        assertIsSatisfied();

        Exchange exchange = result.assertExchangeReceived(0);
        assertNotNull(exchange.getMessage().getHeader(SmooksConstants.SMOOKS_EXECUTION_CONTEXT, ExecutionContext.class));
        assertIsInstanceOf(InputStreamCache.class, exchange.getIn().getBody());
        assertFalse(DiffBuilder.compare(getExpectedOrderXml()).withTest(exchange.getIn().getBody(String.class)).ignoreComments()
                .ignoreWhitespace().build().hasDifferences());
    }

    @Test
    public void testProcess() throws Exception {
        context.addRoutes(createEdiToXmlRouteBuilder());
        context.start();
        assertOneProcessedMessage();
    }

    @Test
    public void testProcessUsesExistingExecutionContextWhenExecutionContextIsInHeaderAndAllowExecutionContextFromHeaderIsTrue()
            throws Exception {
        Smooks smooks = new Smooks();
        SmooksProcessor processor = new SmooksProcessor("edi-to-xml-smooks-config.xml", context);
        processor.setSmooksFactory(new SmooksFactory() {
            @Override
            public Smooks createInstance() {
                return smooks;
            }

            @Override
            public Smooks createInstance(InputStream config) {
                return null;
            }

            @Override
            public Smooks createInstance(String config) {
                return null;
            }
        });
        processor.setAllowExecutionContextFromHeader(true);

        final ExecutionContext[] executionContext = new ExecutionContext[1];
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:input")
                        .setHeader(SmooksConstants.SMOOKS_EXECUTION_CONTEXT, () -> {
                            executionContext[0] = smooks.createExecutionContext();
                            return executionContext[0];
                        })
                        .process(processor)
                        .to("mock:result");
            }

        });
        context.start();
        template.sendBody("direct://input", getOrderEdi());

        Exchange exchange = result.assertExchangeReceived(0);
        assertEquals(executionContext[0], exchange.getMessage().getHeader(SmooksConstants.SMOOKS_EXECUTION_CONTEXT));
    }

    @Test
    public void testProcessDoesNotUseExistingExecutionContextWhenExecutionContextIsInHeaderAndAllowExecutionContextFromHeaderIsFalse()
            throws Exception {
        Smooks smooks = new Smooks();
        SmooksProcessor processor = new SmooksProcessor("edi-to-xml-smooks-config.xml", context);
        processor.setSmooksFactory(new SmooksFactory() {
            @Override
            public Smooks createInstance() {
                return smooks;
            }

            @Override
            public Smooks createInstance(InputStream config) {
                return null;
            }

            @Override
            public Smooks createInstance(String config) {
                return null;
            }
        });
        processor.setAllowExecutionContextFromHeader(false);

        final ExecutionContext[] executionContext = new ExecutionContext[1];
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:input")
                        .setHeader(SmooksConstants.SMOOKS_EXECUTION_CONTEXT, () -> {
                            executionContext[0] = smooks.createExecutionContext();
                            return executionContext[0];
                        })
                        .process(processor)
                        .to("mock:result");
            }

        });
        context.start();
        template.sendBody("direct://input", getOrderEdi());

        Exchange exchange = result.assertExchangeReceived(0);
        assertNotEquals(executionContext[0], exchange.getMessage().getHeader(SmooksConstants.SMOOKS_EXECUTION_CONTEXT));
    }

    @Test
    public void testProcessWhenLazyStartSmooksIsFalse()
            throws Exception {
        Smooks smooks = new Smooks();
        SmooksProcessor processor = new SmooksProcessor("edi-to-xml-smooks-config.xml", context);
        processor.setSmooksFactory(new SmooksFactory() {
            @Override
            public Smooks createInstance() {
                return smooks;
            }

            @Override
            public Smooks createInstance(InputStream config) {
                return null;
            }

            @Override
            public Smooks createInstance(String config) {
                return null;
            }
        });
        processor.setLazyStartSmooks(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:input")
                        .process(processor);
            }

        });
        context.start();
        assertEquals(1, smooks.getApplicationContext().getRegistry().lookup(new InstanceLookup<>(EdiParser.class)).size());
    }

    @Test
    public void testProcessWhenLazyStartSmooksIsTrue()
            throws Exception {
        Smooks smooks = new Smooks();
        SmooksProcessor processor = new SmooksProcessor("edi-to-xml-smooks-config.xml", context);
        processor.setSmooksFactory(new SmooksFactory() {
            @Override
            public Smooks createInstance() {
                return smooks;
            }

            @Override
            public Smooks createInstance(InputStream config) {
                return null;
            }

            @Override
            public Smooks createInstance(String config) {
                return null;
            }
        });
        processor.setLazyStartSmooks(true);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:input")
                        .process(processor);
            }

        });
        context.start();
        assertEquals(0, smooks.getApplicationContext().getRegistry().lookup(new InstanceLookup<>(EdiParser.class)).size());
    }

    @Test
    public void testProcessGivenAttachment() throws Exception {
        context.addRoutes(createEdiToXmlRouteBuilder());
        context.start();
        final DefaultExchange exchange = new DefaultExchange(context);
        final String attachmentContent = "A dummy attachment";
        final String attachmentId = "testAttachment";
        addAttachment(attachmentContent, attachmentId, exchange);
        exchange.getIn().setBody(getOrderEdi());

        template.send("direct://input", exchange);

        final DataHandler datahandler
                = result.assertExchangeReceived(0).getIn(AttachmentMessage.class).getAttachment(attachmentId);
        assertThat(datahandler, is(notNullValue()));
        assertThat(datahandler.getContent(), is(instanceOf(ByteArrayInputStream.class)));

        final String actualAttachmentContent = getAttachmentContent(datahandler);
        assertThat(actualAttachmentContent, is(equalTo(attachmentContent)));
    }

    @Test
    public void testProcessCreatesSmooksReport() throws Exception {
        context.addRoutes(createEdiToXmlRouteBuilder());
        context.start();
        assertOneProcessedMessage();

        File report = new File("target/smooks-report.html");
        report.deleteOnExit();
        assertTrue(report.exists(), "Smooks report was not generated.");
    }

    @Test
    public void testProcessGivenCamelCharsetNameProperty() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                Smooks smooks = new Smooks().setExports(new Exports(JavaSink.class));
                from("direct:a")
                        .process(new SmooksProcessor(smooks, context)
                                .addVisitor(new Value(
                                        "customer", "/order/header/customer", String.class,
                                        smooks.getApplicationContext().getRegistry())));
            }

        });
        enableJMX();
        context.start();
        Exchange response = template.request("direct:a", new Processor() {
            public void process(Exchange exchange) {
                InputStream in = this.getClass().getResourceAsStream("/EBCDIC-input-message.txt");
                exchange.getIn().setBody(new StreamSource(in));
                exchange.setProperty("CamelCharsetName", "Cp1047");
            }
        });
        assertEquals("Joe", response.getMessage().getBody(String.class));
    }

    @Test
    public void testProcessWhenBodyIsByteArray() throws Exception {
        context.addRoutes(createEdiToXmlRouteBuilder());
        context.start();

        result.expectedMessageCount(1);
        template.sendBody("direct://input", getOrderEdi().getBytes());

        assertPostCondition();
    }

    @Test
    public void testProcessWhenBodyIsNotSource() throws Exception {
        context.addRoutes(createEdiToXmlRouteBuilder());
        context.start();

        RuntimeException runtimeException
                = assertThrows(RuntimeException.class, () -> template.sendBody("direct://input", new Object()));
        assertEquals(InvalidPayloadException.class, runtimeException.getCause().getCause().getClass());
    }

    @Test
    public void testRegisteredCamelContextIsNotAppContextScoped() throws Exception {
        context.addRoutes(createEdiToXmlRouteBuilder());
        context.start();

        result.expectedMessageCount(1);
        template.sendBody("direct://input", getOrderEdi());

        Exchange exchange = result.assertExchangeReceived(0);
        ExecutionContext executionContext
                = exchange.getMessage().getHeader(SmooksConstants.SMOOKS_EXECUTION_CONTEXT, ExecutionContext.class);
        Object camelContextRef
                = executionContext.getApplicationContext().getRegistry()
                        .lookup(registryEntries -> registryEntries.entrySet().stream()
                                .filter(e -> e.getKey().equals(CamelContext.class)).findFirst().get().getValue());
        assertInstanceOf(NotAppContextScoped.Ref.class, camelContextRef);

    }

    @Test
    public void testProcessWhenBodyIsFileAndSmooksExportIsStringSink() throws Exception {
        deleteDirectory("target/smooks");
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("file://target/smooks")
                        .process(new SmooksProcessor(new Smooks().setExports(new Exports(StringSink.class)), context))
                        .to("mock:a");
            }
        });
        context.start();
        template.sendBody("file://target/smooks", "<blah />");

        MockEndpoint mockEndpoint = getMockEndpoint("mock:a");
        mockEndpoint.expectedMessageCount(1);

        assertIsSatisfied(mockEndpoint);

        assertEquals("<blah/>", mockEndpoint.getExchanges().get(0).getIn().getBody(String.class));
    }

    @Test
    public void testProcessWhenSmooksExportIsJavaSinkAndBodyIsVisitedByJavaBeanValue() throws Exception {
        Smooks smooks = new Smooks().setExports(new Exports(JavaSink.class));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a")
                        .process(new SmooksProcessor(smooks, context)
                                .addVisitor(new Value(
                                        "x", "/coord/@x", Integer.class, smooks.getApplicationContext().getRegistry())));
            }
        });
        enableJMX();
        context.start();
        Exchange response
                = template.request("direct:a", exchange -> exchange.getIn().setBody(new StringSource("<coord x='1234' />")));
        assertEquals(1234, response.getMessage().getBody(Integer.class));
    }

    @Test
    public void testProcessWhenSmooksExportIsJavaSinkAndBodyIsVisitedByMultipleJavaBeanValues() throws Exception {
        Smooks smooks = new Smooks().setExports(new Exports(JavaSink.class));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:b").process(new SmooksProcessor(smooks, context)
                        .addVisitor(new Value("x", "/coord/@x", Integer.class, smooks.getApplicationContext().getRegistry()))
                        .addVisitor(new Value("y", "/coord/@y", Double.class, smooks.getApplicationContext().getRegistry())));
            }
        });
        context.start();
        Exchange response = template.request("direct:b",
                exchange -> exchange.getIn().setBody(new StringSource("<coord x='1234' y='98765.76' />")));
        Map javaResult = response.getOut().getBody(Map.class);
        Integer x = (Integer) javaResult.get("x");
        assertEquals(1234, (int) x);
        Double y = (Double) javaResult.get("y");
        assertEquals(98765.76D, y, 0.01D);
    }

    @Test
    public void testProcessWhenSmooksExportIsJavaSinkAndBodyIsVisitedByBean() throws Exception {
        Smooks smooks = new Smooks().setExports(new Exports(JavaSink.class));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:c").process(new SmooksProcessor(smooks, context)
                        .addVisitor(new Bean(Coordinate.class, "coordinate", smooks.getApplicationContext().getRegistry())
                                .bindTo("x", "/coord/@x").bindTo("y", "/coord/@y")));
            }
        });
        context.start();
        Exchange response = template.request("direct:c",
                exchange -> exchange.getIn().setBody(new StringSource("<coord x='111' y='222' />")));

        Coordinate coord = response.getMessage().getBody(Coordinate.class);

        assertEquals((Integer) 111, coord.getX());
        assertEquals((Integer) 222, coord.getY());
    }

    @Test
    public void testProcessWhenSmooksExportIsStringSink() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:a")
                        .process(new SmooksProcessor(new Smooks().setExports(new Exports(StringSink.class)), context))
                        .to("direct:b");

                from("direct:b").convertBodyTo(String.class).process(new DirectBProcessor());
            }
        });
        context.start();
        template.request("direct:a", exchange -> exchange.getIn().setBody(new StringSource("<x/>")));

        assertEquals("<x/>", DirectBProcessor.inMessage);
    }

    private static class DirectBProcessor implements Processor {
        private static String inMessage;

        public void process(Exchange exchange) {
            inMessage = (String) exchange.getIn().getBody();
        }
    }

    private void addAttachment(final String attachment, final String id, final Exchange exchange) {
        final DataSource ds = new StringDataSource(attachment);
        final DataHandler dataHandler = new DataHandler(ds);
        exchange.getIn(AttachmentMessage.class).addAttachment(id, dataHandler);
    }

    private String getAttachmentContent(final DataHandler datahandler) throws IOException {
        final ByteArrayInputStream bs = (ByteArrayInputStream) datahandler.getContent();
        return new String(StreamUtils.readStream(bs));
    }

    protected RouteBuilder createEdiToXmlRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                SmooksProcessor processor = new SmooksProcessor("edi-to-xml-smooks-config.xml", context);
                processor.setReportPath("target/smooks-report.html");

                from("direct:input").process(processor).to("mock:result");
            }
        };
    }

    private String getExpectedOrderXml() throws IOException {
        return StreamUtils.readStream(new InputStreamReader(getClass().getResourceAsStream("/xml/expected-order.xml")));
    }

    private String getOrderEdi() throws IOException {
        return StreamUtils.readStream(new InputStreamReader(getClass().getResourceAsStream("/data/order.txt")));
    }

    private static class StringDataSource implements DataSource {
        private final String string;

        private StringDataSource(final String string) {
            this.string = string;

        }

        public String getContentType() {
            return "text/plain";
        }

        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(string.getBytes());
        }

        public String getName() {
            return "StringDataSource";
        }

        public OutputStream getOutputStream() throws IOException {
            throw new IOException("Method 'getOutputStream' is not implemented");
        }

    }
}
