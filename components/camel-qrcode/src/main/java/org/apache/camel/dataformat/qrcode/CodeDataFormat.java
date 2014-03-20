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
import com.google.zxing.ChecksumException;
import com.google.zxing.EncodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;

/**
 * The super class for all code data formats.
 * 
 * @author claus.straube
 */
public abstract class CodeDataFormat implements DataFormat {

    /**
     * The default parameters.
     */
    protected Parameters params;
    
    /**
     * If true, the header parameters of a message will be used to configure
     * the component.
     */
    protected boolean parameterized = true;
    
    protected final Map<EncodeHintType, ErrorCorrectionLevel> writerHintMap = new EnumMap<EncodeHintType, ErrorCorrectionLevel>(EncodeHintType.class);

    /**
     * Create instance with default parameters.
     * 
     * @param parameterized if true you can override default values with header parameters
     */
    public CodeDataFormat(boolean parameterized) {
        this.parameterized = parameterized;
        this.setDefaultParameters();
    }

    /**
     * Create instance with custom height and width. The other values are default.
     * 
     * @param height the image height
     * @param width the image width
     * @param parameterized if true you can override default values with header parameters
     */
    public CodeDataFormat(int height, int width, boolean parameterized) {
        this.parameterized = parameterized;
        this.setDefaultParameters();
        this.params.setHeight(height);
        this.params.setWidth(width);
    }

    /**
     * Create instance with custom {@link ImageType}. The other values are default.
     * 
     * @param type the type (format) of the image. e.g. PNG
     * @param parameterized if true you can override default values with header parameters
     */
    public CodeDataFormat(ImageType type, boolean parameterized) {
        this.parameterized = parameterized;
        this.setDefaultParameters();
        this.params.setType(type);
    }
    
    /**
     * Create instance with custom height, width and image type. The other values are default.
     * 
     * @param height the image height
     * @param width the image width
     * @param type the type (format) of the image. e.g. PNG
     * @param parameterized if true you can override default values with header parameters
     */
    public CodeDataFormat(int height, int width, ImageType type, boolean parameterized) {
        this.parameterized = parameterized;
        this.setDefaultParameters();
        this.params.setHeight(height);
        this.params.setWidth(width);
        this.params.setType(type);
    }
    
    /**
     * Marshall a {@link String} payload to a code image.
     * 
     * @param exchange
     * @param graph
     * @param stream
     * @throws Exception 
     */
    @Override
    public abstract void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception;

    /**
     * Unmarshall a code image to a {@link String} payload.
     * 
     * @param exchange
     * @param stream
     * @return
     * @throws Exception 
     */
    @Override
    public abstract Object unmarshal(Exchange exchange, InputStream stream) throws Exception;
    
    /**
     * Sets the default parameters:
     * <ul>
     *  <li>image type: PNG</li>
     *  <li>image width: 100px</li>
     *  <li>image heigth: 100px</li>
     *  <li>encoding: UTF-8</li>
     * </ul>
     */
    protected void setDefaultParameters() {
        this.params = new Parameters(ImageType.PNG, 100, 100, "UTF-8");
        this.writerHintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
    }
    
    /**
     * Creates and fille a {@link Parameters} object with the 
     * given header parameters. 
     * 
     * @param exchange the camel {@link Exchange}
     * @return a filled {@link Parameters} instance 
     */
    protected Parameters setRequestParameters(Exchange exchange) {
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
        
        // set file name (<exchangeid>.<imagetype>)       
        String filename = String.format("%s.%s", name, p.getType().toString().toLowerCase());
        exchange.getOut().setHeader(Exchange.FILE_NAME, filename);
        
        return p;
    }
    
    /**
     * Writes the image file to the output stream.
     * 
     * @param writer the zxing wiriter instance
     * @param graph the object graph
     * @param exchange the camel exchange
     * @param format the barcode format
     * @param stream the output stream
     * @return the message payload
     * @throws WriterException
     * @throws UnsupportedEncodingException
     * @throws IOException 
     */
    protected String printImage(Exchange exchange
            , Object graph
            , OutputStream stream
            , Writer writer
            , BarcodeFormat format) throws WriterException, UnsupportedEncodingException, IOException, TypeConversionException, NoTypeConversionAvailableException {
         
        String payload = ExchangeHelper.convertToMandatoryType(exchange, String.class, graph);
        Parameters p = this.setRequestParameters(exchange);

        // set values
        String type = p.getType().toString();
        String encoding = p.getEncoding(); 
        
        // create code image  
        BitMatrix matrix = writer.encode(
                new String(payload.getBytes(encoding), encoding),
                format, 
                p.getWidth(), 
                p.getHeight(), 
                writerHintMap);
        
        MatrixToImageWriter.writeToStream(matrix, type, stream);
        
        return payload;
    }
    
    /**
     * Reads the message from a code.
     * 
     * @param exchange
     * @param stream
     * @param reader
     * @return
     * @throws TypeConversionException
     * @throws NoTypeConversionAvailableException
     * @throws IOException
     * @throws NotFoundException
     * @throws ChecksumException
     * @throws FormatException 
     */
    protected String readImage(Exchange exchange, InputStream stream, Reader reader) throws TypeConversionException, NoTypeConversionAvailableException, IOException, NotFoundException, ChecksumException, FormatException {
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

    public Parameters getParams() {
        return params;
    }

    public boolean isParameterized() {
        return parameterized;
    }
}
