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
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.MapFile;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.util.ReflectionUtils;

class HdfsMapFileType extends DefaultHdfsFileType {

    @Override
    public long append(HdfsOutputStream hdfsostr, Object key, Object value, TypeConverter typeConverter) {
        try {
            Holder<Integer> keySize = new Holder<>();
            Writable keyWritable = getWritable(key, typeConverter, keySize);
            Holder<Integer> valueSize = new Holder<>();
            Writable valueWritable = getWritable(value, typeConverter, valueSize);
            ((MapFile.Writer) hdfsostr.getOut()).append((WritableComparable<?>) keyWritable, valueWritable);
            return Long.sum(keySize.value, valueSize.value);
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public long next(HdfsInputStream hdfsInputStream, Holder<Object> key, Holder<Object> value) {
        try {
            MapFile.Reader reader = (MapFile.Reader) hdfsInputStream.getIn();
            Holder<Integer> keySize = new Holder<>();
            WritableComparable<?> keyWritable = (WritableComparable<?>) ReflectionUtils.newInstance(reader.getKeyClass(), new Configuration());
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
    @SuppressWarnings("rawtypes")
    public Closeable createOutputStream(String hdfsPath, HdfsConfiguration configuration) {
        try {
            Closeable rout;
            HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath, configuration);
            Class<? extends WritableComparable> keyWritableClass = configuration.getKeyType().getWritableClass();
            Class<? extends WritableComparable> valueWritableClass = configuration.getValueType().getWritableClass();
            rout = new MapFile.Writer(hdfsInfo.getConfiguration(), new Path(hdfsPath), MapFile.Writer.keyClass(keyWritableClass), MapFile.Writer.valueClass(valueWritableClass),
                    MapFile.Writer.compression(configuration.getCompressionType(), configuration.getCompressionCodec().getCodec()),
                    MapFile.Writer.progressable(() -> {
                    }));
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
            rin = new MapFile.Reader(new Path(hdfsPath), hdfsInfo.getConfiguration());
            return rin;
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }
}
