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
package org.apache.camel.component.minio;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Properties;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;

public final class MinioTestUtils {

    private MinioTestUtils() {
    }

    public static Properties loadMinioPropertiesFile() throws IOException {
        final Properties properties = new Properties();
        final String fileName = "minio_key.properties";

        final InputStream inputStream
                = Objects.requireNonNull(MinioTestUtils.class.getClassLoader().getResourceAsStream(fileName));

        properties.load(inputStream);

        return properties;
    }

    /**
     * Counts the objects stored in a bucket
     *
     * @param  client the MinioClient
     * @param  bucket the bucket name
     * @return        objects count of the specified bucket
     */
    public static int countObjectsInBucket(MinioClient client, String bucket) {
        Iterable result = client.listObjects(ListObjectsArgs.builder().bucket(bucket).build());
        ArrayList arrayList = new ArrayList<>();
        result.forEach(arrayList::add);
        return arrayList.size();
    }
}
