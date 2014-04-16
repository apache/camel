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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import java.util.Map;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author claus.straube
 */
public class BarcodeDataFormatTest {
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }
    
    @Test
    public void testDefaultConstructor() {
        BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat();
        this.checkParams(BarcodeParameters.IMAGE_TYPE, BarcodeParameters.WIDTH, BarcodeParameters.HEIGHT, BarcodeParameters.ENCODING, BarcodeParameters.FORMAT, barcodeDataFormat.getParams());
    }
    
    @Test
    public void testConstructorWithBarcodeFormat() {
        BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat(BarcodeFormat.AZTEC);
        this.checkParams(BarcodeParameters.IMAGE_TYPE, BarcodeParameters.WIDTH, BarcodeParameters.HEIGHT, BarcodeParameters.ENCODING, BarcodeFormat.AZTEC, barcodeDataFormat.getParams());
    }
    
    @Test
    public void testConstructorWithSize() {
        BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat(200, 250);
        this.checkParams(BarcodeParameters.IMAGE_TYPE, 200, 250, BarcodeParameters.ENCODING, BarcodeParameters.FORMAT, barcodeDataFormat.getParams());
    }
    
    @Test
    public void testConstructorWithImageType() {
        BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat(BarcodeImageType.JPG);
        this.checkParams(BarcodeImageType.JPG, BarcodeParameters.WIDTH, BarcodeParameters.HEIGHT, BarcodeParameters.ENCODING, BarcodeParameters.FORMAT, barcodeDataFormat.getParams());
    }
    
    @Test
    public void testConstructorWithAll() {
        BarcodeDataFormat barcodeDataFormat = new BarcodeDataFormat(200, 250, BarcodeImageType.JPG, BarcodeFormat.AZTEC);
        this.checkParams(BarcodeImageType.JPG, 200, 250, BarcodeParameters.ENCODING, BarcodeFormat.AZTEC, barcodeDataFormat.getParams());
    }

    /**
     * Test of optimizeHints method, of class BarcodeDataFormat.
     */
    @Test
    public void testOptimizeHints() {
        BarcodeDataFormat instance = new BarcodeDataFormat();
        assertTrue(instance.getWriterHintMap().containsKey(EncodeHintType.ERROR_CORRECTION));
        assertTrue(instance.getReaderHintMap().containsKey(DecodeHintType.TRY_HARDER));
    }
    
    @Test
    public void testOptimizieHintsForDataMatrix() {
        BarcodeDataFormat instance = new BarcodeDataFormat(BarcodeFormat.DATA_MATRIX);
        assertTrue("error correction hint incorrect.", instance.getWriterHintMap().containsKey(EncodeHintType.ERROR_CORRECTION));
        assertTrue("margin hint incorrect.", instance.getWriterHintMap().containsKey(EncodeHintType.MARGIN));
        assertTrue("data matrix shape hint incorrect.", instance.getWriterHintMap().containsKey(EncodeHintType.DATA_MATRIX_SHAPE));
        assertTrue("try harder hint incorrect.",instance.getReaderHintMap().containsKey(DecodeHintType.TRY_HARDER));
    }

    /**
     * Test of addToHintMap method, of class BarcodeDataFormat.
     */
    @Test
    public void testAddToHintMap_EncodeHintType_Object() {
        EncodeHintType hintType = EncodeHintType.MARGIN;
        Object value = 10;
        BarcodeDataFormat instance = new BarcodeDataFormat();
        instance.addToHintMap(hintType, value);
        assertTrue(instance.getWriterHintMap().containsKey(hintType));
        assertEquals(instance.getWriterHintMap().get(hintType), value);
    }

    /**
     * Test of addToHintMap method, of class BarcodeDataFormat.
     */
    @Test
    public void testAddToHintMap_DecodeHintType_Object() {
        DecodeHintType hintType = DecodeHintType.CHARACTER_SET;
        Object value = "UTF-8";
        BarcodeDataFormat instance = new BarcodeDataFormat();
        instance.addToHintMap(hintType, value);
        assertTrue(instance.getReaderHintMap().containsKey(hintType));
        assertEquals(instance.getReaderHintMap().get(hintType), value);
    }

    /**
     * Test of removeFromHintMap method, of class BarcodeDataFormat.
     */
    @Test
    public void testRemoveFromHintMap_EncodeHintType() {
        EncodeHintType hintType = EncodeHintType.ERROR_CORRECTION;
        BarcodeDataFormat instance = new BarcodeDataFormat();
        instance.removeFromHintMap(hintType);
        assertFalse(instance.getWriterHintMap().containsKey(hintType));
    }

    /**
     * Test of removeFromHintMap method, of class BarcodeDataFormat.
     */
    @Test
    public void testRemoveFromHintMap_DecodeHintType() {
        DecodeHintType hintType = DecodeHintType.TRY_HARDER;
        BarcodeDataFormat instance = new BarcodeDataFormat();
        instance.removeFromHintMap(hintType);
        assertFalse(instance.getReaderHintMap().containsKey(hintType));
    }

    /**
     * Test of getParams method, of class BarcodeDataFormat.
     */
    @Test
    public void testGetParams() {
        BarcodeDataFormat instance = new BarcodeDataFormat();
        BarcodeParameters result = instance.getParams();
        assertNotNull(result);
    }

    /**
     * Test of getWriterHintMap method, of class BarcodeDataFormat.
     */
    @Test
    public void testGetWriterHintMap() {
        BarcodeDataFormat instance = new BarcodeDataFormat();
        Map<EncodeHintType, Object> result = instance.getWriterHintMap();
        assertNotNull(result);
    }

    /**
     * Test of getReaderHintMap method, of class BarcodeDataFormat.
     */
    @Test
    public void testGetReaderHintMap() {
        BarcodeDataFormat instance = new BarcodeDataFormat();
        Map<DecodeHintType, Object> result = instance.getReaderHintMap();
        assertNotNull(result);
    }
    
    private void checkParams(BarcodeImageType imageType, int width, int height, String encoding, BarcodeFormat format, BarcodeParameters params) {
        assertEquals(params.getType(), imageType);
        assertTrue(params.getWidth() == width);
        assertTrue(params.getHeight() == height);
        assertEquals(params.getEncoding(), encoding);
        assertEquals(params.getFormat(), format);
    }
}
