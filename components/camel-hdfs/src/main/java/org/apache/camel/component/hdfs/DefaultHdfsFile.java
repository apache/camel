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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.util.IOHelper;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.ShortWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;

abstract class DefaultHdfsFile<T extends Closeable, U extends Closeable> implements HdfsFile<T, U, Object, Object> {

    protected final long copyBytes(InputStream in, OutputStream out, int buffSize, boolean close) throws IOException {
        long numBytes = 0;
        PrintStream ps = out instanceof PrintStream ? (PrintStream) out : null;
        byte[] buf = new byte[buffSize];
        try {
            int bytesRead = in.read(buf);
            while (bytesRead >= 0) {
                out.write(buf, 0, bytesRead);
                numBytes += bytesRead;
                if (ps != null && ps.checkError()) {
                    throw new IOException("Unable to write to output stream.");
                }
                bytesRead = in.read(buf);
            }
        } finally {
            if (close) {
                IOHelper.close(out, in);
            }
        }
        return numBytes;
    }

    protected final Writable getWritable(Object obj, Exchange exchange, Holder<Integer> size) {
        Class<?> objCls = Optional.ofNullable(obj).orElse(new UnknownType()).getClass();
        HdfsWritableFactories.HdfsWritableFactory objWritableFactory
                = WritableCache.writables.getOrDefault(objCls, new HdfsWritableFactories.HdfsObjectWritableFactory());
        return objWritableFactory.create(obj, exchange.getContext().getTypeConverter(), size);
    }

    protected final Object getObject(Writable writable, Holder<Integer> size) {
        Class<?> writableClass = NullWritable.class;
        if (writable != null) {
            writableClass = writable.getClass();
        }
        HdfsWritableFactories.HdfsWritableFactory writableObjectFactory = WritableCache.readables.get(writableClass);
        return writableObjectFactory.read(writable, size);
    }

    @SuppressWarnings({ "rawtypes" })
    private static final class WritableCache {
        private static Map<Class, HdfsWritableFactories.HdfsWritableFactory> writables = new HashMap<>();
        private static Map<Class, HdfsWritableFactories.HdfsWritableFactory> readables = new HashMap<>();

        private WritableCache() {
        }

        static {
            writables.put(Boolean.class, new HdfsWritableFactories.HdfsBooleanWritableFactory());
            writables.put(Byte.class, new HdfsWritableFactories.HdfsByteWritableFactory());
            writables.put(ByteBuffer.class, new HdfsWritableFactories.HdfsBytesWritableFactory());
            writables.put(Double.class, new HdfsWritableFactories.HdfsDoubleWritableFactory());
            writables.put(Float.class, new HdfsWritableFactories.HdfsFloatWritableFactory());
            writables.put(Short.class, new HdfsWritableFactories.HdfsShortWritableFactory());
            writables.put(Integer.class, new HdfsWritableFactories.HdfsIntWritableFactory());
            writables.put(Long.class, new HdfsWritableFactories.HdfsLongWritableFactory());
            writables.put(String.class, new HdfsWritableFactories.HdfsTextWritableFactory());
            writables.put(UnknownType.class, new HdfsWritableFactories.HdfsNullWritableFactory());
        }

        static {
            readables.put(BooleanWritable.class, new HdfsWritableFactories.HdfsBooleanWritableFactory());
            readables.put(ByteWritable.class, new HdfsWritableFactories.HdfsByteWritableFactory());
            readables.put(BytesWritable.class, new HdfsWritableFactories.HdfsBytesWritableFactory());
            readables.put(DoubleWritable.class, new HdfsWritableFactories.HdfsDoubleWritableFactory());
            readables.put(FloatWritable.class, new HdfsWritableFactories.HdfsFloatWritableFactory());
            readables.put(ShortWritable.class, new HdfsWritableFactories.HdfsShortWritableFactory());
            readables.put(IntWritable.class, new HdfsWritableFactories.HdfsIntWritableFactory());
            readables.put(LongWritable.class, new HdfsWritableFactories.HdfsLongWritableFactory());
            readables.put(Text.class, new HdfsWritableFactories.HdfsTextWritableFactory());
            readables.put(NullWritable.class, new HdfsWritableFactories.HdfsNullWritableFactory());
        }
    }

    private static final class UnknownType {
    }
}
