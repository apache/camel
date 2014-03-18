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

package org.apache.camel.dataformat.qrcode;

import java.util.Map;

/**
 *
 * @author claus.straube
 */
public class Parameters {
    /**
     * The Image Type. Default is PNG.
     */
    private ImageType type;
    
    /**
     * The width of the image. Default is 100px.
     */
    private Integer width;
    
    /**
     * The height of the image. Default is 100px.
     */
    private Integer height;
    
    /**
     * The message encoding. Default is UTF-8.
     */
    private String charset;

    /**
     * Default construtor creates default values.
     */
    public Parameters(ImageType type, Integer width, Integer height, String charset) {
        this.charset = charset;
        this.height = height;
        this.width = width;
        this.type = type;
    }
    
    /**
     * Parameter bean with given header.
     * 
     * @param headers the camel message headers 
     */
    public Parameters(Map<String,Object> headers, Parameters params) {
        this.setParameters(headers, params);
    }
    
    private void setParameters(Map<String,Object> headers, Parameters params) {
        
        if(headers.containsKey(QRCode.HEIGHT)) {
            this.setHeight((Integer) headers.get(QRCode.HEIGHT));
        } else {
            this.setHeight(params.getHeight());
        }
        
        if(headers.containsKey(QRCode.WIDTH)) {
            this.setWidth((Integer) headers.get(QRCode.WIDTH));
        } else {
            this.setWidth(params.getWidth());
        }
        
        if(headers.containsKey(QRCode.TYPE)) {
            this.setType((ImageType) headers.get(QRCode.TYPE));
        } else {
            this.setType(params.getType());
        }
        
        if(headers.containsKey(QRCode.ENCODING)){
            this.setCharset((String) headers.get(QRCode.ENCODING));
        } else {
            this.setCharset(params.getCharset());
        }
        
    }

    public ImageType getType() {
        return type;
    }

    public void setType(ImageType type) {
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

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }
    
}
