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
package org.apache.camel.itest.springboot.arquillian;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.exporter.ArchiveExportException;
import org.jboss.shrinkwrap.impl.base.io.IOUtil;
import org.jboss.shrinkwrap.impl.base.path.PathUtil;

/**
 * A spring-boot compatible on-demand input stream.
 * It does not compress jar entries for spring-boot nested jar structure compatibility.
 */
public class SpringBootZipOnDemandInputStream extends InputStream {

    /**
     * Created by abstract method.
     */
    protected ZipOutputStream outputStream;

    /**
     * Iterator over nodes contained in base archive.
     */
    private final Iterator<Node> nodesIterator;

    /**
     * Base for outputStream.
     */
    private final ByteArrayOutputStream bufferedOutputStream = new ByteArrayOutputStream();

    /**
     * Stream of currently processed Node.
     */
    private InputStream currentNodeStream;

    /**
     * Stream to the buffer.
     */
    private ByteArrayInputStream bufferInputStream;

    /**
     * If output stream was closed - we should finish.
     */
    private boolean outputStreamClosed;

    /**
     * Currently processed archive path - for displaying exception.
     */
    private ArchivePath currentPath;

    /**
     * Creates stream directly from archive.
     *
     * @param archive
     */
    public SpringBootZipOnDemandInputStream(final Archive<?> archive) {
        final Collection<Node> nodes = archive.getContent().values();
        this.nodesIterator = nodes.iterator();
    }

    @Override
    public int read() throws IOException {

        if (outputStream == null && !outputStreamClosed) {
            // first run
            outputStream = createOutputStream(bufferedOutputStream);
        }

        int value = bufferInputStream != null ? bufferInputStream.read() : -1;
        if (value == -1) {
            if (currentNodeStream != null) {
                // current node was not processed completely
                try {
                    doCopy();
                    bufferInputStream = new ByteArrayInputStream(bufferedOutputStream.toByteArray());
                    bufferedOutputStream.reset();
                    return this.read();
                } catch (final Throwable t) {
                    throw new ArchiveExportException("Failed to write asset to output: " + currentPath.get(), t);
                }
            } else if (nodesIterator.hasNext()) {
                // current node was processed completely, process next one
                final Node currentNode = nodesIterator.next();

                currentPath = currentNode.getPath();
                final String pathName = PathUtil.optionallyRemovePrecedingSlash(currentPath.get());

                final boolean isDirectory = currentNode.getAsset() == null;
                String resolvedPath = pathName;

                if (isDirectory) {
                    resolvedPath = PathUtil.optionallyAppendSlash(resolvedPath);
                    startAsset(resolvedPath, 0L, 0L);
                    endAsset();
                } else {

                    try {
                        byte[] content = IOUtil.asByteArray(currentNode.getAsset().openStream());
                        long size = content.length;
                        CRC32 crc = new CRC32();
                        crc.update(content);
                        long crc32Value = crc.getValue();
                        startAsset(resolvedPath, size, crc32Value);

                        currentNodeStream = new ByteArrayInputStream(content);
                        doCopy();
                    } catch (final Throwable t) {
                        throw new ArchiveExportException("Failed to write asset to output: " + currentPath.get(), t);
                    }
                    bufferInputStream = new ByteArrayInputStream(bufferedOutputStream.toByteArray());
                    bufferedOutputStream.reset();
                }

            } else {
                // each node was processed
                if (!outputStreamClosed) {
                    outputStream.close();
                    outputStreamClosed = true;

                    // output closed, now process what was saved on close
                    bufferInputStream = new ByteArrayInputStream(bufferedOutputStream.toByteArray());
                    bufferedOutputStream.close();

                    currentNodeStream = null;
                    outputStream = null;
                    return this.read();
                }

                // everything was read, end
                return -1;
            }

            // chosen new node or new data in buffer - read again
            return this.read();
        }

        return value;
    }

    /**
     * Performs copy operation between currentNodeStream and outputStream using buffer length.
     *
     * @throws IOException
     */
    private void doCopy() throws IOException {
        IOUtil.copy(currentNodeStream, outputStream);
        currentNodeStream.close();
        currentNodeStream = null;
        endAsset();
    }

    /**
     * Start entry in stream.
     *
     * @param path
     * @throws IOException
     */
    private void startAsset(final String path, long size, long crc32) throws IOException {
        putNextEntry(outputStream, path, size, crc32);
    }

    /**
     * Close entry in stream.
     *
     * @throws IOException
     */
    private void endAsset() throws IOException {
        closeEntry(outputStream);
    }


    protected ZipOutputStream createOutputStream(final OutputStream outputStream) {
        ZipOutputStream stream = new ZipOutputStream(outputStream);
        stream.setMethod(ZipEntry.STORED);
        stream.setLevel(Deflater.NO_COMPRESSION);
        return stream;
    }


    protected void closeEntry(final ZipOutputStream outputStream) throws IOException {
        outputStream.closeEntry();
    }


    protected void putNextEntry(final ZipOutputStream outputStream, final String context, long size, long crc32) throws IOException {

        ZipEntry entry = new ZipEntry(context);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(size);
        entry.setCrc(crc32);

        outputStream.putNextEntry(entry);
    }


}
