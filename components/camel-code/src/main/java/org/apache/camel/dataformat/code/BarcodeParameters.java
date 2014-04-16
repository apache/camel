/*
 * Copyright 2014 claus.straube.
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

/**
 * All configuration parameters for the code component.
 * 
 * @author claus.straube
 */
public class BarcodeParameters {
    
    /**
     * Default image type: PNG
     */
    public final static BarcodeImageType IMAGE_TYPE = BarcodeImageType.PNG;
    
    /**
     * Default width: 100px
     */
    public final static int WIDTH = 100;
    
    /**
     * Default height: 100px
     */
    public final static int HEIGHT = 100;
    
    /**
     * Default encoding: UTF-8
     */
    public final static String ENCODING = "UTF-8";
    
    /**
     * Default barcode format: QR-CODE
     */
    public final static BarcodeFormat FORMAT = BarcodeFormat.QR_CODE;
    
    /**
     * The Image Type. 
     */
    private BarcodeImageType type = IMAGE_TYPE;
    
    /**
     * The width of the image.
     */
    private Integer width = WIDTH;
    
    /**
     * The height of the image.
     */
    private Integer height = HEIGHT;
    
    /**
     * The message encoding.
     */
    private String encoding = ENCODING;
    
    /**
     * The barcode format (e.g. QR-Code, DataMatrix,...).
     */
    private BarcodeFormat format = FORMAT;

    /**
     * Default Constructor (creates a bean with default parameters).
     * 
     * <ul>
     *  <li>image type: PNG</li>
     *  <li>image width: 100px</li>
     *  <li>image heigth: 100px</li>
     *  <li>encoding: UTF-8</li>
     *  <li>format: QR-Code</li>
     * </ul>
     */
    public BarcodeParameters() {
        
    }
    
    /**
     * Constructor with parameters.
     * 
     * @param type
     * @param width
     * @param height
     * @param encoding
     * @param format
     */
    public BarcodeParameters(BarcodeImageType type, Integer width, Integer height, String encoding, BarcodeFormat format) {
        this.encoding = encoding;
        this.height = height;
        this.width = width;
        this.type = type;
        this.format = format;
    }

    public BarcodeImageType getType() {
        return type;
    }

    public void setType(BarcodeImageType type) {
        this.type = type;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public BarcodeFormat getFormat() {
        return format;
    }

    public void setFormat(BarcodeFormat format) {
        this.format = format;
    }
}
