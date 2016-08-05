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
package org.apache.camel.component.spark;

import com.mongodb.hadoop.MongoInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.bson.BSONObject;

public final class SparkMongos {

    private SparkMongos() {
    }

    public static JavaPairRDD<Object, BSONObject> mongoRdd(JavaSparkContext sparkContext, String mongoHost, long mongoPort, String db, String collection) {
        Configuration mongodbConfig = new Configuration();
        mongodbConfig.set("mongo.job.input.format", "com.mongodb.hadoop.MongoInputFormat");
        mongodbConfig.set("mongo.input.uri", String.format("mongodb://%s:%d/%s.%s", mongoHost, mongoPort, db, collection));
        return sparkContext.newAPIHadoopRDD(mongodbConfig, MongoInputFormat.class, Object.class, BSONObject.class);
    }

}