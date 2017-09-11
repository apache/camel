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
package org.apache.camel.processor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.StringSource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.IOHelper;

/**
 * Tests the processing of a stream-cache in the multi-cast processor in the
 * parallel processing mode.
 */
public class MultiCastParallelAndStreamCachingTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setStreamCaching(true);
                context.getStreamCachingStrategy().setEnabled(true);
                context.getStreamCachingStrategy().setSpoolDirectory("target/camel/cache");
                context.getStreamCachingStrategy().setSpoolThreshold(5L);

                from("direct:start").multicast().parallelProcessing().stopOnException().to("direct:a", "direct:b").end().to("mock:result");

                from("direct:a") //
                        // read stream
                        .process(new SimpleProcessor(false)).to("mock:resulta");

                from("direct:b") //
                        // read stream concurrently, because of parallel processing
                        .process(new SimpleProcessor(true)).to("mock:resultb");

            }
        };
    }

    private static class SimpleProcessor implements Processor {

        private final boolean withSleepTime;

        SimpleProcessor(boolean withSleepTime) {
            this.withSleepTime = withSleepTime;
        }

        @Override
        public void process(Exchange exchange) throws Exception {

            if (withSleepTime) {
                // simulate some processing in order to get easier concurrency effects
                Thread.sleep(50);
            }
            Object body = exchange.getIn().getBody();
            if (body instanceof InputStream) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                IOHelper.copy((InputStream) body, output);
                exchange.getOut().setBody(output.toByteArray());
            } else if (body instanceof Reader) {
                Reader reader = (Reader) body;
                StringBuilder sb = new StringBuilder();
                for (int i = reader.read(); i > -1; i = reader.read()) {
                    sb.append((char) i);
                }
                reader.close();
                exchange.getOut().setBody(sb.toString());
            } else if (body instanceof StreamSource) {
                StreamSource ss = (StreamSource) body;
                if (ss.getInputStream() != null) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    IOHelper.copy(ss.getInputStream(), output);
                    exchange.getOut().setBody(output.toByteArray());
                } else if (ss.getReader() != null) {
                    Reader reader = ss.getReader();
                    StringBuilder sb = new StringBuilder();
                    for (int i = reader.read(); i > -1; i = reader.read()) {
                        sb.append((char) i);
                    }
                    reader.close();
                    exchange.getOut().setBody(sb.toString());
                } else {
                    throw new RuntimeException("StreamSource without InputStream and without Reader not supported");
                }
            } else {
                throw new RuntimeException("Type " + body.getClass().getName() + " not supported");
            }

        }
    }

    /**
     * Tests the ByteArrayInputStreamCache. The send byte array is transformed
     * to a ByteArrayInputStreamCache before the multi-cast processor is called.
     * 
     * @throws Exception
     */
    public void testByteArrayInputStreamCache() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resulta");
        mock.expectedBodiesReceived("<start></start>");
        mock = getMockEndpoint("mock:resultb");
        mock.expectedBodiesReceived("<start></start>");

        template.sendBody("direct:start", new ByteArrayInputStream("<start></start>".getBytes("UTF-8")));

        assertMockEndpointsSatisfied();
    }

    /**
     * Tests the FileInputStreamCache.
     * 
     * The sent input stream is transformed to FileInputStreamCache before the
     * multi-cast processor is called.
     * 
     * @throws Exception
     */
    public void testFileInputStreamCache() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resulta");
        mock.expectedBodiesReceived("James,Guillaume,Hiram,Rob,Roman");
        mock = getMockEndpoint("mock:resultb");
        mock.expectedBodiesReceived("James,Guillaume,Hiram,Rob,Roman");

        InputStream in = MultiCastParallelAndStreamCachingTest.class.getClassLoader().getResourceAsStream(
                "org/apache/camel/processor/simple.txt");
        template.sendBody("direct:start", in);

        assertMockEndpointsSatisfied();
    }

    /**
     * Tests the FileInputStreamCache.
     * 
     * The sent input stream is transformed to InputStreamCache before the
     * multi-cast processor is called.
     * 
     * @throws Exception
     */
    public void testInputStreamCache() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resulta");
        mock.expectedBodiesReceived("A");

        MockEndpoint mockb = getMockEndpoint("mock:resultb");
        mockb.expectedBodiesReceived("A");

        InputStream in = MultiCastParallelAndStreamCachingTest.class.getClassLoader().getResourceAsStream(
                "org/apache/camel/processor/oneCharacter.txt");
        // The body is only one character. Therefore InputStreamCache is used for stream caching
        template.sendBody("direct:start", in);

        assertMockEndpointsSatisfied();
    }

    /**
     * Tests the ReaderCache.
     * 
     * The sent InputStreamReader is transformed to a ReaderCache before the
     * multi-cast processor is called.
     * 
     * @throws Exception
     */
    public void testReaderCache() throws Exception {
        String abcScharpS = "ABC\u00DF"; // sharp-s

        MockEndpoint mock = getMockEndpoint("mock:resulta");
        mock.expectedBodiesReceived(abcScharpS);
        mock = getMockEndpoint("mock:resultb");
        mock.expectedBodiesReceived(abcScharpS);

        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(abcScharpS.getBytes("ISO-8859-1")), "ISO-8859-1");
        template.sendBody("direct:start", isr);

        assertMockEndpointsSatisfied();
    }

    public void testStreamSourceCacheWithInputStream() throws Exception {
        String input = "<A>a</A>";

        MockEndpoint mock = getMockEndpoint("mock:resulta");
        mock.expectedBodiesReceived(input);
        mock = getMockEndpoint("mock:resultb");
        mock.expectedBodiesReceived(input);

        StreamSource ss = new StreamSource(new ByteArrayInputStream(input.getBytes("UTF-8")));
        template.sendBody("direct:start", ss);

        assertMockEndpointsSatisfied();
    }
    
    public void testStreamSourceCacheWithReader() throws Exception {
        String input = "ABC\u00DF"; // sharp-s

        MockEndpoint mock = getMockEndpoint("mock:resulta");
        mock.expectedBodiesReceived(input);
        mock = getMockEndpoint("mock:resultb");
        mock.expectedBodiesReceived(input);

        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(input.getBytes("ISO-8859-1")), "ISO-8859-1");
        StreamSource ss = new StreamSource(isr);
        template.sendBody("direct:start", ss);

        assertMockEndpointsSatisfied();
    }

    public void testSourceCache() throws Exception {
        String input = "<A>a</A>";

        MockEndpoint mock = getMockEndpoint("mock:resulta");
        mock.expectedBodiesReceived(input);
        mock = getMockEndpoint("mock:resultb");
        mock.expectedBodiesReceived(input);

        StringSource ss = new StringSource(input);
        SAXSource saxSource = new SAXSource(SAXSource.sourceToInputSource(ss));
        template.sendBody("direct:start", saxSource);

        assertMockEndpointsSatisfied();
    }

}
