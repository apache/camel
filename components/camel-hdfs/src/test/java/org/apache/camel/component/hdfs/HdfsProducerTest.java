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

import java.io.File;

import junit.framework.Assert;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.ArrayFile;
import org.apache.hadoop.io.BloomMapFile;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.MapFile;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.util.ReflectionUtils;
import org.junit.Test;

public class HdfsProducerTest extends CamelTestSupport {
    
    private static final Path TEMP_DIR = new Path(new File("target/test/").getAbsolutePath());

    @Test
    public void testProducer() throws Exception {
        for (int i = 0; i < 10; ++i) {
            template.sendBody("direct:start1", "PAPPO" + i);
        }
        stopCamelContext();
        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "test-camel1");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        SequenceFile.Reader reader = new SequenceFile.Reader(fs1, file1, conf);
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        int i = 0;
        while (reader.next(key, value)) {
            Text txt = (Text) value;
            Assert.assertTrue(("PAPPO" + i).equals(txt.toString()));
            ++i;
        }
    }

    @Test
    public void testWriteBoolean() throws Exception {
        Boolean aBoolean = true;
        template.sendBody("direct:write_boolean", aBoolean);
        stopCamelContext();
        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "test-camel-boolean");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        SequenceFile.Reader reader = new SequenceFile.Reader(fs1, file1, conf);
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        Boolean rBoolean = ((BooleanWritable) value).get();
        Assert.assertEquals(rBoolean, aBoolean);
    }

    @Test
    public void testWriteByte() throws Exception {
        byte aByte = 8;
        template.sendBody("direct:write_byte", aByte);
        stopCamelContext();
        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "test-camel-byte");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        SequenceFile.Reader reader = new SequenceFile.Reader(fs1, file1, conf);
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        byte rByte = ((ByteWritable) value).get();
        Assert.assertEquals(rByte, aByte);
    }

    @Test
    public void testWriteInt() throws Exception {
        int anInt = 1234;
        template.sendBody("direct:write_int", anInt);
        stopCamelContext();
        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "test-camel-int");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        SequenceFile.Reader reader = new SequenceFile.Reader(fs1, file1, conf);
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        int rInt = ((IntWritable) value).get();
        Assert.assertEquals(rInt, anInt);
    }

    @Test
    public void testWriteFloat() throws Exception {
        float aFloat = 12.34f;
        template.sendBody("direct:write_float", aFloat);
        stopCamelContext();
        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "test-camel-float");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        SequenceFile.Reader reader = new SequenceFile.Reader(fs1, file1, conf);
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        float rFloat = ((FloatWritable) value).get();
        Assert.assertEquals(rFloat, aFloat);
    }

    @Test
    public void testWriteDouble() throws Exception {
        double aDouble = 12.34d;
        template.sendBody("direct:write_double", aDouble);
        stopCamelContext();
        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "test-camel-double");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        SequenceFile.Reader reader = new SequenceFile.Reader(fs1, file1, conf);
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        Double rDouble = ((DoubleWritable) value).get();
        Assert.assertEquals(rDouble, aDouble);
    }

    @Test
    public void testWriteLong() throws Exception {
        long aLong = 1234567890;
        template.sendBody("direct:write_long", aLong);
        stopCamelContext();
        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "test-camel-long");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        SequenceFile.Reader reader = new SequenceFile.Reader(fs1, file1, conf);
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Writable value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        long rLong = ((LongWritable) value).get();
        Assert.assertEquals(rLong, aLong);
    }

    @Test
    public void testWriteText() throws Exception {
        String txt = "CIAO MONDO !";
        template.sendBody("direct:write_text1", txt);
        stopCamelContext();
        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "test-camel-text1");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        SequenceFile.Reader reader = new SequenceFile.Reader(fs1, file1, conf);
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Text value = (Text) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        String rTxt = ((Text) value).toString();
        Assert.assertEquals(rTxt, txt);
    }

    @Test
    public void testWriteTextWithKey() throws Exception {
        String txtKey = "THEKEY";
        String txtValue = "CIAO MONDO !";
        template.sendBodyAndHeader("direct:write_text2", txtValue, "KEY", txtKey);
        stopCamelContext();
        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "test-camel-text2");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        SequenceFile.Reader reader = new SequenceFile.Reader(fs1, file1, conf);
        Text key = (Text) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Text value = (Text) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        Assert.assertEquals(key.toString(), txtKey);
        Assert.assertEquals(value.toString(), txtValue);
    }

    @Test
    public void testMapWriteTextWithKey() throws Exception {
        String txtKey = "THEKEY";
        String txtValue = "CIAO MONDO !";
        template.sendBodyAndHeader("direct:write_text3", txtValue, "KEY", txtKey);
        stopCamelContext();
        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "test-camel-text3");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        MapFile.Reader reader = new MapFile.Reader(fs1, "file:///" + TEMP_DIR.toUri() + "test-camel-text3", conf);
        Text key = (Text) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Text value = (Text) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        Assert.assertEquals(key.toString(), txtKey);
        Assert.assertEquals(value.toString(), txtValue);
    }

    @Test
    public void testArrayWriteText() throws Exception {
        String txtValue = "CIAO MONDO !";
        template.sendBody("direct:write_text4", txtValue);
        stopCamelContext();
        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "test-camel-text4");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        ArrayFile.Reader reader = new ArrayFile.Reader(fs1, "file:///" + TEMP_DIR.toUri() + "test-camel-text4", conf);
        Text value = (Text) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(value);
        Assert.assertEquals(value.toString(), txtValue);
    }

    @Test
    public void testBloomMapWriteText() throws Exception {
        String txtKey = "THEKEY";
        String txtValue = "CIAO MONDO !";
        template.sendBodyAndHeader("direct:write_text5", txtValue, "KEY", txtKey);
        stopCamelContext();
        Configuration conf = new Configuration();
        Path file1 = new Path("file:///" + TEMP_DIR.toUri() + "test-camel-text5");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        BloomMapFile.Reader reader = new BloomMapFile.Reader(fs1, "file:///" + TEMP_DIR.toUri() + "test-camel-text5", conf);
        Text key = (Text) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        Text value = (Text) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        reader.next(key, value);
        Assert.assertEquals(key.toString(), txtKey);
        Assert.assertEquals(value.toString(), txtValue);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        Thread.sleep(100);
        Configuration conf = new Configuration();
        Path dir = new Path("target/test");
        FileSystem fs = FileSystem.get(dir.toUri(), conf);
        fs.delete(dir, true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:start1").to("hdfs:///" + TEMP_DIR.toUri() + "test-camel1?fileSystemType=LOCAL&valueType=TEXT&fileType=SEQUENCE_FILE");

                /* For testing writables */
                from("direct:write_boolean").to("hdfs:///" + TEMP_DIR.toUri() + "test-camel-boolean?fileSystemType=LOCAL&valueType=BOOLEAN&fileType=SEQUENCE_FILE");

                from("direct:write_byte").to("hdfs:///" + TEMP_DIR.toUri() + "test-camel-byte?fileSystemType=LOCAL&valueType=BYTE&fileType=SEQUENCE_FILE");

                from("direct:write_int").to("hdfs:///" + TEMP_DIR.toUri() + "test-camel-int?fileSystemType=LOCAL&valueType=INT&fileType=SEQUENCE_FILE");

                from("direct:write_float").to("hdfs:///" + TEMP_DIR.toUri() + "test-camel-float?fileSystemType=LOCAL&valueType=FLOAT&fileType=SEQUENCE_FILE");

                from("direct:write_long").to("hdfs:///" + TEMP_DIR.toUri() + "test-camel-long?fileSystemType=LOCAL&valueType=LONG&fileType=SEQUENCE_FILE");

                from("direct:write_double").to("hdfs:///" + TEMP_DIR.toUri() + "test-camel-double?fileSystemType=LOCAL&valueType=DOUBLE&fileType=SEQUENCE_FILE");

                from("direct:write_text1").to("hdfs:///" + TEMP_DIR.toUri() + "test-camel-text1?fileSystemType=LOCAL&valueType=TEXT&fileType=SEQUENCE_FILE");

                /* For testing key and value writing */
                from("direct:write_text2").to("hdfs:///" + TEMP_DIR.toUri() + "test-camel-text2?fileSystemType=LOCAL&keyType=TEXT&valueType=TEXT&fileType=SEQUENCE_FILE");

                from("direct:write_text3").to("hdfs:///" + TEMP_DIR.toUri() + "test-camel-text3?fileSystemType=LOCAL&keyType=TEXT&valueType=TEXT&fileType=MAP_FILE");

                /* For testing ArrayFile */
                from("direct:write_text4").to("hdfs:///" + TEMP_DIR.toUri() + "test-camel-text4?fileSystemType=LOCAL&valueType=TEXT&fileType=ARRAY_FILE");

                /* For testing BloomMapFile */
                from("direct:write_text5").to("hdfs:///" + TEMP_DIR.toUri() + "test-camel-text5?fileSystemType=LOCAL&keyType=TEXT&valueType=TEXT&fileType=BLOOMMAP_FILE");
            }
        };
    }
}
