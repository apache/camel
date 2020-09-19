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
package org.apache.camel.dataformat.barcode;

import java.io.*;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BarcodeUnmarshalTest extends BarcodeTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(BarcodeUnmarshalTest.class);

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // clean directory
        File directory = new File(PATH);
        if (!directory.isDirectory() || !directory.exists()) {
            LOG.error(String.format(
                    "cannot delete files from directory '%s', because path is not a directory, or it doesn't exist.", PATH));
        } else {
            LOG.info("deleting files from " + PATH + "...");
            File[] files = directory.listFiles();
            for (File file : files) {
                LOG.info(String.format("deleting %s", file.getName()));
                file.delete();
            }
        }
    }

    @Test
    void testOrientation() throws Exception {

        Exchange exchange = template.request("direct:code1", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(MSG);
            }
        });

        assertEquals(180, exchange.getOut().getHeader("ORIENTATION"));

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                DataFormat code1 = new BarcodeDataFormat(200, 200, BarcodeImageType.PNG, BarcodeFormat.CODE_39);

                from("direct:code1")
                        .marshal(code1)
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                ByteArrayInputStream bis = new ByteArrayInputStream((byte[]) exchange.getIn().getBody());
                                BinaryBitmap bitmap = new BinaryBitmap(
                                        new HybridBinarizer(new BufferedImageLuminanceSource(ImageIO.read(bis))));
                                BitMatrix blackMatrix = bitmap.getBlackMatrix();
                                blackMatrix.rotate180();
                                File file = new File(PATH + "/TestImage.png");
                                FileOutputStream outputStream = new FileOutputStream(file);
                                MatrixToImageWriter.writeToStream(blackMatrix, "png", outputStream);
                                exchange.getIn().setBody(file);
                            }
                        }).unmarshal(code1)
                        .to("log:OUT")
                        .to("mock:out");
            }
        };
    }
}
