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
package org.apache.camel.dataformat.qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Apache Camel {@link DataFormat} component to generate a qr-code image from a text message
 * and vice versa.
 * 
 * @author claus.straube
 */
public class QRCodeDataFormat extends CodeDataFormat {

    private static final Logger LOG = LoggerFactory.getLogger(QRCodeDataFormat.class);

    /**
     * The barcode format. Default is QR-Code.
     */
    private final BarcodeFormat format = BarcodeFormat.QR_CODE;
    
    /**
     * The {@link QRCodeWriter} instance.
     */
    private final QRCodeWriter writer = new QRCodeWriter();
    
    /**
     * The {@link QRCodeReader} instance.
     */
    private final QRCodeReader reader = new QRCodeReader();

    /**
     * {@inheritDoc}
     */
    public QRCodeDataFormat(boolean parameterized) {
        super(parameterized);
    }

    /**
     * {@inheritDoc}
     */
    public QRCodeDataFormat(int height, int width, boolean parameterized) {
        super(height, width, parameterized);
    }
 
    /**
     * {@inheritDoc}
     */
    public QRCodeDataFormat(ImageType type, boolean parameterized) {
        super(type, parameterized);
    }
    
    /**
     * {@inheritDoc}
     */
    public QRCodeDataFormat(int height, int width, ImageType type, boolean parameterized) {
        super(height, width, type, parameterized);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        String payload = ExchangeHelper.convertToMandatoryType(exchange, String.class, graph);
        LOG.debug(String.format("Marshalling body '%s' to %s - code.", payload, format.toString()));
        
        // set default values
        Parameters p = this.params;
        String name = exchange.getExchangeId();
        
        // if message headers should be used, create a new parameters object
        if(this.parameterized) {
            Map<String, Object> headers = exchange.getIn().getHeaders();
            p = new Parameters(headers, params);
            
            // if a qrcode filename is set, take it
            if(headers.containsKey(QRCode.NAME)) {
                name = (String) headers.get(QRCode.NAME);
            }
        } 

        // set values
        String type = p.getType().toString();
        String charset = p.getCharset(); 
        
        // set file name (<exchangeid>.<imagetype>)       
        String filename = String.format("%s.%s", name, type.toLowerCase());
        exchange.getOut().setHeader(Exchange.FILE_NAME, filename);
        
        // create qr-code image
        Map<EncodeHintType, ErrorCorrectionLevel> hintMap = new EnumMap<EncodeHintType, ErrorCorrectionLevel>(EncodeHintType.class);
        hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);    
        
        BitMatrix matrix = writer.encode(
                new String(payload.getBytes(charset), charset),
                format, 
                p.getWidth(), 
                p.getHeight(), 
                hintMap);
        
        MatrixToImageWriter.writeToStream(matrix, type, stream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        LOG.debug("Unmarshalling code image to string.");

        BufferedInputStream in = exchange.getContext()
                .getTypeConverter()
                .mandatoryConvertTo(BufferedInputStream.class, stream);
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(
                        new BufferedImageLuminanceSource(ImageIO.read(in))));
        Result result = reader.decode(bitmap);
        
        // write the found barcode format into the header
        exchange.getOut().setHeader(QRCode.BARCODE_FORMAT, result.getBarcodeFormat());
        
        return result.getText();
    }
}
