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

import java.io.InputStream;
import java.io.OutputStream;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;

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
    private void setDefaultParameters() {
        this.params = new Parameters(ImageType.PNG, 100, 100, "UTF-8");
    }

    public Parameters getParams() {
        return params;
    }

    public boolean isParameterized() {
        return parameterized;
    }
}
