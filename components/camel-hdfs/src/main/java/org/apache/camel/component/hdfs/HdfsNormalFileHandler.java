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
package org.apache.camel.component.hdfs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.IOHelper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;

class HdfsNormalFileHandler extends DefaultHdfsFile<OutputStream, InputStream> {

    private boolean consumed;

    @Override
    public OutputStream createOutputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        try {
            OutputStream outputStream;
            HdfsInfo hdfsInfo = hdfsInfoFactory.newHdfsInfo(hdfsPath);
            HdfsConfiguration endpointConfig = hdfsInfoFactory.getEndpointConfig();
            if (endpointConfig.isAppend()) {
                outputStream = hdfsInfo.getFileSystem().append(
                        hdfsInfo.getPath(),
                        endpointConfig.getBufferSize(),
                    () -> { }
                );
            } else {
                outputStream = hdfsInfo.getFileSystem().create(
                        hdfsInfo.getPath(),
                        endpointConfig.isOverwrite(),
                        endpointConfig.getBufferSize(),
                        endpointConfig.getReplication(),
                        endpointConfig.getBlockSize(),
                    () -> { }
                );
            }
            return outputStream;
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public long append(HdfsOutputStream hdfsOutputStream, Object key, Object value, Exchange exchange) {
        InputStream inputStream = null;
        try {
            inputStream = exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, value);
            return copyBytes(inputStream, (FSDataOutputStream) hdfsOutputStream.getOut(), HdfsConstants.DEFAULT_BUFFERSIZE, false);
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        } finally {
            IOHelper.close(inputStream);
        }
    }

    @Override
    public InputStream createInputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        try {
            InputStream inputStream;
            HdfsConfiguration endpointConfig = hdfsInfoFactory.getEndpointConfig();
            if (endpointConfig.getFileSystemType().equals(HdfsFileSystemType.LOCAL)) {
                HdfsInfo hdfsInfo = hdfsInfoFactory.newHdfsInfo(hdfsPath);
                inputStream = hdfsInfo.getFileSystem().open(hdfsInfo.getPath());
            } else {
                inputStream = new FileInputStream(getHdfsFileToTmpFile(hdfsPath, endpointConfig));
            }
            return inputStream;
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public long next(HdfsInputStream hdfsInputStream, Holder<Object> key, Holder<Object> value) {
        if (hdfsInputStream.isStreamDownload()) {
            return nextAsWrappedStream(hdfsInputStream, key, value);
        } else {
            return nextAsOutputStream(hdfsInputStream, key, value);
        }
    }

    private long nextAsWrappedStream(HdfsInputStream hdfsInputStream, Holder<Object> key, Holder<Object> value) {
        InputStream inputStream = (InputStream) hdfsInputStream.getIn();
        value.setValue(inputStream);

        if (consumed) {
            return 0;
        } else {
            consumed = true;
            return 1;
        }
    }

    private long nextAsOutputStream(HdfsInputStream hdfsInputStream, Holder<Object> key, Holder<Object> value) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(hdfsInputStream.getChunkSize());
            byte[] buf = new byte[hdfsInputStream.getChunkSize()];
            int bytesRead = ((InputStream) hdfsInputStream.getIn()).read(buf);
            if (bytesRead >= 0) {
                outputStream.write(buf, 0, bytesRead);
                value.setValue(outputStream);
                return bytesRead;
            } else {
                // indication that we may have read from empty file
                value.setValue(outputStream);
                return 0;
            }
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    private File getHdfsFileToTmpFile(String hdfsPath, HdfsConfiguration configuration) {
        try {
            String fileName = hdfsPath.substring(hdfsPath.lastIndexOf('/'));

            // [CAMEL-13711] Files.createTempFile not equivalent to File.createTempFile
            File outputDest;
            try {
                // First trying: Files.createTempFile
                outputDest = Files.createTempFile(fileName, ".hdfs").toFile();

            } catch (Exception ex) {
                // Now trying: File.createTempFile
                outputDest = File.createTempFile(fileName, ".hdfs");
            }

            if (outputDest.exists()) {
                outputDest.delete();
            }

            HdfsInfoFactory hdfsInfoFactory = new HdfsInfoFactory(configuration);
            HdfsInfo hdfsInfo = hdfsInfoFactory.newHdfsInfo(hdfsPath);
            FileSystem fileSystem = hdfsInfo.getFileSystem();
            FileUtil.copy(fileSystem, new Path(hdfsPath), outputDest, false, fileSystem.getConf());
            try {
                FileUtil.copyMerge(
                        fileSystem, // src
                        new Path(hdfsPath),
                        FileSystem.getLocal(new Configuration()), // dest
                        new Path(outputDest.toURI()),
                        false, fileSystem.getConf(), null);
            } catch (IOException e) {
                return outputDest;
            }

            return new File(outputDest, fileName);
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }
}
