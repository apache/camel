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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class tests all Camel independent test cases for
 * {@link BarcodeDataFormat}.
 */
public class BarcodeDataFormatTest {

    /**
     * Test default constructor.
     */
    @Test
    final void testDefaultConstructor() throws IOException {
        try (BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat()) {
            this.checkParams(BarcodeParameters.IMAGE_TYPE, BarcodeParameters.WIDTH, BarcodeParameters.HEIGHT, BarcodeParameters.FORMAT, barcodeDataFormat.getParams());
        }
    }

    /**
     * Test constructor with barcode format.
     */
    @Test
    final void testConstructorWithBarcodeFormat() throws IOException {
        try (BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat(BarcodeFormat.AZTEC)) {
            this.checkParams(BarcodeParameters.IMAGE_TYPE, BarcodeParameters.WIDTH, BarcodeParameters.HEIGHT, BarcodeFormat.AZTEC, barcodeDataFormat.getParams());
        }
    }

    /**
     * Test constructor with size.
     */
    @Test
    final void testConstructorWithSize() throws IOException {
        try (BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat(200, 250)) {
            this.checkParams(BarcodeParameters.IMAGE_TYPE, 200, 250, BarcodeParameters.FORMAT, barcodeDataFormat.getParams());
        }
    }

    /**
     * Test constructor with image type.
     */
    @Test
    final void testConstructorWithImageType() throws IOException {
        try (BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat(BarcodeImageType.JPG)) {
            this.checkParams(BarcodeImageType.JPG, BarcodeParameters.WIDTH, BarcodeParameters.HEIGHT, BarcodeParameters.FORMAT, barcodeDataFormat.getParams());
        }
    }

    /**
     * Test constructor with all.
     */
    @Test
    final void testConstructorWithAll() throws IOException {
        try (BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat(200, 250, BarcodeImageType.JPG, BarcodeFormat.AZTEC)) {
            this.checkParams(BarcodeImageType.JPG, 200, 250, BarcodeFormat.AZTEC, barcodeDataFormat.getParams());
        }
    }

    /**
     * Test of optimizeHints method, of class BarcodeDataFormat.
     */
    @Test
    final void testOptimizeHints() throws IOException {
        try (BarcodeDataFormat instance = new BarcodeDataFormat()) {
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
            assertTrue(instance.getWriterHintMap().containsKey(EncodeHintType.DATA_MATRIX_SHAPE), "data matrix shape hint incorrect.");
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
            assertTrue(instance.getWriterHintMap().containsKey(EncodeHintType.DATA_MATRIX_SHAPE));
            assertTrue(instance.getReaderHintMap().containsKey(DecodeHintType.TRY_HARDER));

            // -> QR-CODE
            instance.setBarcodeFormat(BarcodeFormat.QR_CODE);
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
            Map<DecodeHintType, Object> result = instance.getReaderHintMap();
            assertNotNull(result);
        }
    }

    /**
     * Helper to check the saved parameters.
     */
    private void checkParams(BarcodeImageType imageType, int width, int height, BarcodeFormat format, BarcodeParameters params) {
        assertEquals(params.getType(), imageType);
        assertTrue(params.getWidth() == width);
        assertTrue(params.getHeight() == height);
        assertEquals(params.getFormat(), format);
    }
}
