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

import com.google.zxing.BarcodeFormat;

/**
 * All configuration parameters for the code component.
 */
public class BarcodeParameters {
    
    /**
     * Default image type: PNG
     */
    public static final BarcodeImageType IMAGE_TYPE = BarcodeImageType.PNG;
    
    /**
     * Default width: 100px
     */
    public static final int WIDTH = 100;
    
    /**
     * Default height: 100px
     */
    public static final int HEIGHT = 100;
    
    /**
     * Default barcode format: QR-CODE
     */
    public static final BarcodeFormat FORMAT = BarcodeFormat.QR_CODE;
    
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
     *  <li>format: QR-Code</li>
     * </ul>
     */
    public BarcodeParameters() {
        
    }
    
    /**
     * Constructor with parameters.
     * @param type
     * @param width
     * @param height
     * @param format
     */
    public BarcodeParameters(final BarcodeImageType type, final int width, 
            final int height, final BarcodeFormat format) {
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
   
    public BarcodeFormat getFormat() {
        return format;
    }

    public void setFormat(BarcodeFormat format) {
        this.format = format;
    }
}
