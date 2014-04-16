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
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.datamatrix.encoder.SymbolShapeHint;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author claus.straube
 */
public class ZxingTest {
  
    private final static String PATH = System.getProperty("user.home")+ "/tmp/zxing/";

    @Test
    public void simpleDataMatrixTest() throws IOException, FileNotFoundException, NotFoundException, InterruptedException, WriterException {
        String filePath = PATH + "/data_matrix.png";
        String text = "hello world";
        Map hintMap = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hintMap.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        
        write(filePath, text, BarcodeFormat.DATA_MATRIX);
        String result = read(filePath, hintMap);
        
        assertEquals(text, result);
    }
    
    @Test
    public void simpleQRCodeTest() throws Exception {
        String filePath = PATH + "/qrcode.png";
        String text = "hello world";
        Map<EncodeHintType, Object> encodeHintMap = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
        encodeHintMap.put(EncodeHintType.DATA_MATRIX_SHAPE, SymbolShapeHint.FORCE_SQUARE);
        encodeHintMap.put(EncodeHintType.MIN_SIZE, 200);
        encodeHintMap.put(EncodeHintType.MARGIN, 10);
        Map decodeHintMap = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        decodeHintMap.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        
        write(filePath, text, BarcodeFormat.QR_CODE);
        String result = read(filePath, decodeHintMap);
        
        assertEquals(text, result);
    }
    
    @Test
    public void readOnlyTest() throws IOException, FileNotFoundException, NotFoundException {
        String filePath = PATH + "/foo.png";
        Map hintMap = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hintMap.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        
        String result = read(filePath, hintMap);
        assertEquals("This is a testmessage!", result);
    }
    
    @Test
    public void readDocumentTest() throws IOException, FileNotFoundException, NotFoundException {
        String filePath = PATH + "/test-page-001.png";
        Map hintMap = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hintMap.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        
        String result = read(filePath, hintMap);
        assertEquals("hello world", result);
    }
    
    @Test
    public void readDocumentTestTryHarder() throws IOException, FileNotFoundException, NotFoundException {
        String filePath = PATH + "/embedded_qrcode.png";
        Map hintMap = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hintMap.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        
        String result = read(filePath, hintMap);
        assertEquals("hello world", result);
    }
    
    public static void write(String filePath, String text, BarcodeFormat format) throws IOException, WriterException {
        MultiFormatWriter writer = new MultiFormatWriter();
        Map<EncodeHintType, Object> writerHintMap = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
        writerHintMap.put(EncodeHintType.DATA_MATRIX_SHAPE, SymbolShapeHint.FORCE_SQUARE);
        BitMatrix matrix = writer.encode(text, format, 100, 100, writerHintMap);
        
        MatrixToImageWriter.writeToPath(matrix, "PNG", Paths.get(filePath));
        System.out.println("file written...");
    }

    public static String read(String filePath, Map hintMap)
            throws FileNotFoundException, IOException, NotFoundException {
        System.out.println("reading file --> " + filePath);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(
                        ImageIO.read(new FileInputStream(filePath)))));
        Result qrCodeResult = new MultiFormatReader().decode(binaryBitmap, hintMap);
        return qrCodeResult.getText();
    }
}
