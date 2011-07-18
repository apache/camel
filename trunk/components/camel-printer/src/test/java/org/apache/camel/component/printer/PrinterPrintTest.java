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
package org.apache.camel.component.printer;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

public class PrinterPrintTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    // Check if there is an awt library
    private boolean isAwtHeadless() {
        return Boolean.getBoolean("java.awt.headless");
    }

    private void sendFile() throws Exception {
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                // Read from an input stream
                InputStream is = new BufferedInputStream(
                        new FileInputStream("./src/test/resources/test.txt"));

                byte buffer[] = new byte[is.available()];
                int n = is.available();
                for (int i = 0; i < n; i++) {
                    buffer[i] = (byte) is.read();
                }

                is.close();
                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(buffer);
            }
        });
    }

    private void sendGIF() throws Exception {
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                // Read from an input stream
                InputStream is = new BufferedInputStream(
                        new FileInputStream("./src/test/resources/asf-logo.gif"));

                byte buffer[] = new byte[is.available()];
                int n = is.available();
                for (int i = 0; i < n; i++) {
                    buffer[i] = (byte) is.read();
                }

                is.close();
                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(buffer);
            }
        });
    }

    private void sendJPEG() throws Exception {
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                // Read from an input stream
                InputStream is = new BufferedInputStream(
                        new FileInputStream("./src/test/resources/asf-logo.JPG"));

                byte buffer[] = new byte[is.available()];
                int n = is.available();
                for (int i = 0; i < n; i++) {
                    buffer[i] = (byte) is.read();
                }

                is.close();

                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(buffer);
            }
        });
    }

    @Test
    @Ignore
    public void testSendingFileToPrinter() throws Exception {
        if (isAwtHeadless()) {
            return;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").
                        to("lpr://localhost/default?copies=1&flavor=DocFlavor.BYTE_ARRAY&mimeType=AUTOSENSE&mediaSize=na-letter&sides=one-sided&sendToPrinter=false");
            }
        });
        context.start();

        sendFile();
    }

    @Test
    @Ignore
    public void testSendingGIFToPrinter() throws Exception {
        if (isAwtHeadless()) {
            return;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").
                        to("lpr://localhost/default?flavor=DocFlavor.INPUT_STREAM&mimeType=GIF&mediaSize=na-letter&sides=one-sided&sendToPrinter=false");
            }
        });
        context.start();

        sendGIF();
    }

    @Test
    @Ignore
    public void testSendingJPEGToPrinter() throws Exception {
        if (isAwtHeadless()) {
            return;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").to("lpr://localhost/default?copies=2&flavor=DocFlavor.INPUT_STREAM"
                        + "&mimeType=JPEG&mediaSize=na-letter&sides=one-sided&sendToPrinter=false");
            }
        });
        context.start();

        sendJPEG();
    }

    /**
     * Test for resolution of bug CAMEL-3446.
     * Not specifying mediaSize nor sides attributes make it use
     * default values when starting the route.
     */
    @Test
    @Ignore
    public void testDefaultPrinterConfiguration() throws Exception {
        if (isAwtHeadless()) {
            return;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").to("lpr://localhost/default?sendToPrinter=false");
            }
        });
        context.start();
    }
}
