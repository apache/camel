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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.datamatrix.DataMatrixWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataFormat;
import org.junit.Test;

/**
 *
 * @author claus.straube
 */
public class DataMatrixDataFormatTest extends CodeTestBase {

//    @Test
//    public void testDefaultMDCode() throws Exception {
//        out.expectedBodiesReceived(MSG);
//        image.expectedMessageCount(1);
//
//        template.sendBody("direct:code1", MSG);
//
//        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
//        this.checkImage(image, 100, 100, ImageType.PNG.toString());
//    }
    @Test
    public void testDataMatrix() throws Exception {
        writeDataMatrix();
        String result = readDataMatrix("/home/claus/NetBeansProjects/camel/components/camel-qrcode/target/out/data_matrix.png", "UTF-8", new EnumMap<DecodeHintType, Object>(DecodeHintType.class));
        assertEquals("my message", result);
    }
    
    public static void writeDataMatrix() throws IOException {
        DataMatrixWriter writer = new DataMatrixWriter();
        BitMatrix matrix = writer.encode("my message", BarcodeFormat.DATA_MATRIX, 100, 100);
        
        MatrixToImageWriter.writeToPath(matrix, "PNG", Paths.get("/home/claus/NetBeansProjects/camel/components/camel-qrcode/target/out/data_matrix.png"));
    }

    public static String readDataMatrix(String filePath, String charset, Map hintMap)
            throws FileNotFoundException, IOException, NotFoundException {
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(
                        ImageIO.read(new FileInputStream(filePath)))));
        Result qrCodeResult = new MultiFormatReader().decode(binaryBitmap,
                hintMap);
        return qrCodeResult.getText();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {

                // Code default
                DataFormat code1 = new DataMatrixDataFormat(false);

                from("direct:code1")
                        .marshal(code1)
                        .to("file:target/out");

                // Code with modified size
                DataFormat code2 = new DataMatrixDataFormat(200, 200, false);

                from("direct:code2")
                        .marshal(code2)
                        .to("file:target/out");

                // Code with JPEG type
                DataFormat code3 = new DataMatrixDataFormat(ImageType.JPG, false);

                from("direct:code3")
                        .marshal(code3)
                        .to("file:target/out");

                // Code with GIF type
                DataFormat code4 = new DataMatrixDataFormat(ImageType.GIF, false);

                from("direct:code4")
                        .marshal(code4)
                        .to("file:target/out");

                // Code with modified size and image type
                DataFormat code5 = new DataMatrixDataFormat(200, 200, ImageType.JPG, false);

                from("direct:code5")
                        .marshal(code5)
                        .to("file:target/out");

                // QR-Code with parameters
                from("direct:code_param")
                        .marshal(new DataMatrixDataFormat(true))
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
