/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dataformat.code;

import org.apache.camel.dataformat.code.ImageType;
import org.apache.camel.dataformat.code.PDF417DataFormat;
import java.util.concurrent.TimeUnit;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataFormat;
import org.junit.Test;

/**
 *
 * @author claus
 */
public class PDF417DataFormatTest extends CodeTestBase {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 116;
    
    @Test
    public void testDefaultPDF417Code() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);

        template.sendBody("direct:code1", MSG);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        this.checkImage(image, HEIGHT, WIDTH, ImageType.PNG.toString());
    }
    
    @Test
    public void testDefaultPDF417CodeWithJPEG() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);

        template.sendBody("direct:code2", MSG);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        this.checkImage(image, HEIGHT, WIDTH, "JPEG");
    }
    
    @Test
    public void testDefaultPDF417CodeWithGIF() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);

        template.sendBody("direct:code3", MSG);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        this.checkImage(image, HEIGHT, WIDTH, ImageType.GIF.toString());
    }
    
    @Test
    public void testDefaultPDF417CodeWithModifiedDimension() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);

        template.sendBody("direct:code4", MSG);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        this.checkImage(image, HEIGHT, WIDTH, ImageType.PNG.toString());
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {

                // Code default
                DataFormat code1 = new PDF417DataFormat(false);

                from("direct:code1")
                        .marshal(code1)
                        .to("file:target/out");
                
                // jpeg
                DataFormat code2 = new PDF417DataFormat(ImageType.JPG, false);
                
                from("direct:code2")
                        .marshal(code2)
                        .to("file:target/out");
                
                // gif
                DataFormat code3 = new PDF417DataFormat(ImageType.GIF, false);
                
                from("direct:code3")
                        .marshal(code3)
                        .to("file:target/out");
                
                // dimension
                DataFormat code4 = new PDF417DataFormat(200, 200, true);
                
                from("direct:code4")
                        .marshal(code4)
                        .to("file:target/out");

                // generic file read --->
                // 
                // read file and route it
                from("file:target/out?noop=true")
                        .multicast().to("direct:marshall", "mock:image");

                // get the message from qrcode
                from("direct:marshall")
                        .unmarshal(code1) // for unmarshalling, the instance doesn't matter
                        .to("log:OUT")
                        .to("mock:out");

            }
        };
    }

}
