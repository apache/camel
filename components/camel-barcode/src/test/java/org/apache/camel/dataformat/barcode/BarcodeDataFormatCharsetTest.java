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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The text of a barcode must come back as it was written, also when ISO-8859-1 (the ZXing default) cannot represent it,
 * and the hints added before the data format is started must be used.
 */
public class BarcodeDataFormatCharsetTest extends CamelTestSupport {

    @Test
    void testQRCodeJapaneseText() throws Exception {
        assertEquals("日本語のテキスト", roundTrip(new BarcodeDataFormat(), "日本語のテキスト"));
    }

    @Test
    void testQRCodeCyrillicText() throws Exception {
        assertEquals("Привет, мир", roundTrip(new BarcodeDataFormat(), "Привет, мир"));
    }

    @Test
    void testAztecCyrillicText() throws Exception {
        assertEquals("Привет, мир",
                roundTrip(new BarcodeDataFormat(200, 200, BarcodeImageType.PNG, BarcodeFormat.AZTEC), "Привет, мир"));
    }

    @Test
    void testPdf417CyrillicText() throws Exception {
        assertEquals("Привет, мир",
                roundTrip(new BarcodeDataFormat(400, 200, BarcodeImageType.PNG, BarcodeFormat.PDF_417), "Привет, мир"));
    }

    @Test
    void testQRCodeLatinText() throws Exception {
        assertEquals("Hello Camel", roundTrip(new BarcodeDataFormat(), "Hello Camel"));
        assertEquals("Grüße aus Köln", roundTrip(new BarcodeDataFormat(), "Grüße aus Köln"));
    }

    @Test
    void testHintsAddedBeforeStart() throws Exception {
        try (BarcodeDataFormat format = new BarcodeDataFormat()) {
            format.addToHintMap(EncodeHintType.CHARACTER_SET, "UTF-8");
            format.addToHintMap(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
            format.start();

            assertEquals("UTF-8", format.getWriterHintMap().get(EncodeHintType.CHARACTER_SET));
            assertEquals(ErrorCorrectionLevel.H, format.getWriterHintMap().get(EncodeHintType.ERROR_CORRECTION));
            assertEquals(Boolean.TRUE, format.getReaderHintMap().get(DecodeHintType.PURE_BARCODE));
            assertEquals(Boolean.TRUE, format.getReaderHintMap().get(DecodeHintType.TRY_HARDER));
        }
    }

    @Test
    void testCharacterSetHintIsUsed() throws Exception {
        BarcodeDataFormat format = new BarcodeDataFormat();
        format.addToHintMap(EncodeHintType.CHARACTER_SET, "UTF-8");

        Exchange exchange = new DefaultExchange(context);
        assertEquals("Grüße", roundTrip(format, "Grüße", exchange));
        // the text was written in UTF-8 as the hint asks, not in ISO-8859-1
        List<?> segments = exchange.getMessage().getHeader("BYTE_SEGMENTS", List.class);
        assertEquals(1, segments.size());
        assertArrayEquals("Grüße".getBytes(StandardCharsets.UTF_8), (byte[]) segments.get(0));
    }

    private String roundTrip(BarcodeDataFormat writer, String text) throws Exception {
        return roundTrip(writer, text, new DefaultExchange(context));
    }

    private String roundTrip(BarcodeDataFormat writer, String text, Exchange exchange) throws Exception {
        try (BarcodeDataFormat reader = new BarcodeDataFormat()) {
            writer.start();
            reader.start();

            ByteArrayOutputStream image = new ByteArrayOutputStream();
            writer.marshal(exchange, text, image);
            return (String) reader.unmarshal(exchange,
                    new BufferedInputStream(new ByteArrayInputStream(image.toByteArray())));
        } finally {
            writer.stop();
        }
    }
}
