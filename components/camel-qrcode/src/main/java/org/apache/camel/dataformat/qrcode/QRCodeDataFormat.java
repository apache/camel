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
        String payload = super.printImage(exchange, graph, stream, writer, format);
        LOG.debug(String.format("Marshalling body '%s' to %s - code.", payload, format.toString()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        LOG.debug("Unmarshalling code image to string.");
        return super.readImage(exchange, stream, reader);
    }
}
