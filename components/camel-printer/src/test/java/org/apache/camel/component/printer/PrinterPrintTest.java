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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

public class PrinterPrintTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
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
                    buffer[i] = (byte)is.read();
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
                    buffer[i] = (byte)is.read();
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
                    buffer[i] = (byte)is.read();
                }
                
                is.close();
                
                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(buffer);
            }            
        });       
    }

    public void testSendingFileToPrinter() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").
                    to("lpr://localhost/default?copies=1&flavor=DocFlavor.BYTE_ARRAY&mimeType=AUTOSENSE&mediaSize=na-letter&sides=one-sided&sendToPrinter=false");
            }
        });
        context.start();

        sendFile();
    }
    
    public void testSendingGIFToPrinter() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").
                    to("lpr://localhost/default?flavor=DocFlavor.INPUT_STREAM&mimeType=GIF&mediaSize=na-letter&sides=one-sided&sendToPrinter=false");
            }
        });
        context.start();

        sendGIF();
    }
    
    public void testSendingJPEGToPrinter() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").
                    to("lpr://localhost/default?copies=2&flavor=DocFlavor.INPUT_STREAM&mimeType=JPEG&mediaSize=na-letter&sides=one-sided&sendToPrinter=false");
            }
        });
        context.start();

        sendJPEG();
    }

}
