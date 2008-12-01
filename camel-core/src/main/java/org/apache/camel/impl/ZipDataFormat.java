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
import org.apache.camel.converter.IOConverter;
import org.apache.camel.spi.DataFormat;

public class ZipDataFormat implements DataFormat {

    private static final int INITIALBYTEARRAYSIZE = 1048576;
    private int compressionLevel;

    public ZipDataFormat() {
        this.compressionLevel = Deflater.BEST_SPEED;
    }

    public ZipDataFormat(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public void marshal(Exchange exchange, Object body, OutputStream stream)
        throws Exception {
        InputStream is;
        if (body instanceof InputStream) {
            is = (InputStream)body;
        }
        is = exchange.getIn().getBody(InputStream.class);
        if (is == null) {
            throw new IllegalArgumentException("Can't get the inputstream for ZipDataFormat mashalling");
        }
       
        DeflaterOutputStream zipOutput = new DeflaterOutputStream(stream, new Deflater(compressionLevel));
        
        // Compress the data
        IOConverter.copy(is, zipOutput);
        zipOutput.close();

    }

    public Object unmarshal(Exchange exchange, InputStream stream)
        throws Exception {
        
        InputStream is = stream;
        if (is == null) {           
            exchange.getIn().getBody(InputStream.class);
        }
        if (is == null) {
            throw new IllegalArgumentException("Can't get the inputStream for ZipDataFormat unmashalling");
        }
        
        InflaterInputStream unzipInput = new InflaterInputStream(is);
        
        // Create an expandable byte array to hold the inflated data
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        IOConverter.copy(unzipInput, bos);        

        return bos.toByteArray();
    }

}
