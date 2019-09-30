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
import org.apache.hadoop.io.ArrayFile;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.util.ReflectionUtils;

class HdfsArrayFileType extends DefaultHdfsFileType {

    @Override
    public long append(HdfsOutputStream hdfsostr, Object key, Object value, TypeConverter typeConverter) {
        try {
            Holder<Integer> valueSize = new Holder<>();
            Writable valueWritable = getWritable(value, typeConverter, valueSize);
            ((ArrayFile.Writer) hdfsostr.getOut()).append(valueWritable);
            return valueSize.value;
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @Override
    public long next(HdfsInputStream hdfsistr, Holder<Object> key, Holder<Object> value) {
        try {
            ArrayFile.Reader reader = (ArrayFile.Reader) hdfsistr.getIn();
            Holder<Integer> valueSize = new Holder<>();
            Writable valueWritable = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), new Configuration());
            if (reader.next(valueWritable) != null) {
                value.value = getObject(valueWritable, valueSize);
                return valueSize.value;
            } else {
                return 0;
            }
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Closeable createOutputStream(String hdfsPath, HdfsConfiguration configuration) {
        try {
            Closeable rout;
            HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath, configuration);
            Class<? extends WritableComparable> valueWritableClass = configuration.getValueType().getWritableClass();
            rout = new ArrayFile.Writer(hdfsInfo.getConf(), hdfsInfo.getFileSystem(), hdfsPath, valueWritableClass,
                    configuration.getCompressionType(), () -> { });
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
            rin = new ArrayFile.Reader(hdfsInfo.getFileSystem(), hdfsPath, hdfsInfo.getConf());
            return rin;
        } catch (IOException ex) {
            throw new RuntimeCamelException(ex);
        }
    }
}
