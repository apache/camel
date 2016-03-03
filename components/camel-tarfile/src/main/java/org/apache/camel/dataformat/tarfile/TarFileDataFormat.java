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
package org.apache.camel.dataformat.tarfile;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;

import org.apache.camel.Exchange;
import org.apache.camel.converter.stream.OutputStreamBuilder;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import static org.apache.camel.Exchange.FILE_LENGTH;
import static org.apache.camel.Exchange.FILE_NAME;

/**
 * Tar file data format.
 * Based on ZipFileDataFormat from camel-zipfile component
 */
public class TarFileDataFormat extends ServiceSupport implements DataFormat, DataFormatName {
    private boolean usingIterator;

    @Override
    public String getDataFormatName() {
        return "tarfile";
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        String filename = exchange.getIn().getHeader(FILE_NAME, String.class);
        Long filelength = exchange.getIn().getHeader(FILE_LENGTH, Long.class);
        if (filename == null) {
            // generate the file name as the camel file component would do
            filename = StringHelper.sanitize(exchange.getIn().getMessageId());
        } else {
            filename = Paths.get(filename).getFileName().toString(); // remove any path elements
        }

        TarArchiveOutputStream tos = new TarArchiveOutputStream(stream);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

        InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, graph);
        if (filelength == null) {
            filelength = (long) is.available();
        }

        TarArchiveEntry entry = new TarArchiveEntry(filename);
        entry.setSize(filelength);
        tos.putArchiveEntry(entry);

        try {
            IOHelper.copy(is, tos);
        } finally {
            tos.closeArchiveEntry();
            IOHelper.close(is, tos);
        }

        String newFilename = filename + ".tar";
        exchange.getOut().setHeader(FILE_NAME, newFilename);
    }

    @Override
    public Object unmarshal(final Exchange exchange, final InputStream stream) throws Exception {
        if (usingIterator) {
            return new TarIterator(exchange, stream);
        } else {
            BufferedInputStream bis = new BufferedInputStream(stream);
            TarArchiveInputStream tis = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, bis);
            OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);

            try {
                TarArchiveEntry entry = tis.getNextTarEntry();
                if (entry != null) {
                    exchange.getOut().setHeader(FILE_NAME, entry.getName());
                    IOHelper.copy(tis, osb);
                }

                entry = tis.getNextTarEntry();
                if (entry != null) {
                    throw new IllegalStateException("Tar file has more than 1 entry.");
                }

                return osb.build();

            } finally {
                IOHelper.close(osb, tis, bis);
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
