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
import org.apache.hadoop.io.ArrayFile;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.util.ReflectionUtils;

class HdfsArrayFileTypeHandler extends DefaultHdfsFile<ArrayFile.Writer, ArrayFile.Reader> {

    @SuppressWarnings("rawtypes")
    @Override
    public ArrayFile.Writer createOutputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        try {
            ArrayFile.Writer rout;
            HdfsInfo hdfsInfo = hdfsInfoFactory.newHdfsInfo(hdfsPath);
            HdfsConfiguration endpointConfig = hdfsInfoFactory.getEndpointConfig();
            Class<? extends WritableComparable> valueWritableClass = endpointConfig.getValueType().getWritableClass();
            rout = new ArrayFile.Writer(
                    hdfsInfo.getConfiguration(),
                    hdfsInfo.getFileSystem(),
                    hdfsPath,
                    valueWritableClass,
                    endpointConfig.getCompressionType(),
            () -> { }
            );
            return rout;
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public long append(HdfsOutputStream hdfsOutputStream, Object key, Object value, Exchange exchange) {
        try {
            Holder<Integer> valueSize = new Holder<>();
            Writable valueWritable = getWritable(value, exchange, valueSize);
            ((ArrayFile.Writer) hdfsOutputStream.getOut()).append(valueWritable);
            return valueSize.getValue();
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public ArrayFile.Reader createInputStream(String hdfsPath, HdfsInfoFactory hdfsInfoFactory) {
        try {
            ArrayFile.Reader rin;
            HdfsInfo hdfsInfo = hdfsInfoFactory.newHdfsInfo(hdfsPath);
            rin = new ArrayFile.Reader(hdfsInfo.getFileSystem(), hdfsPath, hdfsInfo.getConfiguration());
            return rin;
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public long next(HdfsInputStream hdfsInputStream, Holder<Object> key, Holder<Object> value) {
        try {
            ArrayFile.Reader reader = (ArrayFile.Reader) hdfsInputStream.getIn();
            Holder<Integer> valueSize = new Holder<>();
            Writable valueWritable = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), new Configuration());
            if (reader.next(valueWritable) != null) {
                value.setValue(getObject(valueWritable, valueSize));
                return valueSize.getValue();
            } else {
                return 0;
            }
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }
    }
}
