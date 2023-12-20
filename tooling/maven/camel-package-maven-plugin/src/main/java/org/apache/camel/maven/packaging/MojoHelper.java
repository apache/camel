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
package org.apache.camel.maven.packaging;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MojoHelper {

    private MojoHelper() {
    }

    public static List<Path> getComponentPath(Path dir) {
        switch (dir.getFileName().toString()) {
            case "camel-as2":
                return Collections.singletonList(dir.resolve("camel-as2-component"));
            case "camel-avro-rpc":
                return Collections.singletonList(dir.resolve("camel-avro-rpc-component"));
            case "camel-cxf":
                return Arrays.asList(dir.resolve("camel-cxf-soap"),
                        dir.resolve("camel-cxf-rest"));
            case "camel-salesforce":
                return Collections.singletonList(dir.resolve("camel-salesforce-component"));
            case "camel-dhis2":
                return Collections.singletonList(dir.resolve("camel-dhis2-component"));
            case "camel-olingo2":
                return Collections.singletonList(dir.resolve("camel-olingo2-component"));
            case "camel-olingo4":
                return Collections.singletonList(dir.resolve("camel-olingo4-component"));
            case "camel-box":
                return Collections.singletonList(dir.resolve("camel-box-component"));
            case "camel-servicenow":
                return Collections.singletonList(dir.resolve("camel-servicenow-component"));
            case "camel-fhir":
                return Collections.singletonList(dir.resolve("camel-fhir-component"));
            case "camel-infinispan":
                return Arrays.asList(dir.resolve("camel-infinispan"), dir.resolve("camel-infinispan-embedded"));
            case "camel-azure":
                return Arrays.asList(dir.resolve("camel-azure-eventhubs"), dir.resolve("camel-azure-storage-blob"),
                        dir.resolve("camel-azure-storage-datalake"), dir.resolve("camel-azure-cosmosdb"),
                        dir.resolve("camel-azure-storage-queue"), dir.resolve("camel-azure-servicebus"),
                        dir.resolve("camel-azure-key-vault"), dir.resolve("camel-azure-files"),
                        dir.resolve("camel-azure-schema-registry"));
            case "camel-google":
                return Arrays.asList(dir.resolve("camel-google-bigquery"), dir.resolve("camel-google-calendar"),
                        dir.resolve("camel-google-drive"), dir.resolve("camel-google-mail"), dir.resolve("camel-google-pubsub"),
                        dir.resolve("camel-google-sheets"),
                        dir.resolve("camel-google-storage"), dir.resolve("camel-google-functions"),
                        dir.resolve("camel-google-secret-manager"));
            case "camel-debezium":
                return Arrays.asList(dir.resolve("camel-debezium-mongodb"), dir.resolve("camel-debezium-mysql"),
                        dir.resolve("camel-debezium-postgres"), dir.resolve("camel-debezium-sqlserver"),
                        dir.resolve("camel-debezium-oracle"), dir.resolve("camel-debezium-db2"));
            case "camel-microprofile":
                return Arrays.asList(dir.resolve("camel-microprofile-config"),
                        dir.resolve("camel-microprofile-fault-tolerance"),
                        dir.resolve("camel-microprofile-health"));
            case "camel-test":
                return Arrays.asList(dir.resolve("camel-test-junit5"),
                        dir.resolve("camel-test-spring-junit5"),
                        dir.resolve("camel-test-main-junit5"));
            case "camel-aws":
                return Arrays.asList(dir.resolve("camel-aws2-athena"), dir.resolve("camel-aws2-cw"),
                        dir.resolve("camel-aws2-ddb"), dir.resolve("camel-aws2-ec2"),
                        dir.resolve("camel-aws2-ecs"), dir.resolve("camel-aws2-eks"), dir.resolve("camel-aws2-eventbridge"),
                        dir.resolve("camel-aws2-iam"),
                        dir.resolve("camel-aws2-kinesis"), dir.resolve("camel-aws2-kms"), dir.resolve("camel-aws2-lambda"),
                        dir.resolve("camel-aws2-mq"),
                        dir.resolve("camel-aws2-msk"), dir.resolve("camel-aws2-redshift"),
                        dir.resolve("camel-aws2-s3"), dir.resolve("camel-aws2-ses"),
                        dir.resolve("camel-aws2-sns"),
                        dir.resolve("camel-aws2-sqs"), dir.resolve("camel-aws2-step-functions"),
                        dir.resolve("camel-aws2-sts"),
                        dir.resolve("camel-aws2-timestream"), dir.resolve("camel-aws2-translate"),
                        dir.resolve("camel-aws-xray"), dir.resolve("camel-aws-secrets-manager"),
                        dir.resolve("camel-aws-cloudtrail"), dir.resolve("camel-aws-config"));
            case "camel-vertx":
                return Arrays.asList(dir.resolve("camel-vertx"),
                        dir.resolve("camel-vertx-http"),
                        dir.resolve("camel-vertx-websocket"));
            case "camel-huawei":
                return Arrays.asList(dir.resolve("camel-huaweicloud-frs"),
                        dir.resolve("camel-huaweicloud-dms"),
                        dir.resolve("camel-huaweicloud-functiongraph"),
                        dir.resolve("camel-huaweicloud-iam"),
                        dir.resolve("camel-huaweicloud-imagerecognition"),
                        dir.resolve("camel-huaweicloud-obs"),
                        dir.resolve("camel-huaweicloud-smn"));
            case "camel-knative":
                return Collections.singletonList(dir.resolve("camel-knative-component"));
            case "camel-groovy-dsl":
                return Collections.singletonList(dir.resolve("camel-groovy-dsl"));
            case "camel-yaml-dsl":
                return Collections.singletonList(dir.resolve("camel-yaml-dsl"));
            default:
                return Collections.singletonList(dir);
        }
    }

}
