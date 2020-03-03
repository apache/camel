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
package org.apache.camel.processor.aggregate.zipfile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileMessage;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.FileUtil;

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
    private boolean useTempFile;
    private File parentDir = new File(System.getProperty("java.io.tmpdir"));

    public ZipAggregationStrategy() {
        this(false);
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
        this(preserveFolderStructure, useFilenameHeader, false);
    }

    /**
     * @param preserveFolderStructure if true, the folder structure is preserved when the source is
     * a type of {@link GenericFileMessage}.  If used with a file, use recursive=true.
     * @param useFilenameHeader if true, the filename header will be used to name aggregated byte arrays
     * within the ZIP file.
     * @param useTempFile if true, the ZipFileSystem will use temporary files for zip manipulations instead of memory.
     */
    public ZipAggregationStrategy(boolean preserveFolderStructure, boolean useFilenameHeader, boolean useTempFile) {
        this.preserveFolderStructure = preserveFolderStructure;
        this.useFilenameHeader = useFilenameHeader;
        this.useTempFile = useTempFile;
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

    public File getParentDir() {
        return parentDir;
    }

    /**
     * Sets the parent directory to use for writing temporary files.
     */
    public void setParentDir(File parentDir) {
        this.parentDir = parentDir;
    }

    /**
     * Sets the parent directory to use for writing temporary files.
     */
    public void setParentDir(String parentDir) {
        this.parentDir = new File(parentDir);
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
                zipFile = FileUtil.createTempFile(this.filePrefix, this.fileSuffix, this.parentDir);
                newZipFile(zipFile);
            } catch (IOException | URISyntaxException e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
            answer = newExchange;
            answer.adapt(ExtendedExchange.class).addOnCompletion(new DeleteZipFileOnCompletion(zipFile));
        } else {
            zipFile = oldExchange.getIn().getBody(File.class);
        }
        Object body = newExchange.getIn().getBody();
        if (body instanceof WrappedFile) {
            body = ((WrappedFile) body).getFile();
        }

        String charset = ExchangeHelper.getCharsetName(newExchange, true);

        if (body instanceof File) {
            try {
                File appendFile = (File) body;
                // do not try to append empty files
                if (appendFile.length() > 0) {
                    String entryName = preserveFolderStructure ? newExchange.getIn().getHeader(Exchange.FILE_NAME, String.class) : newExchange.getIn().getMessageId();
                    addFileToZip(zipFile, appendFile, this.preserveFolderStructure ? entryName : null);
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
                    addEntryToZip(zipFile, entryName, buffer, charset);
                }
            } catch (Exception e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        }

        GenericFile<File> genericFile = FileConsumer.asGenericFile(zipFile.getParent(), zipFile, charset, false);
        genericFile.bindToExchange(answer);

        return answer;
    }

    private static void newZipFile(File zipFile) throws URISyntaxException, IOException {
        if (zipFile.exists() && !zipFile.delete()) { //Delete, because ZipFileSystem needs to create file on its own (with correct END bytes in the file)
            throw new IOException("Cannot delete file " + zipFile);
        }
        Map<String, Object> env = new HashMap<>();
        env.put("create", Boolean.TRUE.toString()); //Intentionally String, it is implemented this way in ZipFileSystem

        try (FileSystem ignored = FileSystems.newFileSystem(getZipURI(zipFile), env)) {
            //noop, just open and close FileSystem to initialize correct headers in file
        }
    }

    private void addFileToZip(File zipFile, File file, String fileName) throws IOException, URISyntaxException {
        String entryName = fileName == null ? file.getName() : fileName;
        Map<String, Object> env = new HashMap<>();
        env.put("useTempFile", this.useTempFile); //Intentionally boolean, it is implemented this way in ZipFileSystem
        try (FileSystem fs = FileSystems.newFileSystem(getZipURI(zipFile), env)) {
            Path dest = fs.getPath("/", entryName);
            Files.createDirectories(dest.getParent());
            Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void addEntryToZip(File zipFile, String entryName, byte[] buffer, String charset) throws IOException, URISyntaxException {
        Map<String, Object> env = new HashMap<>();
        env.put("encoding", charset);
        env.put("useTempFile", this.useTempFile); //Intentionally boolean, it is implemented this way in ZipFileSystem
        try (FileSystem fs = FileSystems.newFileSystem(getZipURI(zipFile), env)) {
            Path dest = fs.getPath("/", entryName);
            Files.createDirectories(dest.getParent());
            Files.write(dest, buffer, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static URI getZipURI(File zipFile) throws URISyntaxException {
        return new URI("jar", zipFile.toURI().toString(),  null);
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
