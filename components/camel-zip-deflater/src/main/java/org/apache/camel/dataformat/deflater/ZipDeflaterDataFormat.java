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
package org.apache.camel.dataformat.deflater;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;

/**
 * Deflate (zip) compression data format (does not support zip files, instead use zipfile dataformat).
 */
@Dataformat("zipdeflater")
public class ZipDeflaterDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private int compressionLevel;

    public ZipDeflaterDataFormat() {
        this.compressionLevel = Deflater.DEFAULT_COMPRESSION;
    }

    public ZipDeflaterDataFormat(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    @Override
    public String getDataFormatName() {
        return "zipdeflater";
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        // ask for a mandatory type conversion to avoid a possible NPE beforehand as we do copy from the InputStream
        final InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, graph);

        final Deflater deflater = new Deflater(compressionLevel);
        final DeflaterOutputStream zipOutput = new DeflaterOutputStream(stream, deflater);
        try {
            IOHelper.copy(is, zipOutput);
        } finally {
            IOHelper.close(is, zipOutput);
            
            /*
            * As we create the Deflater our self and do not use the stream default
            * (see {@link java.util.zip.DeflaterOutputStream#usesDefaultDeflater})
            * we need to close the Deflater to not risk a OutOfMemoryException
            * in native code parts (see {@link java.util.zip.Deflater#end})
            */
            deflater.end();
        }
    }

    @Override
    public Object unmarshal(final Exchange exchange, final InputStream inputStream) throws Exception {
        InflaterInputStream inflaterInputStream = new InflaterInputStream(inputStream);
        OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);

        try {
            IOHelper.copy(inflaterInputStream, osb);
            return osb.build();
        } finally {
            // must close input streams
            IOHelper.close(osb, inflaterInputStream, inputStream);
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
