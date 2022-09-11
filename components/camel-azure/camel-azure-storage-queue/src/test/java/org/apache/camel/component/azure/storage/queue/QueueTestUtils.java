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
package org.apache.camel.component.azure.storage.queue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.fail;

public final class QueueTestUtils {

    private QueueTestUtils() {
    }

    public static Properties loadAzurePropertiesFile() throws IOException {
        final Properties properties = new Properties();
        final String fileName = "azure_key.properties";

        final InputStream inputStream
                = Objects.requireNonNull(QueueTestUtils.class.getClassLoader().getResourceAsStream(fileName));

        properties.load(inputStream);

        return properties;
    }

    public static Properties loadAzureAccessFromJvmEnv() {
        final Properties properties = new Properties();
        if (System.getProperty("accountName") == null || System.getProperty("accessKey") == null) {
            fail("Make sure to supply azure accessKey or accountName, e.g:  mvn verify -DaccountName=myacc -DaccessKey=mykey");
        }
        properties.setProperty("account_name", System.getProperty("accountName"));
        properties.setProperty("access_key", System.getProperty("accessKey"));

        return properties;
    }

}
