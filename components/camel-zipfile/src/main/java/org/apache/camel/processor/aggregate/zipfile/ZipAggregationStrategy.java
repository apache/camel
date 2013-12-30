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
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileMessage;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.FileUtil;

/**
 * This aggregation strategy will aggregate all incoming messages into a ZIP file.
 * <p>If the incoming exchanges contain {@link GenericFileMessage} file name will 
 * be taken from the body otherwise the body content will be treated as a byte 
 * array and the ZIP entry will be named using the message id.</p>
 * <p><b>Note:</b> Please note that this aggregation strategy requires eager 
 * completion check to work properly.</p>
 * 
 */
public class ZipAggregationStrategy implements AggregationStrategy {

    private String filePrefix;
    private String fileSuffix = ".zip";

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
            DefaultEndpoint endpoint = (DefaultEndpoint) newExchange.getFromEndpoint();
            answer = endpoint.createExchange();
            answer.addOnCompletion(new DeleteZipFileOnCompletion(zipFile));
        } else {
            zipFile = oldExchange.getIn().getBody(File.class);
        }
        
        // Handle GenericFileMessages
        if (GenericFileMessage.class.isAssignableFrom(newExchange.getIn().getClass())) {
            try {
                File appendFile =  newExchange.getIn().getBody(File.class);
                if (appendFile != null) {
                    addFilesToZip(zipFile, new File[]{appendFile});
                    GenericFile<File> genericFile = 
                        FileConsumer.asGenericFile(
                            zipFile.getParent(), 
                            zipFile, 
                            Charset.defaultCharset().toString());
                    genericFile.bindToExchange(answer);
                } else {
                    throw new GenericFileOperationFailedException("Could not get body as file.");
                }
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        } else {
            // Handle all other messages
            byte[] buffer = newExchange.getIn().getBody(byte[].class);
            try {
                addEntryToZip(zipFile, newExchange.getIn().getMessageId(), buffer, buffer.length);
                GenericFile<File> genericFile = FileConsumer.asGenericFile(
                    zipFile.getParent(), zipFile, Charset.defaultCharset().toString());
                genericFile.bindToExchange(answer);
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        }
        
        return answer;
    }
    
    private static void addFilesToZip(File source, File[] files) throws IOException {
        File tmpZip = File.createTempFile(source.getName(), null);
        tmpZip.delete();
        if (!source.renameTo(tmpZip)) {
            throw new IOException("Could not make temp file (" + source.getName() + ")");
        }
        byte[] buffer = new byte[1024];
        ZipInputStream zin = new ZipInputStream(new FileInputStream(tmpZip));
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(source));

        for (int i = 0; i < files.length; i++) {
            InputStream in = new FileInputStream(files[i]);
            out.putNextEntry(new ZipEntry(files[i].getName()));
            for (int read = in.read(buffer); read > -1; read = in.read(buffer)) {
                out.write(buffer, 0, read);
            }
            out.closeEntry();
            in.close();
        }

        for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
            out.putNextEntry(ze);
            for (int read = zin.read(buffer); read > -1; read = zin.read(buffer)) {
                out.write(buffer, 0, read);
            }
            out.closeEntry();
        }
        zin.close();
        out.close();
        tmpZip.delete();
    }
    
    private static void addEntryToZip(File source, String entryName, byte[] buffer, int length) throws IOException {

        File tmpZip = File.createTempFile(source.getName(), null);
        tmpZip.delete();
        if (!source.renameTo(tmpZip)) {
            throw new IOException("Could not make temp file (" + source.getName() + ")");
        }
        ZipInputStream zin = new ZipInputStream(new FileInputStream(tmpZip));
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(source));
        
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
        zin.close();
        out.close();
        tmpZip.delete();
    }
    
    /**
     * This callback class is used to clean up the temporary ZIP file once the exchange has completed.
     *
     */
    private class DeleteZipFileOnCompletion implements Synchronization {
        
        private File fileToDelete;
        
        public DeleteZipFileOnCompletion(File fileToDelete) {
            this.fileToDelete = fileToDelete;
        }
        
        @Override
        public void onFailure(Exchange exchange) {
            // Keep the file if somthing gone a miss.
        }
        
        @Override
        public void onComplete(Exchange exchange) {
            FileUtil.deleteFile(this.fileToDelete);
        }
    }
}
