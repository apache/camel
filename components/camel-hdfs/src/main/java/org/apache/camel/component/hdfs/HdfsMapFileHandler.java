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
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.MapFile;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.util.ReflectionUtils;

class HdfsMapFileHandler extends DefaultHdfsFile<MapFile.Writer, MapFile.Reader> {

    @Override
    @SuppressWarnings("rawtypes")
    public MapFile.Writer createOutputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        try {
            MapFile.Writer rout;
            HdfsInfo hdfsInfo = hdfsInfoFactory.newHdfsInfo(hdfsPath);
            HdfsConfiguration endpointConfig = hdfsInfoFactory.getEndpointConfig();
            Class<? extends WritableComparable> keyWritableClass = endpointConfig.getKeyType().getWritableClass();
            Class<? extends WritableComparable> valueWritableClass = endpointConfig.getValueType().getWritableClass();
            rout = new MapFile.Writer(
                    hdfsInfo.getConfiguration(),
                    new Path(hdfsPath),
                    MapFile.Writer.keyClass(keyWritableClass),
                    MapFile.Writer.valueClass(valueWritableClass),
                    MapFile.Writer.compression(endpointConfig.getCompressionType(), endpointConfig.getCompressionCodec().getCodec()),
                    MapFile.Writer.progressable(() -> { })
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
            ((MapFile.Writer) hdfsOutputStream.getOut()).append((WritableComparable<?>) keyWritable, valueWritable);
            return Long.sum(keySize.getValue(), valueSize.getValue());
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public MapFile.Reader createInputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        try {
            MapFile.Reader rin;
            HdfsInfo hdfsInfo = hdfsInfoFactory.newHdfsInfo(hdfsPath);
            rin = new MapFile.Reader(new Path(hdfsPath), hdfsInfo.getConfiguration());
            return rin;
        } catch (IOException ex) {
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
