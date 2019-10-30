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
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.util.IOHelper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;

class HdfsNormalFileHandler extends DefaultHdfsFile {

    @Override
    public Closeable createOutputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        try {
            FSDataOutputStream rout;
            HdfsInfo hdfsInfo = hdfsInfoFactory.newHdfsInfo(hdfsPath);
            HdfsConfiguration endpointConfig = hdfsInfoFactory.getEndpointConfig();
            if (endpointConfig.isAppend()) {
                rout = hdfsInfo.getFileSystem().append(
                        hdfsInfo.getPath(),
                        endpointConfig.getBufferSize(),
                    () -> { }
                );
            } else {
                rout = hdfsInfo.getFileSystem().create(
                        hdfsInfo.getPath(),
                        endpointConfig.isOverwrite(),
                        endpointConfig.getBufferSize(),
                        endpointConfig.getReplication(),
                        endpointConfig.getBlockSize(),
                    () -> { }
                );
            }
            return rout;
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public long append(HdfsOutputStream hdfsOutputStream, Object key, Object value, TypeConverter typeConverter) {
        InputStream is = null;
        try {
            is = typeConverter.convertTo(InputStream.class, value);
            return copyBytes(is, (FSDataOutputStream) hdfsOutputStream.getOut(), HdfsConstants.DEFAULT_BUFFERSIZE, false);
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        } finally {
            IOHelper.close(is);
        }
    }

    @Override
    public Closeable createInputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        try {
            Closeable rin;
            HdfsConfiguration endpointConfig = hdfsInfoFactory.getEndpointConfig();
            if (endpointConfig.getFileSystemType().equals(HdfsFileSystemType.LOCAL)) {
                HdfsInfo hdfsInfo = hdfsInfoFactory.newHdfsInfo(hdfsPath);
                rin = hdfsInfo.getFileSystem().open(hdfsInfo.getPath());
            } else {
                rin = new FileInputStream(getHdfsFileToTmpFile(hdfsPath, endpointConfig));
            }
            return rin;
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public long next(HdfsInputStream hdfsInputStream, Holder<Object> key, Holder<Object> value) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(hdfsInputStream.getChunkSize());
            byte[] buf = new byte[hdfsInputStream.getChunkSize()];
            int bytesRead = ((InputStream) hdfsInputStream.getIn()).read(buf);
            if (bytesRead >= 0) {
                bos.write(buf, 0, bytesRead);
                key.value = null;
                value.value = bos;
                return bytesRead;
            } else {
                key.value = null;
                // indication that we may have read from empty file
                value.value = bos;
                return 0;
            }
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    private File getHdfsFileToTmpFile(String hdfsPath, HdfsConfiguration configuration) {
        try {
            String fname = hdfsPath.substring(hdfsPath.lastIndexOf('/'));

            // [CAMEL-13711] Files.createTempFile not equivalent to File.createTempFile

            File outputDest;
            try {
                // First trying: Files.createTempFile
                outputDest = Files.createTempFile(fname, ".hdfs").toFile();

            } catch (Exception ex) {
                // Now trying: File.createTempFile
                outputDest = File.createTempFile(fname, ".hdfs");
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

            return new File(outputDest, fname);
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }
}
