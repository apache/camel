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

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.util.ReflectionUtils;

class HdfsSequenceFileHandler extends DefaultHdfsFile<SequenceFile.Writer, SequenceFile.Reader> {

    @Override
    public SequenceFile.Writer createOutputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        try {
            SequenceFile.Writer rout;
            HdfsInfo hdfsInfo = hdfsInfoFactory.newHdfsInfo(hdfsPath);
            HdfsConfiguration endpointConfig = hdfsInfoFactory.getEndpointConfig();
            Class<?> keyWritableClass = endpointConfig.getKeyType().getWritableClass();
            Class<?> valueWritableClass = endpointConfig.getValueType().getWritableClass();
            rout = SequenceFile.createWriter(
                    hdfsInfo.getConfiguration(),
                    SequenceFile.Writer.file(hdfsInfo.getPath()),
                    SequenceFile.Writer.keyClass(keyWritableClass),
                    SequenceFile.Writer.valueClass(valueWritableClass),
                    SequenceFile.Writer.bufferSize(endpointConfig.getBufferSize()),
                    SequenceFile.Writer.replication(endpointConfig.getReplication()),
                    SequenceFile.Writer.blockSize(endpointConfig.getBlockSize()),
                    SequenceFile.Writer.compression(endpointConfig.getCompressionType(), endpointConfig.getCompressionCodec().getCodec()),
                    SequenceFile.Writer.progressable(() -> { }),
                    SequenceFile.Writer.metadata(new SequenceFile.Metadata())
            );
            return rout;
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public long append(HdfsOutputStream hdfsOutputStream, Object key, Object value, Exchange exchange) {
        try {
            Holder<Integer> keySize = new Holder<>();
            Writable keyWritable = getWritable(key, exchange, keySize);
            Holder<Integer> valueSize = new Holder<>();
            Writable valueWritable = getWritable(value, exchange, valueSize);
            SequenceFile.Writer writer = (SequenceFile.Writer) hdfsOutputStream.getOut();
            writer.append(keyWritable, valueWritable);
            writer.sync();
            return Long.sum(keySize.getValue(), valueSize.getValue());
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public SequenceFile.Reader createInputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        try {
            SequenceFile.Reader rin;
            HdfsInfo hdfsInfo = hdfsInfoFactory.newHdfsInfo(hdfsPath);
            rin = new SequenceFile.Reader(hdfsInfo.getConfiguration(), SequenceFile.Reader.file(hdfsInfo.getPath()));
            return rin;
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public long next(HdfsInputStream hdfsInputStream, Holder<Object> key, Holder<Object> value) {
        try {
            SequenceFile.Reader reader = (SequenceFile.Reader) hdfsInputStream.getIn();
            Holder<Integer> keySize = new Holder<>();
            Writable keyWritable = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), new Configuration());
            Holder<Integer> valueSize = new Holder<>();
            Writable valueWritable = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), new Configuration());
            if (reader.next(keyWritable, valueWritable)) {
                key.setValue(getObject(keyWritable, keySize));
                value.setValue(getObject(valueWritable, valueSize));
                return Long.sum(keySize.getValue(), valueSize.getValue());
            } else {
                return 0;
            }
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }
    }

}
