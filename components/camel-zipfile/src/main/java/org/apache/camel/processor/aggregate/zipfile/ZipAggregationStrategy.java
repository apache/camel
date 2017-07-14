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
package org.apache.camel.processor.aggregate.zipfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileMessage;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;

/**
 * This aggregation strategy will aggregate all incoming messages into a ZIP file.
 * <p>If the incoming exchanges contain {@link GenericFileMessage} file name will 
 * be taken from the body otherwise the body content will be treated as a byte 
 * array and the ZIP entry will be named using the message id (unless the flag
 * useFilenameHeader is set to true.</p>
 * <p><b>Note:</b> Please note that this aggregation strategy requires eager 
 * completion check to work properly.</p>
 */
public class ZipAggregationStrategy implements AggregationStrategy {

    private String filePrefix;
    private String fileSuffix = ".zip";
    private boolean preserveFolderStructure;
    private boolean useFilenameHeader;

    public ZipAggregationStrategy() {
        this(false, false);
    }

    /**
     * @param preserveFolderStructure if true, the folder structure is preserved when the source is
     * a type of {@link GenericFileMessage}.  If used with a file, use recursive=true.
     */
    public ZipAggregationStrategy(boolean preserveFolderStructure) {
        this(preserveFolderStructure, false);
    }
    
    /**
     * @param preserveFolderStructure if true, the folder structure is preserved when the source is
     * a type of {@link GenericFileMessage}.  If used with a file, use recursive=true.
     * @param useFilenameHeader if true, the filename header will be used to name aggregated byte arrays
     * within the ZIP file.
     */
    public ZipAggregationStrategy(boolean preserveFolderStructure, boolean useFilenameHeader) {
        this.preserveFolderStructure = preserveFolderStructure;
        this.useFilenameHeader = useFilenameHeader;
    }
    
    /**
     * Gets the prefix used when creating the ZIP file name.
     * @return the prefix
     */
    public String getFilePrefix() {
        return filePrefix;
    }

    /**
     * Sets the prefix that will be used when creating the ZIP filename.
     * @param filePrefix prefix to use on ZIP file.
     */
    public void setFilePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
    }
    
    /**
     * Gets the suffix used when creating the ZIP file name.
     * @return the suffix
     */
    public String getFileSuffix() {
        return fileSuffix;
    }

    /**
     * Sets the suffix that will be used when creating the ZIP filename.
     * @param fileSuffix suffix to use on ZIP file.
     */
    public void setFileSuffix(String fileSuffix) {
        this.fileSuffix = fileSuffix;
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        File zipFile;
        Exchange answer = oldExchange;
        
        // Guard against empty new exchanges
        if (newExchange == null) {
            return oldExchange;
        }
    
        // First time for this aggregation
        if (oldExchange == null) {
            try {
                zipFile = FileUtil.createTempFile(this.filePrefix, this.fileSuffix);
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
            answer = newExchange;
            answer.addOnCompletion(new DeleteZipFileOnCompletion(zipFile));
        } else {
            zipFile = oldExchange.getIn().getBody(File.class);
        }

        Object body = newExchange.getIn().getBody();
        if (body instanceof WrappedFile) {
            body = ((WrappedFile) body).getFile();
        }
        
        if (body instanceof File) {
            try {
                File appendFile = (File) body;
                // do not try to append empty files
                if (appendFile.length() > 0) {
                    String entryName = preserveFolderStructure ? newExchange.getIn().getHeader(Exchange.FILE_NAME, String.class) : newExchange.getIn().getMessageId();
                    addFileToZip(zipFile, appendFile, this.preserveFolderStructure ? entryName : null);
                    GenericFile<File> genericFile = 
                        FileConsumer.asGenericFile(
                            zipFile.getParent(), zipFile, Charset.defaultCharset().toString(), false);
                    genericFile.bindToExchange(answer);
                }
            } catch (Exception e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        } else {
            // Handle all other messages
            try {
                byte[] buffer = newExchange.getIn().getMandatoryBody(byte[].class);
                // do not try to append empty data
                if (buffer.length > 0) {
                    String entryName = useFilenameHeader ? newExchange.getIn().getHeader(Exchange.FILE_NAME, String.class) : newExchange.getIn().getMessageId();
                    addEntryToZip(zipFile, entryName, buffer, buffer.length);
                    GenericFile<File> genericFile = FileConsumer.asGenericFile(
                            zipFile.getParent(), zipFile, Charset.defaultCharset().toString(), false);
                    genericFile.bindToExchange(answer);
                }
            } catch (Exception e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        }
        
        return answer;
    }
    
    private static void addFileToZip(File source, File file, String fileName) throws IOException {
        File tmpZip = File.createTempFile(source.getName(), null);
        tmpZip.delete();
        if (!source.renameTo(tmpZip)) {
            throw new IOException("Could not make temp file (" + source.getName() + ")");
        }
        byte[] buffer = new byte[8192];

        FileInputStream fis = new FileInputStream(tmpZip);
        ZipInputStream zin = new ZipInputStream(fis);
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(source));

        try {
            InputStream in = new FileInputStream(file);
            out.putNextEntry(new ZipEntry(fileName == null ? file.getName() : fileName));
            for (int read = in.read(buffer); read > -1; read = in.read(buffer)) {
                out.write(buffer, 0, read);
            }
            out.closeEntry();
            IOHelper.close(in);

            for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
                out.putNextEntry(ze);
                for (int read = zin.read(buffer); read > -1; read = zin.read(buffer)) {
                    out.write(buffer, 0, read);
                }
                out.closeEntry();
            }
        } finally {
            IOHelper.close(fis, zin, out);
        }
        tmpZip.delete();
    }
    
    private static void addEntryToZip(File source, String entryName, byte[] buffer, int length) throws IOException {
        File tmpZip = File.createTempFile(source.getName(), null);
        tmpZip.delete();
        if (!source.renameTo(tmpZip)) {
            throw new IOException("Cannot create temp file: " + source.getName());
        }

        FileInputStream fis = new FileInputStream(tmpZip);
        ZipInputStream zin = new ZipInputStream(fis);
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(source));
        try {
            out.putNextEntry(new ZipEntry(entryName));
            out.write(buffer, 0, length);
            out.closeEntry();

            for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
                out.putNextEntry(ze);
                for (int read = zin.read(buffer); read > -1; read = zin.read(buffer)) {
                    out.write(buffer, 0, read);
                }
                out.closeEntry();
            }
        } finally {
            IOHelper.close(fis, zin, out);
        }
        tmpZip.delete();
    }
    
    /**
     * This callback class is used to clean up the temporary ZIP file once the exchange has completed.
     */
    private class DeleteZipFileOnCompletion implements Synchronization {
        
        private final File fileToDelete;
        
        DeleteZipFileOnCompletion(File fileToDelete) {
            this.fileToDelete = fileToDelete;
        }
        
        @Override
        public void onFailure(Exchange exchange) {
            // Keep the file if something gone a miss.
        }
        
        @Override
        public void onComplete(Exchange exchange) {
            FileUtil.deleteFile(this.fileToDelete);
        }
    }
}
