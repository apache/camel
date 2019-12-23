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
package org.apache.camel.dataformat.deflater;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test of the zip data format.
 */
public class ZipDeflaterDataFormatTest extends CamelTestSupport {
    private static final String TEXT = "The Cow in Apple Time \n" 
        + "by: Robert Frost \n\n" 
        + "Something inspires the only cow of late\n" 
        + "To make no more of a wall than an open gate,\n" 
        + "And think no more of wall-builders than fools.\n" 
        + "Her face is flecked with pomace and she drools\n" 
        + "A cider syrup. Having tasted fruit,\n" 
        + "She scorns a pasture withering to the root.\n" 
        + "She runs from tree to tree where lie and sweeten.\n" 
        + "The windfalls spiked with stubble and worm-eaten.\n" 
        + "She leaves them bitten when she has to fly.\n" 
        + "She bellows on a knoll against the sky.\n" 
        + "Her udder shrivels and the milk goes dry.";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testMarshalMandatoryConversionFailed() throws Exception {
        DataFormat dataFormat = new ZipDeflaterDataFormat();

        try {
            dataFormat.marshal(new DefaultExchange(context), new Object(), new ByteArrayOutputStream());
            fail("Should have thrown an exception");
        } catch (NoTypeConversionAvailableException e) {
            // expected
        }
    }

    @Test
    public void testMarshalTextToZipBestCompression() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().zipDeflater(Deflater.BEST_COMPRESSION)
                    .process(new ZippedMessageProcessor());
            }
        });
        context.start();

        sendText();
    }

    @Test
    public void testMarshalTextToZipBestSpeed() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().zipDeflater(Deflater.BEST_SPEED)
                    .process(new ZippedMessageProcessor());
            }
        });
        context.start();

        sendText();

    }

    @Test
    public void testMarshalTextToZipDefaultCompression() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().zipDeflater(Deflater.DEFAULT_COMPRESSION)
                    .process(new ZippedMessageProcessor());
            }
        });
        context.start();

        sendText();
    }

    @Test
    public void testUnMarshalTextToZip() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().zipDeflater()
                    .unmarshal().zipDeflater()
                    .to("mock:result");
            }
        });
        context.start();

        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived(TEXT);
        sendText();
        result.assertIsSatisfied();
        List<Exchange> exchangeList = result.getExchanges();
        assertTrue(exchangeList.get(0).getIn().getBody() instanceof byte[]);
    }

    @Test
    public void testStreamCacheUnzip() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .streamCaching()
                    .marshal().zipDeflater()
                    .unmarshal().zipDeflater()
                    .to("mock:result");
            }
        });
        context.start();

        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived(TEXT);
        sendText();
        result.assertIsSatisfied();
        List<Exchange> exchangeList = result.getExchanges();
        assertTrue(exchangeList.get(0).getIn().getBody() instanceof InputStreamCache);
    }

    private void sendText() throws Exception {
        template.sendBodyAndProperty("direct:start", TEXT, Exchange.CHARSET_NAME, "UTF-8");
    }

    private static class ZippedMessageProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            byte[] body = exchange.getIn().getBody(byte[].class);
            
            Inflater inflater = new Inflater();
            inflater.setInput(body);
            
            // Create an expandable byte array to hold the inflated data
            ByteArrayOutputStream bos = new ByteArrayOutputStream(body.length);
            
            // Inflate the compressed data
            byte[] buf = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                bos.write(buf, 0, count);
            }

            String result = new String(bos.toByteArray(), "UTF-8");
            
            // does the testing
            assertEquals(TEXT, result);
        }
    }    
}
