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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.datamatrix.encoder.SymbolShapeHint;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DataFormat} to create (encode) and
 * read (decode) barcodes. For more info about
 * the available barcodes have a look at:<br/><br/>
 * <p/>
 * https://github.com/zxing/zxing
 */
@Dataformat("barcode")
public class BarcodeDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private static final Logger LOG = LoggerFactory.getLogger(BarcodeDataFormat.class);

    /**
     * The bean for the default parameters.
     */
    private BarcodeParameters params;

    /**
     * The encoding hint map, used for writing a barcode.
     */
    private final Map<EncodeHintType, Object> writerHintMap = new EnumMap<>(EncodeHintType.class);

    /**
     * The decoding hint map, used for reading a barcode.
     */
    private final Map<DecodeHintType, Object> readerHintMap = new EnumMap<>(DecodeHintType.class);


    /**
     * Create instance with default parameters.
     */
    public BarcodeDataFormat() {
        this.setDefaultParameters();
        this.optimizeHints();
    }

    /**
     * Create instance with custom {@link BarcodeFormat}. The other
     * values are default.
     *
     * @param format the barcode format
     */
    public BarcodeDataFormat(final BarcodeFormat format) {
        this.setDefaultParameters();
        this.params.setFormat(format);
        this.optimizeHints();
    }

    /**
     * Create instance with custom height and width. The other
     * values are default.
     *
     * @param height the image height
     * @param width  the image width
     */
    public BarcodeDataFormat(final int width, final int height) {
        this.setDefaultParameters();
        this.params.setHeight(height);
        this.params.setWidth(width);
        this.optimizeHints();
    }

    /**
     * Create instance with custom {@link BarcodeImageType}. The other
     * values are default.
     *
     * @param type the type (format) of the image. e.g. PNG
     */
    public BarcodeDataFormat(final BarcodeImageType type) {
        this.setDefaultParameters();
        this.params.setType(type);
        this.optimizeHints();
    }

    /**
     * Create instance with custom height, width and image type. The other
     * values are default.
     *
     * @param height the image height
     * @param width  the image width
     * @param type   the type (format) of the image. e.g. PNG
     * @param format the barcode format
     */
    public BarcodeDataFormat(final int width, final int height, final BarcodeImageType type, final BarcodeFormat format) {
        this.setDefaultParameters();
        this.params.setHeight(height);
        this.params.setWidth(width);
        this.params.setType(type);
        this.params.setFormat(format);
        this.optimizeHints();
    }

    @Override
    public String getDataFormatName() {
        return "barcode";
    }

    /**
     * Marshall a {@link String} payload to a code image.
     */
    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        this.printImage(exchange, graph, stream);
    }

    /**
     * Unmarshall a code image to a {@link String} payload.
     */
    @Override
    public Object unmarshal(final Exchange exchange, final InputStream stream) throws Exception {
        return this.readImage(exchange, stream);
    }

    /**
     * Sets the default parameters.
     */
    protected final void setDefaultParameters() {
        this.params = new BarcodeParameters();
    }

    /**
     * Sets hints optimized for different barcode types.
     */
    protected final void optimizeHints() {
        // clear hints for re-optimization
        this.writerHintMap.clear();
        this.readerHintMap.clear();

        // writer hints
        String format = this.params.getFormat().toString();

        // only for QR code. AZTEC uses zxing's default error correction 33%.
        if (format.equals(BarcodeFormat.QR_CODE.toString())) {
            this.writerHintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        }

        if (format.equals(BarcodeFormat.DATA_MATRIX.toString())) {
            this.writerHintMap.put(EncodeHintType.DATA_MATRIX_SHAPE, SymbolShapeHint.FORCE_SQUARE);
        }

        // reader hints
        this.readerHintMap.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    }

    /**
     * Writes the image file to the output stream.
     *
     * @param graph    the object graph
     * @param exchange the camel exchange
     * @param stream   the output stream
     */
    private void printImage(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        final String payload = ExchangeHelper
                .convertToMandatoryType(exchange, String.class, graph);
        final MultiFormatWriter writer = new MultiFormatWriter();

        // set values
        final String type = this.params.getType().toString();

        // create code image  
        final BitMatrix matrix = writer.encode(
                payload,
                this.params.getFormat(),
                this.params.getWidth(),
                this.params.getHeight(),
                writerHintMap);

        // write image back to stream
        MatrixToImageWriter.writeToStream(matrix, type, stream);
    }

    /**
     * Reads the message from a code.
     */
    private String readImage(final Exchange exchange, final InputStream stream) throws Exception {
        final MultiFormatReader reader = new MultiFormatReader();
        final BufferedInputStream in = exchange.getContext()
                .getTypeConverter()
                .mandatoryConvertTo(BufferedInputStream.class, stream);
        final BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(ImageIO.read(in))));
        final Result result = reader.decode(bitmap, readerHintMap);

        // write the found barcode format into the header
        exchange.getOut().setHeader(Barcode.BARCODE_FORMAT, result.getBarcodeFormat());

        return result.getText();
    }

    /**
     * Adds a new hint value to writer (encode) hint map.
     */
    public final void addToHintMap(final EncodeHintType hintType, final Object value) {
        this.writerHintMap.put(hintType, value);
        LOG.info(String.format("Added '%s' with value '%s' to writer hint map.", hintType.toString(), value.toString()));
    }

    /**
     * Adds a new hint value to reader (decode) hint map.
     */
    public final void addToHintMap(final DecodeHintType hintType, final Object value) {
        this.readerHintMap.put(hintType, value);
    }

    /**
     * Removes a hint from writer (encode) hint map.
     */
    public final void removeFromHintMap(final EncodeHintType hintType) {
        if (this.writerHintMap.containsKey(hintType)) {
            this.writerHintMap.remove(hintType);
            LOG.info(String.format("Removed '%s' from writer hint map.", hintType.toString()));
        } else {
            LOG.warn(String.format("Could not find encode hint type '%s' in writer hint map.", hintType.toString()));
        }
    }

    /**
     * Removes a hint from reader (decode) hint map.
     */
    public final void removeFromHintMap(final DecodeHintType hintType) {
        if (this.readerHintMap.containsKey(hintType)) {
            this.readerHintMap.remove(hintType);
            LOG.info(String.format("Removed '%s' from reader hint map.", hintType.toString()));
        } else {
            LOG.warn(String.format("Could not find decode hint type '%s' in reader hint map.", hintType.toString()));
        }
    }

    /**
     * The (default) parameters.
     */
    public final BarcodeParameters getParams() {
        return params;
    }

    /**
     * The writer (encode) hint map.
     */
    public final Map<EncodeHintType, Object> getWriterHintMap() {
        return writerHintMap;
    }

    /**
     * The reader (decode) hint map.
     */
    public final Map<DecodeHintType, Object> getReaderHintMap() {
        return readerHintMap;
    }

    // these set method is used for BarcodeDataFormat XML DSL
    public void setBarcodeImageType(BarcodeImageType type) {
        this.params.setType(type);
        this.optimizeHints();
    }

    public void setBarcodeFormat(BarcodeFormat format) {
        this.params.setFormat(format);
        this.optimizeHints();
    }

    public void setWidth(Integer width) {
        this.params.setWidth(width);
    }

    public void setHeight(Integer height) {
        this.params.setHeight(height);
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}
