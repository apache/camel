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
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;

public class HdfsWritableFactories {

    interface HdfsWritableFactory {

        Writable create(Object value, TypeConverter typeConverter, Holder<Integer> size);

        Object read(Writable writable, Holder<Integer> size);
    }

    public static final class HdfsNullWritableFactory implements HdfsWritableFactory {

        @Override
        public Writable create(Object value, TypeConverter typeConverter, Holder<Integer> size) {
            size.setValue(0);
            return NullWritable.get();
        }

        @Override
        public Object read(Writable writable, Holder<Integer> size) {
            size.setValue(0);
            return null;
        }
    }

    public static final class HdfsByteWritableFactory implements HdfsWritableFactory {

        private static final int SIZE = 1;

        @Override
        public Writable create(Object value, TypeConverter typeConverter, Holder<Integer> size) {
            size.setValue(SIZE);
            ByteWritable writable = new ByteWritable();
            writable.set(typeConverter.convertTo(Byte.class, value));
            return writable;
        }

        @Override
        public Object read(Writable writable, Holder<Integer> size) {
            size.setValue(SIZE);
            return ((ByteWritable) writable).get();
        }
    }

    public static final class HdfsBooleanWritableFactory implements HdfsWritableFactory {

        private static final int SIZE = 1;

        @Override
        public Writable create(Object value, TypeConverter typeConverter, Holder<Integer> size) {
            size.setValue(SIZE);
            BooleanWritable writable = new BooleanWritable();
            writable.set(typeConverter.convertTo(Boolean.class, value));
            return writable;
        }

        @Override
        public Object read(Writable writable, Holder<Integer> size) {
            size.setValue(SIZE);
            return ((BooleanWritable) writable).get();
        }
    }

    public static final class HdfsBytesWritableFactory implements HdfsWritableFactory {

        @Override
        public Writable create(Object value, TypeConverter typeConverter, Holder<Integer> size) {
            BytesWritable writable = new BytesWritable();
            ByteBuffer bb = (ByteBuffer) value;
            writable.set(bb.array(), 0, bb.array().length);
            size.setValue(bb.array().length);
            return writable;
        }

        @Override
        public Object read(Writable writable, Holder<Integer> size) {
            size.setValue(((BytesWritable) writable).getLength());
            ByteBuffer bb = ByteBuffer.allocate(size.getValue());
            bb.put(((BytesWritable) writable).getBytes(), 0, size.getValue());
            return bb;
        }
    }

    public static final class HdfsDoubleWritableFactory implements HdfsWritableFactory {

        private static final int SIZE = 8;

        @Override
        public Writable create(Object value, TypeConverter typeConverter, Holder<Integer> size) {
            size.setValue(SIZE);
            DoubleWritable writable = new DoubleWritable();
            writable.set(typeConverter.convertTo(Double.class, value));
            return writable;
        }

        @Override
        public Object read(Writable writable, Holder<Integer> size) {
            size.setValue(SIZE);
            return ((DoubleWritable) writable).get();
        }
    }

    public static final class HdfsFloatWritableFactory implements HdfsWritableFactory {

        private static final int SIZE = 4;

        @Override
        public Writable create(Object value, TypeConverter typeConverter, Holder<Integer> size) {
            size.setValue(SIZE);
            FloatWritable writable = new FloatWritable();
            writable.set(typeConverter.convertTo(Float.class, value));
            return writable;
        }

        @Override
        public Object read(Writable writable, Holder<Integer> size) {
            size.setValue(SIZE);
            return ((FloatWritable) writable).get();
        }
    }

    public static final class HdfsIntWritableFactory implements HdfsWritableFactory {

        private static final int SIZE = 4;

        @Override
        public Writable create(Object value, TypeConverter typeConverter, Holder<Integer> size) {
            size.setValue(SIZE);
            IntWritable writable = new IntWritable();
            writable.set(typeConverter.convertTo(Integer.class, value));
            return writable;
        }

        @Override
        public Object read(Writable writable, Holder<Integer> size) {
            size.setValue(SIZE);
            return ((IntWritable) writable).get();
        }
    }

    public static final class HdfsLongWritableFactory implements HdfsWritableFactory {

        private static final int SIZE = 8;

        @Override
        public Writable create(Object value, TypeConverter typeConverter, Holder<Integer> size) {
            size.setValue(SIZE);
            LongWritable writable = new LongWritable();
            writable.set(typeConverter.convertTo(Long.class, value));
            return writable;
        }

        @Override
        public Object read(Writable writable, Holder<Integer> size) {
            size.setValue(SIZE);
            return ((LongWritable) writable).get();
        }
    }

    public static final class HdfsTextWritableFactory implements HdfsWritableFactory {

        @Override
        public Writable create(Object value, TypeConverter typeConverter, Holder<Integer> size) {
            Text writable = new Text();
            writable.set(typeConverter.convertTo(String.class, value));
            size.setValue(writable.getBytes().length);
            return writable;
        }

        @Override
        public Object read(Writable writable, Holder<Integer> size) {
            size.setValue(((Text) writable).getLength());
            return writable.toString();
        }
    }

    public static final class HdfsObjectWritableFactory implements HdfsWritableFactory {

        @Override
        public Writable create(Object value, TypeConverter typeConverter, Holder<Integer> size) {
            try (InputStream is = typeConverter.convertTo(InputStream.class, value)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copyBytes(is, bos, HdfsConstants.DEFAULT_BUFFERSIZE, false);
                BytesWritable writable = new BytesWritable();
                writable.set(bos.toByteArray(), 0, bos.toByteArray().length);
                size.setValue(bos.toByteArray().length);
                return writable;
            } catch (IOException ex) {
                throw new RuntimeCamelException(ex);
            }
        }

        @Override
        public Object read(Writable writable, Holder<Integer> size) {
            size.setValue(0);
            return null;
        }
    }

}
