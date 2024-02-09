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
package org.apache.camel.component.aws2.firehose.client;

import org.apache.camel.component.aws2.firehose.KinesisFirehose2Configuration;
import org.apache.camel.component.aws2.firehose.client.impl.KinesisFirehoseClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.firehose.client.impl.KinesisFirehoseClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.firehose.client.impl.KinesisFirehoseClientSessionTokenImpl;
import org.apache.camel.component.aws2.firehose.client.impl.KinesisFirehoseClientStandardImpl;

/**
 * Factory class to return the correct type of AWS Kinesis client.
 */
public final class KinesisFirehoseClientFactory {

    private KinesisFirehoseClientFactory() {
    }

    /**
     * Return the correct aws Kinesis Firehose client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               FirehoseClient
     */
    public static KinesisFirehoseInternalClient getKinesisFirehoseClient(KinesisFirehose2Configuration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new KinesisFirehoseClientIAMOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new KinesisFirehoseClientIAMProfileOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new KinesisFirehoseClientSessionTokenImpl(configuration);
        } else {
            return new KinesisFirehoseClientStandardImpl(configuration);
        }
    }
}
