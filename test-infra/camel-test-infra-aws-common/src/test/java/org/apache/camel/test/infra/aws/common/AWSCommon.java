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

package org.apache.camel.test.infra.aws.common;

public final class AWSCommon {
    /**
     * The default SQS queue name used during the tests
     */
    public static final String BASE_SQS_QUEUE_NAME = "ckc";

    /**
     * The default SQS queue name used during the tests
     */
    public static final String DEFAULT_SQS_QUEUE_FOR_SNS = "ckcsns";

    /**
     * The default S3 bucket name used during the tests
     */
    public static final String DEFAULT_S3_BUCKET = "ckc-s3";

    /**
     * Base name for the Kinesis stream
     */
    public static final String KINESIS_STREAM_BASE_NAME = "ckc-kin-stream";

    private AWSCommon() {
    }

}
