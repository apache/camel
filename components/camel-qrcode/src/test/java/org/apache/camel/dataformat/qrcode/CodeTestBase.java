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
package org.apache.camel.dataformat.qrcode;

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
import static org.junit.Assert.assertEquals;

/**
 *
 * @author claus.straube
 */
public class CodeTestBase extends CamelTestSupport {

    protected final static String MSG = "This is a testmessage!";

    @EndpointInject(uri = "mock:out")
    MockEndpoint out;

    @EndpointInject(uri = "mock:image")
    MockEndpoint image;

    protected void checkImage(MockEndpoint mock, int height, int width, String type) throws IOException {
        Exchange ex = mock.getReceivedExchanges().get(0);
        File in = ex.getIn().getBody(File.class);

        // check image
        BufferedImage i = ImageIO.read(in);
        assertEquals(height, i.getHeight());
        assertEquals(width, i.getWidth());
        ImageInputStream iis = ImageIO.createImageInputStream(in);
        ImageReader reader = ImageIO.getImageReaders(iis).next();
        String format = reader.getFormatName();
        assertEquals(type, format.toUpperCase());
        in.delete();
    }
}
