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

import java.io.Closeable;
import java.io.IOException;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.util.ReflectionUtils;

class HdfsSequenceFileType extends DefaultHdfsFileType {

    @Override
    public long append(HdfsOutputStream hdfsostr, Object key, Object value, TypeConverter typeConverter) {
        try {
            Holder<Integer> keySize = new Holder<>();
            Writable keyWritable = getWritable(key, typeConverter, keySize);
            Holder<Integer> valueSize = new Holder<>();
            Writable valueWritable = getWritable(value, typeConverter, valueSize);
            SequenceFile.Writer writer = (SequenceFile.Writer) hdfsostr.getOut();
            writer.append(keyWritable, valueWritable);
            writer.sync();
            return Long.sum(keySize.value, valueSize.value);
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public long next(HdfsInputStream hdfsistr, Holder<Object> key, Holder<Object> value) {
        try {
            SequenceFile.Reader reader = (SequenceFile.Reader) hdfsistr.getIn();
            Holder<Integer> keySize = new Holder<>();
            Writable keyWritable = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), new Configuration());
            Holder<Integer> valueSize = new Holder<>();
            Writable valueWritable = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), new Configuration());
            if (reader.next(keyWritable, valueWritable)) {
                key.value = getObject(keyWritable, keySize);
                value.value = getObject(valueWritable, valueSize);
                return Long.sum(keySize.value, valueSize.value);
            } else {
                return 0;
            }
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public Closeable createOutputStream(String hdfsPath, HdfsConfiguration configuration) {
        try {
            Closeable rout;
            HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath, configuration);
            Class<?> keyWritableClass = configuration.getKeyType().getWritableClass();
            Class<?> valueWritableClass = configuration.getValueType().getWritableClass();
            rout = SequenceFile.createWriter(hdfsInfo.getConfiguration(), SequenceFile.Writer.file(hdfsInfo.getPath()), SequenceFile.Writer.keyClass(keyWritableClass),
                    SequenceFile.Writer.valueClass(valueWritableClass), SequenceFile.Writer.bufferSize(configuration.getBufferSize()),
                    SequenceFile.Writer.replication(configuration.getReplication()), SequenceFile.Writer.blockSize(configuration.getBlockSize()),
                    SequenceFile.Writer.compression(configuration.getCompressionType(), configuration.getCompressionCodec().getCodec()),
                    SequenceFile.Writer.progressable(() -> {
                    }), SequenceFile.Writer.metadata(new SequenceFile.Metadata()));
            return rout;
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public Closeable createInputStream(String hdfsPath, HdfsConfiguration configuration) {
        try {
            Closeable rin;
            HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath, configuration);
            rin = new SequenceFile.Reader(hdfsInfo.getConfiguration(), SequenceFile.Reader.file(hdfsInfo.getPath()));
            return rin;
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }
}
