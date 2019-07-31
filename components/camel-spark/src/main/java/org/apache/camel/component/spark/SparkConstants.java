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
package org.apache.camel.component.spark;

public final class SparkConstants {

    public static final String SPARK_RDD_HEADER = "CAMEL_SPARK_RDD";

    public static final String SPARK_RDD_CALLBACK_HEADER = "CAMEL_SPARK_RDD_CALLBACK";

    public static final String SPARK_DATAFRAME_HEADER = "CAMEL_SPARK_DATAFRAME";

    public static final String SPARK_DATAFRAME_CALLBACK_HEADER = "CAMEL_SPARK_DATAFRAME_CALLBACK";

    private SparkConstants() {
    }

}
