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
import com.google.zxing.datamatrix.DataMatrixReader;
import com.google.zxing.datamatrix.DataMatrixWriter;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author claus.straube
 */
public class DataMatrixDataFormat extends CodeDataFormat {

    private static final Logger LOG = LoggerFactory.getLogger(DataMatrixDataFormat.class);
    
    /**
     * The barcode format. Default is QR-Code.
     */
    private final BarcodeFormat format = BarcodeFormat.DATA_MATRIX;
    
    /**
     * The {@link DataMatrixReader} instance.
     */
    private final DataMatrixReader reader = new DataMatrixReader();
    
    /**
     * The {@link DataMatrixWriter} instance.
     */
    private final DataMatrixWriter writer = new DataMatrixWriter();
    
    /**
     * {@inheritDoc}
     */
    public DataMatrixDataFormat(boolean parameterized) {
        super(parameterized);
    }

    /**
     * {@inheritDoc}
     */
    public DataMatrixDataFormat(int height, int width, boolean parameterized) {
        super(height, width, parameterized);
    }

    /**
     * {@inheritDoc}
     */
    public DataMatrixDataFormat(ImageType type, boolean parameterized) {
        super(type, parameterized);
    }

    /**
     * {@inheritDoc}
     */
    public DataMatrixDataFormat(int height, int width, ImageType type, boolean parameterized) {
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
