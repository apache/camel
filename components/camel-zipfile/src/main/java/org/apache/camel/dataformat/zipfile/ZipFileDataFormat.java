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
package org.apache.camel.dataformat.zipfile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.converter.stream.OutputStreamBuilder;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.Exchange.FILE_NAME;

/**
 * Zip file data format.
 * See {@link org.apache.camel.model.dataformat.ZipDataFormat} for "deflate" compression.
 */
public class ZipFileDataFormat extends ServiceSupport implements DataFormat, DataFormatName {
    private boolean usingIterator;

    @Override
    public String getDataFormatName() {
        return "zipFile";
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        String filename = exchange.getIn().getHeader(FILE_NAME, String.class);
        if (filename == null) {
            // generate the file name as the camel file component would do
            filename = StringHelper.sanitize(exchange.getIn().getMessageId());
        } else {
            filename = Paths.get(filename).getFileName().toString(); // remove any path elements
        }

        ZipOutputStream zos = new ZipOutputStream(stream);
        zos.putNextEntry(new ZipEntry(filename));

        InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, graph);

        try {
            IOHelper.copy(is, zos);
        } finally {
            IOHelper.close(is, zos);
        }

        String newFilename = filename + ".zip";
        exchange.getOut().setHeader(FILE_NAME, newFilename);
    }

    @Override
    public Object unmarshal(final Exchange exchange, final InputStream inputStream) throws Exception {
        if (usingIterator) {
            return new ZipIterator(exchange.getIn());
        } else {
            ZipInputStream zis = new ZipInputStream(inputStream);
            OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);

            try {
                ZipEntry entry = zis.getNextEntry();
                if (entry != null) {
                    exchange.getOut().setHeader(FILE_NAME, entry.getName());
                    IOHelper.copy(zis, osb);
                }

                entry = zis.getNextEntry();
                if (entry != null) {
                    throw new IllegalStateException("Zip file has more than 1 entry.");
                }

                return osb.build();
            } finally {
                IOHelper.close(zis, osb);
            }
        }
    }

    public boolean isUsingIterator() {
        return usingIterator;
    }

    public void setUsingIterator(boolean usingIterator) {
        this.usingIterator = usingIterator;
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