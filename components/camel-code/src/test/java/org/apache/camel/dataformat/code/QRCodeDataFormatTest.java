/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.camel.dataformat.code;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataFormat;
import org.junit.Test;

public class QRCodeDataFormatTest extends CodeTestBase {

    @Test
    public void testDefaultQRCode() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);

        template.sendBody("direct:qrcode1", MSG);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        this.checkImage(image, 100, 100, ImageType.PNG.toString());
    }

    @Test
    public void testQRCodeWithModifiedSize() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);

        template.sendBody("direct:qrcode2", MSG);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        this.checkImage(image, 200, 200, ImageType.PNG.toString());
    }

    @Test
    public void testQRCodeWithJPEGType() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);

        template.sendBody("direct:qrcode3", MSG);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        this.checkImage(image, 100, 100, "JPEG");
    }

    @Test
    public void testQRCodeWithGIFType() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);

        template.sendBody("direct:qrcode4", MSG);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        this.checkImage(image, 100, 100, ImageType.GIF.toString());
    }

    @Test
    public void testQRCodeWidthModifiedSizeAndImageType() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);

        template.sendBody("direct:qrcode5", MSG);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        this.checkImage(image, 200, 200, "JPEG");
    }

    @Test
    public void testQRCodeWithParameterizedSize() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);
        HashMap<String, Object> headers = new HashMap<String, Object>();
        headers.put(Code.WIDTH, 200);
        headers.put(Code.HEIGHT, 200);

        template.sendBodyAndHeaders("direct:qrcode_param", MSG, headers);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        this.checkImage(image, 200, 200, ImageType.PNG.toString());
    }

    @Test
    public void testQRCodeWithParameterizedType() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);
        HashMap<String, Object> headers = new HashMap<String, Object>();
        headers.put(Code.IMAGE_TYPE, ImageType.JPG);

        template.sendBodyAndHeaders("direct:qrcode_param", MSG, headers);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        this.checkImage(image, 100, 100, "JPEG");
    }

    @Test
    public void testQRCodeWithParameterizedSizeAndType() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);
        HashMap<String, Object> headers = new HashMap<String, Object>();
        headers.put(Code.WIDTH, 200);
        headers.put(Code.HEIGHT, 200);
        headers.put(Code.IMAGE_TYPE, ImageType.JPG);

        template.sendBodyAndHeaders("direct:qrcode_param", MSG, headers);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        this.checkImage(image, 200, 200, "JPEG");
    }

    @Test
    public void testQRCodeWithParameterizedName() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);
        HashMap<String, Object> headers = new HashMap<String, Object>();
        headers.put(Code.NAME, "foo");

        template.sendBodyAndHeaders("direct:qrcode_param", MSG, headers);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        assertEquals("foo.png", image.getExchanges().get(0).getIn().getHeader(Exchange.FILE_NAME_ONLY));
        this.checkImage(image, 100, 100, ImageType.PNG.toString());
    }
    
    @Test
    public void testQRCodeAllParameterized() throws Exception {
        out.expectedBodiesReceived(MSG);
        image.expectedMessageCount(1);
        HashMap<String, Object> headers = new HashMap<String, Object>();
        headers.put(Code.WIDTH, 200);
        headers.put(Code.HEIGHT, 200);
        headers.put(Code.IMAGE_TYPE, ImageType.JPG);
        headers.put(Code.NAME, "foo");

        template.sendBodyAndHeaders("direct:qrcode_param", MSG, headers);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
        assertEquals("foo.jpg", image.getExchanges().get(0).getIn().getHeader(Exchange.FILE_NAME_ONLY));
        this.checkImage(image, 200, 200, "JPEG");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {

                // QR-Code default
                DataFormat qrcode1 = new QRCodeDataFormat(false);

                from("direct:qrcode1")
                        .marshal(qrcode1)
                        .to("file:target/out");

                // QR-Code with modified size
                DataFormat qrcode2 = new QRCodeDataFormat(200, 200, false);

                from("direct:qrcode2")
                        .marshal(qrcode2)
                        .to("file:target/out");

                // QR-Code with JPEG type
                DataFormat qrcode3 = new QRCodeDataFormat(ImageType.JPG, false);

                from("direct:qrcode3")
                        .marshal(qrcode3)
                        .to("file:target/out");

                // QR-Code with GIF type
                DataFormat qrcode4 = new QRCodeDataFormat(ImageType.GIF, false);

                from("direct:qrcode4")
                        .marshal(qrcode4)
                        .to("file:target/out");

                // QR-Code with modified size and image type
                DataFormat qrcode5 = new QRCodeDataFormat(200, 200, ImageType.JPG, false);

                from("direct:qrcode5")
                        .marshal(qrcode5)
                        .to("file:target/out");
                
                // QR-Code with parameters
                from("direct:qrcode_param")
                        .marshal(new QRCodeDataFormat(true))
                        .to("file:target/out");

                // generic file read --->
                // 
                // read file and route it
                from("file:target/out?noop=true")
                        .multicast().to("direct:marshall", "mock:image");

                // get the message from qrcode
                from("direct:marshall")
                        .unmarshal(qrcode1) // for unmarshalling, the instance doesn't matter
                        .to("log:OUT")
                        .to("mock:out");

            }
        };
    }
}
