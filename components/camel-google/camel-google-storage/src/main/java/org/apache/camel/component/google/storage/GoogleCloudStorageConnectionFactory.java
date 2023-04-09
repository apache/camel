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
package org.apache.camel.component.google.storage;

import java.io.InputStream;

import com.google.api.client.util.Strings;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;

public final class GoogleCloudStorageConnectionFactory {

    /**
     * Prevent instantiation.
     */
    private GoogleCloudStorageConnectionFactory() {
    }

    public static Storage create(CamelContext context, GoogleCloudStorageConfiguration configuration) throws Exception {
        if (!Strings.isNullOrEmpty(configuration.getServiceAccountKey())) {
            InputStream resolveMandatoryResourceAsInputStream
                    = ResourceHelper.resolveMandatoryResourceAsInputStream(context, configuration.getServiceAccountKey());
            return StorageOptions.newBuilder()
                    .setCredentials(
                            ServiceAccountCredentials.fromStream(resolveMandatoryResourceAsInputStream))
                    .build().getService();
        } else {
            return StorageOptions.getDefaultInstance().getService();
        }
    }

}
