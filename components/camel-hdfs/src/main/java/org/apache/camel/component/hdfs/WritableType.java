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

import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;

public enum WritableType {

    NULL {
        @Override
        public Class<NullWritable> getWritableClass() {
            return NullWritable.class;
        }
    },

    BOOLEAN {
        @Override
        public Class<BooleanWritable> getWritableClass() {
            return BooleanWritable.class;
        }
    },

    BYTE {
        @Override
        public Class<ByteWritable> getWritableClass() {
            return ByteWritable.class;
        }
    },

    INT {
        @Override
        public Class<IntWritable> getWritableClass() {
            return IntWritable.class;
        }
    },

    FLOAT {
        @Override
        public Class<FloatWritable> getWritableClass() {
            return FloatWritable.class;
        }
    },

    LONG {
        @Override
        public Class<LongWritable> getWritableClass() {
            return LongWritable.class;
        }
    },

    DOUBLE {
        @Override
        public Class<DoubleWritable> getWritableClass() {
            return DoubleWritable.class;
        }
    },

    TEXT {
        @Override
        public Class<Text> getWritableClass() {
            return Text.class;
        }
    },

    BYTES {
        @Override
        public Class<BytesWritable> getWritableClass() {
            return BytesWritable.class;
        }
    };

    @SuppressWarnings("rawtypes")
    public abstract Class<? extends WritableComparable> getWritableClass();
}
