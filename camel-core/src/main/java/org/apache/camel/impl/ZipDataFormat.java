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
package org.apache.camel.impl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.util.IOHelper;

/**
 * "Deflate" compression data format.
 * See {@link org.apache.camel.model.dataformat.ZipFileDataFormat} for Zip file compression.
 */
public class ZipDataFormat extends org.apache.camel.support.ServiceSupport implements DataFormat, DataFormatName {

    private int compressionLevel;

    public ZipDataFormat() {
        this.compressionLevel = Deflater.BEST_SPEED;
    }

    public ZipDataFormat(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    @Override
    public String getDataFormatName() {
        return "zip";
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        // ask for a mandatory type conversion to avoid a possible NPE beforehand as we do copy from the InputStream
        InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, graph);

        DeflaterOutputStream zipOutput = new DeflaterOutputStream(stream, new Deflater(compressionLevel));
        try {
            IOHelper.copy(is, zipOutput);
        } finally {
            IOHelper.close(is, zipOutput);
        }
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        InputStream is = exchange.getIn().getMandatoryBody(InputStream.class);
        InflaterInputStream unzipInput = new InflaterInputStream(is);

        // Create an expandable byte array to hold the inflated data
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            IOHelper.copy(unzipInput, bos);
            return bos.toByteArray();
        } finally {
            // must close input streams
            IOHelper.close(is, unzipInput);
        }
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
