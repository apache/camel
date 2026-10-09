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

import java.io.IOException;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class tests all Camel independent test cases for {@link BarcodeDataFormat}.
 */
public class BarcodeDataFormatTest {

    /**
     * Test default constructor.
     */
    @Test
    final void testDefaultConstructor() throws IOException {
        try (BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat()) {
            barcodeDataFormat.start();
            this.checkParams(BarcodeParameters.IMAGE_TYPE, BarcodeParameters.WIDTH, BarcodeParameters.HEIGHT,
                    BarcodeParameters.FORMAT, barcodeDataFormat.getParams());
        }
    }

    /**
     * Test constructor with barcode format.
     */
    @Test
    final void testConstructorWithBarcodeFormat() throws IOException {
        try (BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat(BarcodeFormat.AZTEC)) {
            barcodeDataFormat.start();
            this.checkParams(BarcodeParameters.IMAGE_TYPE, BarcodeParameters.WIDTH, BarcodeParameters.HEIGHT,
                    BarcodeFormat.AZTEC, barcodeDataFormat.getParams());
        }
    }

    /**
     * Test constructor with size.
     */
    @Test
    final void testConstructorWithSize() throws IOException {
        try (BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat(200, 250)) {
            barcodeDataFormat.start();
            this.checkParams(BarcodeParameters.IMAGE_TYPE, 200, 250, BarcodeParameters.FORMAT, barcodeDataFormat.getParams());
        }
    }

    /**
     * Test constructor with image type.
     */
    @Test
    final void testConstructorWithImageType() throws IOException {
        try (BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat(BarcodeImageType.JPG)) {
            barcodeDataFormat.start();
            this.checkParams(BarcodeImageType.JPG, BarcodeParameters.WIDTH, BarcodeParameters.HEIGHT, BarcodeParameters.FORMAT,
                    barcodeDataFormat.getParams());
        }
    }

    /**
     * Test constructor with all.
     */
    @Test
    final void testConstructorWithAll() throws IOException {
        try (BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat(200, 250, BarcodeImageType.JPG, BarcodeFormat.AZTEC)) {
            barcodeDataFormat.start();
            this.checkParams(BarcodeImageType.JPG, 200, 250, BarcodeFormat.AZTEC, barcodeDataFormat.getParams());
        }
    }

    /**
     * Test of optimizeHints method, of class BarcodeDataFormat.
     */
    @Test
    final void testOptimizeHints() throws IOException {
        try (BarcodeDataFormat instance = new BarcodeDataFormat()) {
            instance.start();
            assertTrue(instance.getWriterHintMap().containsKey(EncodeHintType.ERROR_CORRECTION));
            assertTrue(instance.getReaderHintMap().containsKey(DecodeHintType.TRY_HARDER));
        }
    }

    /**
     * Test optimized hints for data matrix.
     */
    @Test
    final void testOptimizieHintsForDataMatrix() throws IOException {
        try (BarcodeDataFormat instance = new BarcodeDataFormat(BarcodeFormat.DATA_MATRIX)) {
            instance.start();
            assertTrue(instance.getWriterHintMap().containsKey(EncodeHintType.DATA_MATRIX_SHAPE),
                    "data matrix shape hint incorrect.");
            assertTrue(instance.getReaderHintMap().containsKey(DecodeHintType.TRY_HARDER), "try harder hint incorrect.");
        }
    }

    /**
     * Test re-optimize hints.
     */
    @Test
    final void testReOptimizeHints() throws IOException {
        // DATA-MATRIX
        try (BarcodeDataFormat instance = new BarcodeDataFormat(BarcodeFormat.DATA_MATRIX)) {
            instance.start();
            assertTrue(instance.getWriterHintMap().containsKey(EncodeHintType.DATA_MATRIX_SHAPE));
            assertTrue(instance.getReaderHintMap().containsKey(DecodeHintType.TRY_HARDER));

            instance.stop();
            // -> QR-CODE
            instance.setBarcodeFormat(BarcodeFormat.QR_CODE);
            instance.start();
            assertFalse(instance.getWriterHintMap().containsKey(EncodeHintType.DATA_MATRIX_SHAPE));
            assertTrue(instance.getReaderHintMap().containsKey(DecodeHintType.TRY_HARDER));
        }
    }

    /**
     * Test of addToHintMap method, of class BarcodeDataFormat.
     */
    @Test
    final void testAddToHintMapEncodeHintTypeObject() throws IOException {
        EncodeHintType hintType = EncodeHintType.MARGIN;
        Object value = 10;
        try (BarcodeDataFormat instance = new BarcodeDataFormat()) {
            instance.start();
            instance.addToHintMap(hintType, value);
            assertTrue(instance.getWriterHintMap().containsKey(hintType));
            assertEquals(instance.getWriterHintMap().get(hintType), value);
        }
    }

    /**
     * Test of addToHintMap method, of class BarcodeDataFormat.
     */
    @Test
    final void testAddToHintMapDecodeHintTypeObject() throws IOException {
        DecodeHintType hintType = DecodeHintType.CHARACTER_SET;
        Object value = "UTF-8";
        try (BarcodeDataFormat instance = new BarcodeDataFormat()) {
            instance.start();
            instance.addToHintMap(hintType, value);
            assertTrue(instance.getReaderHintMap().containsKey(hintType));
            assertEquals(instance.getReaderHintMap().get(hintType), value);
        }
    }

    /**
     * Test of removeFromHintMap method, of class BarcodeDataFormat.
     */
    @Test
    final void testRemoveFromHintMapEncodeHintType() throws IOException {
        EncodeHintType hintType = EncodeHintType.ERROR_CORRECTION;
        try (BarcodeDataFormat instance = new BarcodeDataFormat()) {
            instance.start();
            instance.removeFromHintMap(hintType);
            assertFalse(instance.getWriterHintMap().containsKey(hintType));
        }
    }

    /**
     * Test of removeFromHintMap method, of class BarcodeDataFormat.
     */
    @Test
    final void testRemoveFromHintMapDecodeHintType() throws IOException {
        DecodeHintType hintType = DecodeHintType.TRY_HARDER;
        try (BarcodeDataFormat instance = new BarcodeDataFormat()) {
            instance.start();
            instance.removeFromHintMap(hintType);
            assertFalse(instance.getReaderHintMap().containsKey(hintType));
        }
    }

    /**
     * Test of getParams method, of class BarcodeDataFormat.
     */
    @Test
    final void testGetParams() throws IOException {
        try (BarcodeDataFormat instance = new BarcodeDataFormat()) {
            instance.start();
            BarcodeParameters result = instance.getParams();
            assertNotNull(result);
        }
    }

    /**
     * Test of getWriterHintMap method, of class BarcodeDataFormat.
     */
    @Test
    final void testGetWriterHintMap() throws IOException {
        try (BarcodeDataFormat instance = new BarcodeDataFormat()) {
            instance.start();
            Map<EncodeHintType, Object> result = instance.getWriterHintMap();
            assertNotNull(result);
        }
    }

    /**
     * Test of getReaderHintMap method, of class BarcodeDataFormat.
     */
    @Test
    final void testGetReaderHintMap() throws IOException {
        try (BarcodeDataFormat instance = new BarcodeDataFormat()) {
            instance.start();
            Map<DecodeHintType, Object> result = instance.getReaderHintMap();
            assertNotNull(result);
        }
    }

    /**
     * A change made through the maps returned by the getters would bypass the hints tracked by
     * {@code addToHintMap}/{@code removeFromHintMap}, and be lost when the data format starts again, so the maps are
     * read-only.
     */
    @Test
    final void testHintMapsAreReadOnly() throws IOException {
        try (BarcodeDataFormat instance = new BarcodeDataFormat()) {
            instance.start();
            Map<EncodeHintType, Object> writerHints = instance.getWriterHintMap();
            Map<DecodeHintType, Object> readerHints = instance.getReaderHintMap();

            assertThrows(UnsupportedOperationException.class, () -> writerHints.put(EncodeHintType.MARGIN, 10));
            assertThrows(UnsupportedOperationException.class, () -> writerHints.remove(EncodeHintType.ERROR_CORRECTION));
            assertThrows(UnsupportedOperationException.class, writerHints::clear);
            assertThrows(UnsupportedOperationException.class, () -> readerHints.put(DecodeHintType.PURE_BARCODE, true));
            assertThrows(UnsupportedOperationException.class, () -> readerHints.remove(DecodeHintType.TRY_HARDER));
            assertThrows(UnsupportedOperationException.class, readerHints::clear);

            assertEquals(Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H), instance.getWriterHintMap());
            assertEquals(Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE), instance.getReaderHintMap());
        }
    }

    /**
     * The maps returned by the getters are live views: a map obtained before a change shows it afterwards, including
     * the hints computed again when the data format starts and restarts.
     */
    @Test
    final void testHintMapsAreLiveViews() throws IOException {
        try (BarcodeDataFormat instance = new BarcodeDataFormat()) {
            Map<EncodeHintType, Object> writerHints = instance.getWriterHintMap();
            Map<DecodeHintType, Object> readerHints = instance.getReaderHintMap();

            instance.addToHintMap(EncodeHintType.MARGIN, 5);
            instance.addToHintMap(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
            assertEquals(Map.of(EncodeHintType.MARGIN, 5), writerHints);
            assertEquals(Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE), readerHints);

            instance.start();
            assertEquals(Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H, EncodeHintType.MARGIN, 5),
                    writerHints);
            assertEquals(Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE, DecodeHintType.PURE_BARCODE, Boolean.TRUE),
                    readerHints);

            instance.removeFromHintMap(EncodeHintType.ERROR_CORRECTION);
            instance.removeFromHintMap(DecodeHintType.TRY_HARDER);
            assertEquals(Map.of(EncodeHintType.MARGIN, 5), writerHints);
            assertEquals(Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE), readerHints);

            instance.stop();
            instance.addToHintMap(EncodeHintType.MARGIN, 7);
            instance.start();
            assertEquals(Map.of(EncodeHintType.MARGIN, 7), writerHints);
            assertEquals(Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE), readerHints);
        }
    }

    /**
     * Helper to check the saved parameters.
     */
    private void checkParams(
            BarcodeImageType imageType, int width, int height, BarcodeFormat format, BarcodeParameters params) {
        assertEquals(params.getType(), imageType);
        assertEquals(width, (int) params.getWidth());
        assertEquals(height, (int) params.getHeight());
        assertEquals(params.getFormat(), format);
    }
}
