/**
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
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.util.IOHelper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.ArrayFile;
import org.apache.hadoop.io.BloomMapFile;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.MapFile;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.Writer;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.util.ReflectionUtils;

public enum HdfsFileType {

    NORMAL_FILE {
        @Override
        public long append(HdfsOutputStream hdfsostr, Object key, Object value, TypeConverter typeConverter) {
            InputStream is = null;
            try {
                is = typeConverter.convertTo(InputStream.class, value);
                return copyBytes(is, (FSDataOutputStream) hdfsostr.getOut(), HdfsConstants.DEFAULT_BUFFERSIZE, false);
            } catch (IOException ex) {
                throw new RuntimeCamelException(ex);
            } finally {
                IOHelper.close(is);
            }
        }

        @Override
        public long next(HdfsInputStream hdfsistr, Holder<Object> key, Holder<Object> value) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(hdfsistr.getChunkSize());
                byte buf[] = new byte[hdfsistr.getChunkSize()];
                int bytesRead = ((InputStream) hdfsistr.getIn()).read(buf);
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

        @Override
        public Closeable createOutputStream(String hdfsPath, HdfsConfiguration configuration) {
            try {
                Closeable rout;
                HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath);
                if (!configuration.isAppend()) {
                    rout = hdfsInfo.getFileSystem().create(hdfsInfo.getPath(), configuration.isOverwrite(), configuration.getBufferSize(),
                            configuration.getReplication(), configuration.getBlockSize(), new Progressable() {
                                @Override
                                public void progress() {
                                }
                            });
                } else {
                    rout = hdfsInfo.getFileSystem().append(hdfsInfo.getPath(), configuration.getBufferSize(), new Progressable() {
                        @Override
                        public void progress() {
                        }
                    });
                }
                return rout;
            } catch (IOException ex) {
                throw new RuntimeCamelException(ex);
            }
        }

        @Override
        public Closeable createInputStream(String hdfsPath, HdfsConfiguration configuration) {
            try {
                Closeable rin;
                if (configuration.getFileSystemType().equals(HdfsFileSystemType.LOCAL)) {
                    HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath);
                    rin = hdfsInfo.getFileSystem().open(hdfsInfo.getPath());
                } else {
                    rin = new FileInputStream(getHfdsFileToTmpFile(hdfsPath, configuration));
                }
                return rin;
            } catch (IOException ex) {
                throw new RuntimeCamelException(ex);
            }
        }

        private File getHfdsFileToTmpFile(String hdfsPath, HdfsConfiguration configuration) {
            try {
                String fname = hdfsPath.substring(hdfsPath.lastIndexOf('/'));

                File outputDest = File.createTempFile(fname, ".hdfs");
                if (outputDest.exists()) {
                    outputDest.delete();
                }

                HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath);
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
    },

    SEQUENCE_FILE {
        @Override
        public long append(HdfsOutputStream hdfsostr, Object key, Object value, TypeConverter typeConverter) {
            try {
                Holder<Integer> keySize = new Holder<Integer>();
                Writable keyWritable = getWritable(key, typeConverter, keySize);
                Holder<Integer> valueSize = new Holder<Integer>();
                Writable valueWritable = getWritable(value, typeConverter, valueSize);
                Writer writer = (SequenceFile.Writer) hdfsostr.getOut();
                writer.append(keyWritable, valueWritable);
                writer.sync();
                return keySize.value + valueSize.value;
            } catch (Exception ex) {
                throw new RuntimeCamelException(ex);
            }
        }

        @Override
        public long next(HdfsInputStream hdfsistr, Holder<Object> key, Holder<Object> value) {
            try {
                SequenceFile.Reader reader = (SequenceFile.Reader) hdfsistr.getIn();
                Holder<Integer> keySize = new Holder<Integer>();
                Writable keyWritable = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), new Configuration());
                Holder<Integer> valueSize = new Holder<Integer>();
                Writable valueWritable = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), new Configuration());
                if (reader.next(keyWritable, valueWritable)) {
                    key.value = getObject(keyWritable, keySize);
                    value.value = getObject(valueWritable, valueSize);
                    return keySize.value + valueSize.value;
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
                HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath);
                Class<?> keyWritableClass = configuration.getKeyType().getWritableClass();
                Class<?> valueWritableClass = configuration.getValueType().getWritableClass();
                rout = SequenceFile.createWriter(hdfsInfo.getFileSystem(), hdfsInfo.getConf(), hdfsInfo.getPath(), keyWritableClass,
                        valueWritableClass, configuration.getBufferSize(), configuration.getReplication(), configuration.getBlockSize(),
                        configuration.getCompressionType(), configuration.getCompressionCodec().getCodec(), new Progressable() {
                            @Override
                            public void progress() {
                            }
                        }, new SequenceFile.Metadata());
                return rout;
            } catch (IOException ex) {
                throw new RuntimeCamelException(ex);
            }
        }

        @Override
        public Closeable createInputStream(String hdfsPath, HdfsConfiguration configuration) {
            try {
                Closeable rin;
                HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath);
                rin = new SequenceFile.Reader(hdfsInfo.getFileSystem(), hdfsInfo.getPath(), hdfsInfo.getConf());
                return rin;
            } catch (IOException ex) {
                throw new RuntimeCamelException(ex);
            }
        }
    },

    MAP_FILE {
        @Override
        public long append(HdfsOutputStream hdfsostr, Object key, Object value, TypeConverter typeConverter) {
            try {
                Holder<Integer> keySize = new Holder<Integer>();
                Writable keyWritable = getWritable(key, typeConverter, keySize);
                Holder<Integer> valueSize = new Holder<Integer>();
                Writable valueWritable = getWritable(value, typeConverter, valueSize);
                ((MapFile.Writer) hdfsostr.getOut()).append((WritableComparable<?>) keyWritable, valueWritable);
                return keySize.value + valueSize.value;
            } catch (Exception ex) {
                throw new RuntimeCamelException(ex);
            }
        }

        @Override
        public long next(HdfsInputStream hdfsistr, Holder<Object> key, Holder<Object> value) {
            try {
                MapFile.Reader reader = (MapFile.Reader) hdfsistr.getIn();
                Holder<Integer> keySize = new Holder<Integer>();
                WritableComparable<?> keyWritable = (WritableComparable<?>) ReflectionUtils.newInstance(reader.getKeyClass(), new Configuration());
                Holder<Integer> valueSize = new Holder<Integer>();
                Writable valueWritable = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), new Configuration());
                if (reader.next(keyWritable, valueWritable)) {
                    key.value = getObject(keyWritable, keySize);
                    value.value = getObject(valueWritable, valueSize);
                    return keySize.value + valueSize.value;
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
                HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath);
                Class<? extends WritableComparable> keyWritableClass = configuration.getKeyType().getWritableClass();
                Class<? extends WritableComparable> valueWritableClass = configuration.getValueType().getWritableClass();
                rout = new MapFile.Writer(hdfsInfo.getConf(), hdfsInfo.getFileSystem(), hdfsPath, keyWritableClass, valueWritableClass,
                        configuration.getCompressionType(), configuration.getCompressionCodec().getCodec(), new Progressable() {
                            @Override
                            public void progress() {
                            }
                        });
                return rout;
            } catch (IOException ex) {
                throw new RuntimeCamelException(ex);
            }
        }

        @Override
        public Closeable createInputStream(String hdfsPath, HdfsConfiguration configuration) {
            try {
                Closeable rin;
                HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath);
                rin = new MapFile.Reader(hdfsInfo.getFileSystem(), hdfsPath, hdfsInfo.getConf());
                return rin;
            } catch (IOException ex) {
                throw new RuntimeCamelException(ex);
            }
        }
    },

    BLOOMMAP_FILE {
        @Override
        public long append(HdfsOutputStream hdfsostr, Object key, Object value, TypeConverter typeConverter) {
            try {
                Holder<Integer> keySize = new Holder<Integer>();
                Writable keyWritable = getWritable(key, typeConverter, keySize);
                Holder<Integer> valueSize = new Holder<Integer>();
                Writable valueWritable = getWritable(value, typeConverter, valueSize);
                ((BloomMapFile.Writer) hdfsostr.getOut()).append((WritableComparable<?>) keyWritable, valueWritable);
                return keySize.value + valueSize.value;
            } catch (Exception ex) {
                throw new RuntimeCamelException(ex);
            }
        }

        @Override
        public long next(HdfsInputStream hdfsistr, Holder<Object> key, Holder<Object> value) {
            try {
                MapFile.Reader reader = (BloomMapFile.Reader) hdfsistr.getIn();
                Holder<Integer> keySize = new Holder<Integer>();
                WritableComparable<?> keyWritable = (WritableComparable<?>) ReflectionUtils.newInstance(reader.getKeyClass(), new Configuration());
                Holder<Integer> valueSize = new Holder<Integer>();
                Writable valueWritable = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), new Configuration());
                if (reader.next(keyWritable, valueWritable)) {
                    key.value = getObject(keyWritable, keySize);
                    value.value = getObject(valueWritable, valueSize);
                    return keySize.value + valueSize.value;
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
                HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath);
                Class<? extends WritableComparable> keyWritableClass = configuration.getKeyType().getWritableClass();
                Class<? extends WritableComparable> valueWritableClass = configuration.getValueType().getWritableClass();
                rout = new BloomMapFile.Writer(hdfsInfo.getConf(), hdfsInfo.getFileSystem(), hdfsPath, keyWritableClass, valueWritableClass,
                        configuration.getCompressionType(), configuration.getCompressionCodec().getCodec(), new Progressable() {
                            @Override
                            public void progress() {
                            }
                        });
                return rout;
            } catch (IOException ex) {
                throw new RuntimeCamelException(ex);
            }
        }

        @Override
        public Closeable createInputStream(String hdfsPath, HdfsConfiguration configuration) {
            try {
                Closeable rin;
                HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath);
                rin = new BloomMapFile.Reader(hdfsInfo.getFileSystem(), hdfsPath, hdfsInfo.getConf());
                return rin;
            } catch (IOException ex) {
                throw new RuntimeCamelException(ex);
            }
        }
    },

    ARRAY_FILE {
        @Override
        public long append(HdfsOutputStream hdfsostr, Object key, Object value, TypeConverter typeConverter) {
            try {
                Holder<Integer> valueSize = new Holder<Integer>();
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
                Holder<Integer> valueSize = new Holder<Integer>();
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
                HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath);
                Class<? extends WritableComparable> valueWritableClass = configuration.getValueType().getWritableClass();
                rout = new ArrayFile.Writer(hdfsInfo.getConf(), hdfsInfo.getFileSystem(), hdfsPath, valueWritableClass,
                        configuration.getCompressionType(), new Progressable() {
                            @Override
                            public void progress() {
                            }
                        });
                return rout;
            } catch (IOException ex) {
                throw new RuntimeCamelException(ex);
            }
        }

        @Override
        public Closeable createInputStream(String hdfsPath, HdfsConfiguration configuration) {
            try {
                Closeable rin;
                HdfsInfo hdfsInfo = HdfsInfoFactory.newHdfsInfo(hdfsPath);
                rin = new ArrayFile.Reader(hdfsInfo.getFileSystem(), hdfsPath, hdfsInfo.getConf());
                return rin;
            } catch (IOException ex) {
                throw new RuntimeCamelException(ex);
            }
        }
    };

    @SuppressWarnings({"rawtypes"})
    private static final class WritableCache {

        private static Map<Class, HdfsWritableFactories.HdfsWritableFactory> writables = new HashMap<Class, HdfsWritableFactories.HdfsWritableFactory>();
        private static Map<Class, HdfsWritableFactories.HdfsWritableFactory> readables = new HashMap<Class, HdfsWritableFactories.HdfsWritableFactory>();

        private WritableCache() {
        }

        static {
            writables.put(Boolean.class, new HdfsWritableFactories.HdfsBooleanWritableFactory());
            writables.put(Byte.class, new HdfsWritableFactories.HdfsByteWritableFactory());
            writables.put(ByteBuffer.class, new HdfsWritableFactories.HdfsBytesWritableFactory());
            writables.put(Double.class, new HdfsWritableFactories.HdfsDoubleWritableFactory());
            writables.put(Float.class, new HdfsWritableFactories.HdfsFloatWritableFactory());
            writables.put(Integer.class, new HdfsWritableFactories.HdfsIntWritableFactory());
            writables.put(Long.class, new HdfsWritableFactories.HdfsLongWritableFactory());
            writables.put(String.class, new HdfsWritableFactories.HdfsTextWritableFactory());
            writables.put(null, new HdfsWritableFactories.HdfsNullWritableFactory());
        }

        static {
            readables.put(BooleanWritable.class, new HdfsWritableFactories.HdfsBooleanWritableFactory());
            readables.put(ByteWritable.class, new HdfsWritableFactories.HdfsByteWritableFactory());
            readables.put(BytesWritable.class, new HdfsWritableFactories.HdfsBytesWritableFactory());
            readables.put(DoubleWritable.class, new HdfsWritableFactories.HdfsDoubleWritableFactory());
            readables.put(FloatWritable.class, new HdfsWritableFactories.HdfsFloatWritableFactory());
            readables.put(IntWritable.class, new HdfsWritableFactories.HdfsIntWritableFactory());
            readables.put(LongWritable.class, new HdfsWritableFactories.HdfsLongWritableFactory());
            readables.put(Text.class, new HdfsWritableFactories.HdfsTextWritableFactory());
            readables.put(NullWritable.class, new HdfsWritableFactories.HdfsNullWritableFactory());
        }
    }

    private static Writable getWritable(Object obj, TypeConverter typeConverter, Holder<Integer> size) {
        Class<?> objCls = obj == null ? null : obj.getClass();
        HdfsWritableFactories.HdfsWritableFactory objWritableFactory = WritableCache.writables.get(objCls);
        if (objWritableFactory == null) {
            objWritableFactory = new HdfsWritableFactories.HdfsObjectWritableFactory();
        }
        return objWritableFactory.create(obj, typeConverter, size);
    }

    private static Object getObject(Writable writable, Holder<Integer> size) {
        Class<?> writableClass = NullWritable.class;
        if (writable != null) {
            writableClass = writable.getClass();
        }
        HdfsWritableFactories.HdfsWritableFactory writableObjectFactory = WritableCache.readables.get(writableClass);
        return writableObjectFactory.read(writable, size);
    }

    public abstract long append(HdfsOutputStream hdfsostr, Object key, Object value, TypeConverter typeConverter);

    public abstract long next(HdfsInputStream hdfsistr, Holder<Object> key, Holder<Object> value);

    public abstract Closeable createOutputStream(String hdfsPath, HdfsConfiguration configuration);

    public abstract Closeable createInputStream(String hdfsPath, HdfsConfiguration configuration);

    public static long copyBytes(InputStream in, OutputStream out, int buffSize, boolean close) throws IOException {
        long numBytes = 0;
        PrintStream ps = out instanceof PrintStream ? (PrintStream) out : null;
        byte buf[] = new byte[buffSize];
        try {
            int bytesRead = in.read(buf);
            while (bytesRead >= 0) {
                out.write(buf, 0, bytesRead);
                numBytes += bytesRead;
                if ((ps != null) && ps.checkError()) {
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
}
