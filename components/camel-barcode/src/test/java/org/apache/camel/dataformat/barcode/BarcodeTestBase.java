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
import java.io.FileInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Reader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;


public class BarcodeTestBase extends CamelTestSupport {

    protected static final String MSG = "This is a testmessage!";
    
    protected static final String PATH = "target/out";
    protected static final String FILE_ENDPOINT = "file:" + PATH;

    @EndpointInject(uri = "mock:out")
    MockEndpoint out;

    @EndpointInject(uri = "mock:image")
    MockEndpoint image;

    protected void checkImage(MockEndpoint mock, int height, int width, String type, BarcodeFormat format) throws IOException {
        Exchange ex = mock.getReceivedExchanges().get(0);
        File in = ex.getIn().getBody(File.class);
        FileInputStream fis = new FileInputStream(in);

        // check image
        BufferedImage i = ImageIO.read(fis);
        IOHelper.close(fis);

        assertTrue(height >= i.getHeight());
        assertTrue(width >= i.getWidth());
        this.checkType(in, type);
        this.checkFormat(in, format);

        FileUtil.deleteFile(in);
    }

    protected void checkImage(MockEndpoint mock, String type, BarcodeFormat format) throws IOException {
        Exchange ex = mock.getReceivedExchanges().get(0);
        File in = ex.getIn().getBody(File.class);
        this.checkType(in, type);
        this.checkFormat(in, format);

        FileUtil.deleteFile(in);
    }
    
    private void checkFormat(File file, BarcodeFormat format) throws IOException {
        Reader reader = new MultiFormatReader();
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(ImageIO.read(file))));
        Result result;
        try {
            result = reader.decode(bitmap);
        } catch (ReaderException ex) {
            throw new IOException(ex);
        }
        
        assertEquals(format, result.getBarcodeFormat());
    }
    
    private void checkType(File file, String type) throws IOException {
        ImageInputStream iis = ImageIO.createImageInputStream(file);
        ImageReader reader = ImageIO.getImageReaders(iis).next();
        IOHelper.close(iis);

        String format = reader.getFormatName();
        assertEquals(type, format.toUpperCase());
    }
}
