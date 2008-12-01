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
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import javax.xml.bind.annotation.XmlAttribute;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
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

    public void marshal(Exchange exchange, Object graph, OutputStream stream)
        throws Exception {
        
        // Retrieve the message body as byte array 
        byte[] input = (byte[]) exchange.getIn().getBody(byte[].class);
        
        // Create a Message Deflater
        Deflater deflater = new Deflater(compressionLevel);
        deflater.setInput(input);
        deflater.finish();        
        
        // Compress the data
        byte[] output = new byte[INITIALBYTEARRAYSIZE];
        while (!deflater.finished()) {
            int count = deflater.deflate(output);
            stream.write(output, 0, count);
        }

    }

    public Object unmarshal(Exchange exchange, InputStream stream)
        throws Exception {

        // Retrieve the message body as byte array 
        byte[] input = (byte[]) exchange.getIn().getBody(byte[].class);
        
        // Create a Message Inflater        
        Inflater inflater = new Inflater();
        inflater.setInput(input);

        // Create an expandable byte array to hold the inflated data
        ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length);
        
        // Inflate the compressed data
        byte[] buf = new byte[INITIALBYTEARRAYSIZE];
        while (!inflater.finished()) {
            int count = inflater.inflate(buf);
            bos.write(buf, 0, count);
        }    

        // Return the inflated data
        return bos.toByteArray();
    }

}
