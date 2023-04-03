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
import java.io.File;
import java.io.InputStream;
import java.net.URL;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.util.IOHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.ArrayFile;
import org.apache.hadoop.io.BloomMapFile;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.MapFile;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.ShortWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.util.ReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class HdfsProducerTest extends HdfsTestSupport {

    private static final Path TEMP_DIR = new Path(new File("target/test/").getAbsolutePath());

    private static final boolean LD_LIBRARY_PATH_DEFINED = StringUtils.isNotBlank(System.getenv("LD_LIBRARY_PATH"));

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        checkTest();
        super.setUp();
    }

    @Test
    public void testProducer() throws Exception {
        template.sendBody("direct:start1", "PAPPO");

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel1");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        assertEquals("PAPPO", value.toString());

        IOHelper.close(reader);
    }

    @Test
    public void testProducerClose() throws Exception {
        for (int i = 0; i < 10; ++i) {
            // send 10 messages, and mark to close in last message
            template.sendBodyAndHeader("direct:start1", "PAPPO" + i, HdfsConstants.HDFS_CLOSE, i == 9);
        }

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel1");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);

        int i = 0;
        while (reader.next(key, value)) {
            Text txt = (Text) value;
            assertEquals("PAPPO" + i, txt.toString());
            ++i;
        }

        IOHelper.close(reader);
    }

    @Test
    public void testWriteBoolean() throws Exception {
        Boolean aBoolean = true;
        template.sendBody("direct:write_boolean", aBoolean);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-boolean");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        Boolean rBoolean = ((BooleanWritable) value).get();
        assertEquals(rBoolean, aBoolean);

        IOHelper.close(reader);
    }

    @Test
    public void testWriteByte() throws Exception {
        byte aByte = 8;
        template.sendBody("direct:write_byte", aByte);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-byte");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        byte rByte = ((ByteWritable) value).get();
        assertEquals(rByte, aByte);

        IOHelper.close(reader);
    }

    @Test
    public void testWriteShort() throws Exception {
        short aShort = 32767;
        template.sendBody("direct:write_short", aShort);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-short");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        short rShort = ((ShortWritable) value).get();
        assertEquals(rShort, aShort);

        IOHelper.close(reader);
    }

    @Test
    public void testWriteInt() throws Exception {
        int anInt = 1234;
        template.sendBody("direct:write_int", anInt);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-int");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        int rInt = ((IntWritable) value).get();
        assertEquals(rInt, anInt);

        IOHelper.close(reader);
    }

    @Test
    public void testWriteFloat() throws Exception {
        float aFloat = 12.34f;
        template.sendBody("direct:write_float", aFloat);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-float");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        float rFloat = ((FloatWritable) value).get();
        assertEquals(rFloat, aFloat, 0.0F);

        IOHelper.close(reader);
    }

    @Test
    public void testWriteDouble() throws Exception {
        Double aDouble = 12.34D;
        template.sendBody("direct:write_double", aDouble);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-double");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        Double rDouble = ((DoubleWritable) value).get();
        assertEquals(rDouble, aDouble);

        IOHelper.close(reader);
    }

    @Test
    public void testWriteLong() throws Exception {
        long aLong = 1234567890;
        template.sendBody("direct:write_long", aLong);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-long");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        long rLong = ((LongWritable) value).get();
        assertEquals(rLong, aLong);

        IOHelper.close(reader);
    }

    @Test
    public void testWriteText() throws Exception {
        String txt = "CIAO MONDO !";
        template.sendBody("direct:write_text1", txt);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-text1");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Text value = (Text) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        String rTxt = value.toString();
        assertEquals(rTxt, txt);

        IOHelper.close(reader);
    }

    @Test
    public void testWriteTextWithKey() throws Exception {
        String txtKey = "THEKEY";
        String txtValue = "CIAO MONDO !";
        template.sendBodyAndHeader("direct:write_text2", txtValue, "KEY", txtKey);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-text2");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Text key = (Text) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Text value = (Text) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        assertEquals(key.toString(), txtKey);
        assertEquals(value.toString(), txtValue);

        IOHelper.close(reader);
    }

    @Test
    public void testMapWriteTextWithKey() throws Exception {
        String txtKey = "THEKEY";
        String txtValue = "CIAO MONDO !";
        template.sendBodyAndHeader("direct:write_text3", txtValue, "KEY", txtKey);

        Configuration conf = new Configuration();
        MapFile.Reader reader = new MapFile.Reader(new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-text3"), conf);
        Text key = (Text) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Text value = (Text) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        assertEquals(key.toString(), txtKey);
        assertEquals(value.toString(), txtValue);

        IOHelper.close(reader);
    }

    @Test
    public void testArrayWriteText() throws Exception {
        String txtValue = "CIAO MONDO !";
        template.sendBody("direct:write_text4", txtValue);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-text4");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        ArrayFile.Reader reader = new ArrayFile.Reader(fs1, "file:///" + TEMP_DIR.toUri() + "/test-camel-text4", conf);
        Text value = (Text) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(value);
        assertEquals(value.toString(), txtValue);

        IOHelper.close(reader);
    }

    @Test
    public void testBloomMapWriteText() throws Exception {
        String txtKey = "THEKEY";
        String txtValue = "CIAO MONDO !";
        template.sendBodyAndHeader("direct:write_text5", txtValue, "KEY", txtKey);

        Configuration conf = new Configuration();
        BloomMapFile.Reader reader
                = new BloomMapFile.Reader(new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-text5"), conf);
        Text key = (Text) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Text value = (Text) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        assertEquals(key.toString(), txtKey);
        assertEquals(value.toString(), txtValue);

        IOHelper.close(reader);
    }

    @Test
    public void testWriteTextWithDynamicFilename() throws Exception {
        for (int i = 0; i < 5; i++) {
            template.sendBodyAndHeader("direct:write_dynamic_filename", "CIAO" + i, Exchange.FILE_NAME, "file" + i);
        }

        for (int i = 0; i < 5; i++) {
            InputStream in = null;
            try {
                in = new URL("file:///" + TEMP_DIR.toUri() + "/test-camel-dynamic/file" + i).openStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copyBytes(in, bos, 4096, false);
                assertEquals("CIAO" + i, new String(bos.toByteArray()));
            } finally {
                IOHelper.close(in);
            }
        }
    }

    @Test
    public void testWriteTextWithDynamicFilenameExpression() throws Exception {
        for (int i = 0; i < 5; i++) {
            template.sendBodyAndHeader("direct:write_dynamic_filename", "CIAO" + i, Exchange.FILE_NAME,
                    new SimpleExpression("file-${body}"));
        }

        for (int i = 0; i < 5; i++) {
            InputStream in = null;
            try {
                in = new URL("file:///" + TEMP_DIR.toUri() + "/test-camel-dynamic/file-CIAO" + i).openStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copyBytes(in, bos, 4096, false);
                assertEquals("CIAO" + i, new String(bos.toByteArray()));
            } finally {
                IOHelper.close(in);
            }
        }
    }

    @Test
    public void testCompressWithGZip() throws Exception {
        assumeTrue(LD_LIBRARY_PATH_DEFINED);
        byte aByte = 8;
        template.sendBody("direct:gzip", aByte);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-gzip");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        byte rByte = ((ByteWritable) value).get();
        assertEquals(rByte, aByte);

        IOHelper.close(reader);
    }

    @Test
    public void testCompressWithBZip2() throws Exception {
        byte aByte = 8;
        template.sendBody("direct:bzip2", aByte);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-bzip2");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        byte rByte = ((ByteWritable) value).get();
        assertEquals(rByte, aByte);

        IOHelper.close(reader);
    }

    @Test
    public void testCompressWithSnappy() throws Exception {
        byte aByte = 8;
        template.sendBody("direct:snappy", aByte);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-snappy");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        byte rByte = ((ByteWritable) value).get();
        assertEquals(rByte, aByte);

        IOHelper.close(reader);
    }

    @Test
    public void testCompressWithLz4() throws Exception {
        byte aByte = 8;
        template.sendBody("direct:lz4", aByte);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-lz4");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        byte rByte = ((ByteWritable) value).get();
        assertEquals(rByte, aByte);

        IOHelper.close(reader);
    }

    @Test
    public void testCompressWithZStandard() throws Exception {
        assumeTrue(LD_LIBRARY_PATH_DEFINED);
        byte aByte = 8;
        template.sendBody("direct:zstandard", aByte);

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "/test-camel-zstandard");
        SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(file1));
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        byte rByte = ((ByteWritable) value).get();
        assertEquals(rByte, aByte);

        IOHelper.close(reader);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        Thread.sleep(250);
        Configuration conf = new Configuration();
        Path dir = new Path("target/test");
        FileSystem fs = FileSystem.get(dir.toUri(), conf);
        fs.delete(dir, true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:start1").to("hdfs://localhost/" + TEMP_DIR.toUri()
                                         + "/test-camel1?fileSystemType=LOCAL&valueType=TEXT&fileType=SEQUENCE_FILE");

                /* For testing writables */
                from("direct:write_boolean")
                        .to("hdfs:localhost/" + TEMP_DIR.toUri()
                            + "/test-camel-boolean?fileSystemType=LOCAL&valueType=BOOLEAN&fileType=SEQUENCE_FILE");

                from("direct:write_byte").to("hdfs:localhost/" + TEMP_DIR.toUri()
                                             + "/test-camel-byte?fileSystemType=LOCAL&valueType=BYTE&fileType=SEQUENCE_FILE");

                from("direct:write_short").to("hdfs:localhost/" + TEMP_DIR.toUri()
                                              + "/test-camel-short?fileSystemType=LOCAL&valueType=SHORT&fileType=SEQUENCE_FILE");

                from("direct:write_int").to("hdfs:localhost/" + TEMP_DIR.toUri()
                                            + "/test-camel-int?fileSystemType=LOCAL&valueType=INT&fileType=SEQUENCE_FILE");

                from("direct:write_float")
                        .to("hdfs:localhost/" + TEMP_DIR.toUri()
                            + "/test-camel-float?fileSystemType=LOCAL&valueType=FLOAT&fileType=SEQUENCE_FILE");

                from("direct:write_long").to("hdfs:localhost/" + TEMP_DIR.toUri()
                                             + "/test-camel-long?fileSystemType=LOCAL&valueType=LONG&fileType=SEQUENCE_FILE");

                from("direct:write_double")
                        .to("hdfs:localhost/" + TEMP_DIR.toUri()
                            + "/test-camel-double?fileSystemType=LOCAL&valueType=DOUBLE&fileType=SEQUENCE_FILE");

                from("direct:write_text1").to("hdfs:localhost/" + TEMP_DIR.toUri()
                                              + "/test-camel-text1?fileSystemType=LOCAL&valueType=TEXT&fileType=SEQUENCE_FILE");

                /* For testing key and value writing */
                from("direct:write_text2")
                        .to("hdfs:localhost/" + TEMP_DIR.toUri()
                            + "/test-camel-text2?fileSystemType=LOCAL&keyType=TEXT&valueType=TEXT&fileType=SEQUENCE_FILE");

                from("direct:write_text3")
                        .to("hdfs:localhost/" + TEMP_DIR.toUri()
                            + "/test-camel-text3?fileSystemType=LOCAL&keyType=TEXT&valueType=TEXT&fileType=MAP_FILE");

                /* For testing ArrayFile */
                from("direct:write_text4").to("hdfs:localhost/" + TEMP_DIR.toUri()
                                              + "/test-camel-text4?fileSystemType=LOCAL&valueType=TEXT&fileType=ARRAY_FILE");

                /* For testing BloomMapFile */
                from("direct:write_text5")
                        .to("hdfs:localhost/" + TEMP_DIR.toUri()
                            + "/test-camel-text5?fileSystemType=LOCAL&keyType=TEXT&valueType=TEXT&fileType=BLOOMMAP_FILE");

                from("direct:write_dynamic_filename")
                        .to("hdfs:localhost/" + TEMP_DIR.toUri() + "/test-camel-dynamic/?fileSystemType=LOCAL&valueType=TEXT");

                /* For testing compression codecs */
                from("direct:bzip2")
                        .to("hdfs:localhost/" + TEMP_DIR.toUri()
                            + "/test-camel-bzip2?fileSystemType=LOCAL&valueType=BYTE&fileType=SEQUENCE_FILE&compressionCodec=BZIP2&compressionType=BLOCK");

                from("direct:snappy")
                        .to("hdfs:localhost/" + TEMP_DIR.toUri()
                            + "/test-camel-snappy?fileSystemType=LOCAL&valueType=BYTE&fileType=SEQUENCE_FILE&compressionCodec=SNAPPY&compressionType=BLOCK");

                from("direct:lz4")
                        .to("hdfs:localhost/" + TEMP_DIR.toUri()
                            + "/test-camel-lz4?fileSystemType=LOCAL&valueType=BYTE&fileType=SEQUENCE_FILE&compressionCodec=LZ4&compressionType=BLOCK");

                // GZip and ZStandard requires native hadoop library. To run these tests,
                // 1. install shared libraries for these codecs (e.g., libz.so and libzstd.so on Linux)
                // 2. download pre-built native hadoop library, or build it yourself in accordance with
                //    https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-common/NativeLibraries.html
                // 3. set LD_LIBRARY_PATH to point native hadoop library when running tests, like
                //    `$ LD_LIBRARY_PATH=/path/to/hadoop/lib/native ./mvnw clean test -f components/camel-hdfs`
                if (LD_LIBRARY_PATH_DEFINED) {
                    from("direct:gzip")
                            .to("hdfs:localhost/" + TEMP_DIR.toUri()
                                + "/test-camel-gzip?fileSystemType=LOCAL&valueType=BYTE&fileType=SEQUENCE_FILE&compressionCodec=GZIP&compressionType=BLOCK");

                    from("direct:zstandard")
                            .to("hdfs:localhost/" + TEMP_DIR.toUri()
                                + "/test-camel-zstandard?fileSystemType=LOCAL&valueType=BYTE&fileType=SEQUENCE_FILE&compressionCodec=ZSTANDARD&compressionType=BLOCK");
                }
            }
        };
    }
}
