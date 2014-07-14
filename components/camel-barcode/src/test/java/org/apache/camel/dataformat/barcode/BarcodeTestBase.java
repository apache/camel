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
package org.apache.camel.dataformat.barcode;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;


public class BarcodeTestBase extends CamelTestSupport {

    protected static final String MSG = "This is a testmessage!";
    
    protected static final String PATH = "target/out";
    protected static final String FILE_ENDPOINT = "file:" + PATH;

    @EndpointInject(uri = "mock:out")
    MockEndpoint out;

    @EndpointInject(uri = "mock:image")
    MockEndpoint image;

    protected void checkImage(MockEndpoint mock, int height, int width, String type) throws IOException {
        Exchange ex = mock.getReceivedExchanges().get(0);
        File in = ex.getIn().getBody(File.class);

        // check image
        BufferedImage i = ImageIO.read(in);
        assertTrue(height >= i.getHeight());
        assertTrue(width >= i.getWidth());
        this.checkType(in, type);
        in.delete();
    }
    
    protected void checkImage(MockEndpoint mock, String type) throws IOException {
        Exchange ex = mock.getReceivedExchanges().get(0);
        File in = ex.getIn().getBody(File.class);
        this.checkType(in, type);
        in.delete();
    }
    
    private void checkType(File file, String type) throws IOException {
        ImageInputStream iis = ImageIO.createImageInputStream(file);
        ImageReader reader = ImageIO.getImageReaders(iis).next();
        String format = reader.getFormatName();
        assertEquals(type, format.toUpperCase());
    }
}
