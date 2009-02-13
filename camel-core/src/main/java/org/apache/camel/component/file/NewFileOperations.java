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
package org.apache.camel.component.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * File operations for {@link java.io.File}.
 */
public class NewFileOperations implements GenericFileOperations<File> {
    private static final transient Log LOG = LogFactory.getLog(NewFileOperations.class);
    private NewFileEndpoint endpoint;

    public NewFileOperations() {
    }

    public NewFileOperations(NewFileEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void setEndpoint(GenericFileEndpoint endpoint) {
        this.endpoint = (NewFileEndpoint) endpoint;
    }

    public boolean deleteFile(String name) throws GenericFileOperationFailedException {        
        File file = new File(name);
        return file.exists() && file.delete();
    }

    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        File file = new File(from);
        File target = new File(to);
        return file.renameTo(target);
    }

    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        ObjectHelper.notNull(endpoint, "endpoint");       

        // always create endpoint defined directory
        if (endpoint.isAutoCreate() && endpoint.isDirectory() && !endpoint.getFile().exists()) {
            endpoint.getFile().mkdirs();
        }

        File path;
        if (absolute) {
            path = new File(directory);
        } else {
            // skip trailing endpoint configued filename as we always start with the endoint file
            // for creating relative directories
            if (directory.startsWith(endpoint.getFile().getPath())) {
                directory = directory.substring(endpoint.getFile().getPath().length());
            }
            path = new File(endpoint.getFile(), directory);
        }       

        if (path.isDirectory() && path.exists()) {
            // the directory already exists
            return true;
        } else {
            return path.mkdirs();
        }
    }

    public List<File> listFiles() throws GenericFileOperationFailedException {
        // noop
        return null;
    }

    public List<File> listFiles(String path) throws GenericFileOperationFailedException {
        // noop
        return null;
    }

    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        // noop
    }

    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        // noop
        return null;
    }

    public boolean retrieveFile(String name, GenericFileExchange<File> exchange) throws GenericFileOperationFailedException {
        // noop as we use type converters to read the body content for java.io.File
        return true;
    }

    public boolean storeFile(String fileName, GenericFileExchange<File> exchange) throws GenericFileOperationFailedException {
        ObjectHelper.notNull(endpoint, "endpoint");
        
        File file = new File(fileName);
        try {
            File source = null;
            try {
                source = exchange.getIn().getBody(File.class);
            } catch (NoTypeConversionAvailableException e) {
                // ignore
            }
            if (source != null && source.exists()) {
                writeFileByFile(source, file);
            } else {
                InputStream in = ExchangeHelper.getMandatoryInBody(exchange, InputStream.class);
                writeFileByStream(in, file);
            }
        } catch (IOException e) {            
            throw new GenericFileOperationFailedException("Cannot store file: " + file, e);
        } catch (InvalidPayloadException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + file, e);
        }

        return true;
    }

    private void writeFileByFile(File source, File target) throws IOException {
        FileChannel in = new FileInputStream(source).getChannel();
        FileChannel out = null;
        try {
            out = prepareOutputFileChannel(target, out);

            if (LOG.isTraceEnabled()) {
                LOG.trace("Using FileChannel to transfer from: " + in + " to: " + out);
            }
            in.transferTo(0, in.size(), out);
        } finally {
            ObjectHelper.close(in, source.getName(), LOG);
            ObjectHelper.close(out, source.getName(), LOG);
        }
    }

    private void writeFileByStream(InputStream in, File target) throws IOException {
        FileChannel out = null;
        try {
            out = prepareOutputFileChannel(target, out);

            if (LOG.isTraceEnabled()) {
                LOG.trace("Using InputStream to transfer from: " + in + " to: " + out);
            }
            int size = endpoint.getBufferSize();
            byte[] buffer = new byte[size];
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            while (true) {
                int count = in.read(buffer);
                if (count <= 0) {
                    break;
                } else if (count < size) {
                    byteBuffer = ByteBuffer.wrap(buffer, 0, count);
                    out.write(byteBuffer);
                    break;
                } else {
                    out.write(byteBuffer);
                    byteBuffer.clear();
                }
            }
        } finally {
            ObjectHelper.close(in, target.getName(), LOG);
            ObjectHelper.close(out, target.getName(), LOG);
        }
    }

    /**
     * Creates and prepares the output file channel. Will position itself in correct position if eg. it should append
     * or override any existing content.
     */
    private FileChannel prepareOutputFileChannel(File target, FileChannel out) throws IOException {
        if (endpoint.isAppend()) {
            out = new RandomAccessFile(target, "rw").getChannel();
            out = out.position(out.size());
        } else {
            out = new FileOutputStream(target).getChannel();
        }
        return out;
    }

}
