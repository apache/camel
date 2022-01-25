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
package org.apache.camel.dataformat.zipfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.Exchange.FILE_NAME;

/**
 * Zip file data format. See {@link org.apache.camel.model.dataformat.ZipDataFormat} for "deflate" compression.
 */
@Dataformat("zipFile")
public class ZipFileDataFormat extends ServiceSupport implements DataFormat, DataFormatName {
    /**
     * The default maximum decompressed size (in bytes), which corresponds to 1G.
     */
    private static final long DEFAULT_MAXIMUM_DECOMPRESSED_SIZE = 1073741824;
    private boolean usingIterator;
    private boolean allowEmptyDirectory;
    private boolean preservePathElements;
    private long maxDecompressedSize = DEFAULT_MAXIMUM_DECOMPRESSED_SIZE;

    @Override
    public String getDataFormatName() {
        return "zipFile";
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        String filename;
        String filepath = exchange.getIn().getHeader(FILE_NAME, String.class);
        if (filepath == null) {
            // generate the file name as the camel file component would do
            filename = filepath = StringHelper.sanitize(exchange.getIn().getMessageId());
        } else {
            Path filenamePath = Paths.get(filepath).getFileName();
            if (filenamePath != null) {
                filename = filenamePath.toString(); // remove any path elements
            } else {
                // TODO do some logging
                return;
            }
        }

        ZipOutputStream zos = new ZipOutputStream(stream);

        if (preservePathElements) {
            createZipEntries(zos, filepath);
        } else {
            createZipEntries(zos, filename);
        }

        InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, graph);

        try {
            IOHelper.copy(is, zos);
        } finally {
            IOHelper.close(is, zos);
        }

        String newFilename = filename + ".zip";
        exchange.getMessage().setHeader(FILE_NAME, newFilename);
    }

    @Override
    public Object unmarshal(final Exchange exchange, final InputStream inputStream) throws Exception {
        if (usingIterator) {
            ZipIterator zipIterator = new ZipIterator(exchange, inputStream);
            zipIterator.setAllowEmptyDirectory(allowEmptyDirectory);
            return zipIterator;
        } else {
            ZipInputStream zis = new ZipInputStream(inputStream);
            OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);

            try {
                ZipEntry entry = zis.getNextEntry();
                if (entry != null) {
                    exchange.getMessage().setHeader(FILE_NAME, entry.getName());
                    IOHelper.copy(zis, osb, IOHelper.DEFAULT_BUFFER_SIZE, false, maxDecompressedSize);
                } else {
                    throw new IllegalStateException("Unable to unzip the file, it may be corrupted.");
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

    private void createZipEntries(ZipOutputStream zos, String filepath) throws IOException {
        Iterator<Path> elements = Paths.get(filepath).iterator();
        StringBuilder sb = new StringBuilder();

        while (elements.hasNext()) {
            Path path = elements.next();
            String element = path.toString();

            // If there are more elements to come this element is a directory
            // The "/" at the end tells the ZipEntry it is a folder
            if (elements.hasNext()) {
                element += "/";
            }

            // Each entry needs the complete path, including previous created folders.
            zos.putNextEntry(new ZipEntry(sb + element));

            sb.append(element);
        }
    }

    public boolean isUsingIterator() {
        return usingIterator;
    }

    public void setUsingIterator(boolean usingIterator) {
        this.usingIterator = usingIterator;
    }

    public boolean isAllowEmptyDirectory() {
        return allowEmptyDirectory;
    }

    public void setAllowEmptyDirectory(boolean allowEmptyDirectory) {
        this.allowEmptyDirectory = allowEmptyDirectory;
    }

    public boolean isPreservePathElements() {
        return preservePathElements;
    }

    public void setPreservePathElements(boolean preservePathElements) {
        this.preservePathElements = preservePathElements;
    }

    public long getMaxDecompressedSize() {
        return maxDecompressedSize;
    }

    public void setMaxDecompressedSize(long maxDecompressedSize) {
        this.maxDecompressedSize = maxDecompressedSize;
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
