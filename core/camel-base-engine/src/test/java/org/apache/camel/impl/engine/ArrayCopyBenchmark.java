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
package org.apache.camel.impl.engine;

import java.util.Arrays;

import org.openjdk.jmh.annotations.*;

@State(Scope.Benchmark)
public class ArrayCopyBenchmark {

    private static final int ARRAY_SIZE = 10; // Adjust as needed

    private Object[] sourceArray;
    private Object[] destArray;

    @Setup
    public void setup() {
        sourceArray = new Object[ARRAY_SIZE];
        destArray = new Object[ARRAY_SIZE];
    }

    @Benchmark
    public void systemArrayCopy() {
        System.arraycopy(sourceArray, 0, destArray, 0, ARRAY_SIZE);
    }

    @Benchmark
    public void arraysFill() {
        Arrays.fill(destArray, null);
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
