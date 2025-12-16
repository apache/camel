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

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.camel.tooling.util.Strings;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;

public final class MojoHelper {

    private MojoHelper() {
    }

    public static List<Path> getComponentPath(Path dir) {
        switch (dir.getFileName().toString()) {
            case "camel-ai":
                return Arrays.asList(dir.resolve("camel-chatscript"), dir.resolve("camel-djl"),
                        dir.resolve("camel-langchain4j-agent"), dir.resolve("camel-langchain4j-chat"),
                        dir.resolve("camel-langchain4j-embeddings"), dir.resolve("camel-langchain4j-embeddingstore"),
                        dir.resolve("camel-langchain4j-tokenizer"), dir.resolve("camel-langchain4j-tools"),
                        dir.resolve("camel-langchain4j-web-search"),
                        dir.resolve("camel-qdrant"), dir.resolve("camel-milvus"), dir.resolve("camel-neo4j"),
                        dir.resolve("camel-pinecone"), dir.resolve("camel-kserve"),
                        dir.resolve("camel-torchserve"), dir.resolve("camel-tensorflow-serving"),
                        dir.resolve("camel-weaviate"), dir.resolve("camel-docling"));
            case "camel-as2":
                return Collections.singletonList(dir.resolve("camel-as2-component"));
            case "camel-avro-rpc":
                return Collections.singletonList(dir.resolve("camel-avro-rpc-component"));
            case "camel-cxf":
                return Arrays.asList(dir.resolve("camel-cxf-soap"), dir.resolve("camel-cxf-rest"));
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
                        dir.resolve("camel-google-pubsub-lite"), dir.resolve("camel-google-sheets"),
                        dir.resolve("camel-google-storage"), dir.resolve("camel-google-functions"),
                        dir.resolve("camel-google-secret-manager"), dir.resolve("camel-google-vertexai"));
            case "camel-debezium":
                return Arrays.asList(dir.resolve("camel-debezium-mongodb"), dir.resolve("camel-debezium-mysql"),
                        dir.resolve("camel-debezium-postgres"), dir.resolve("camel-debezium-sqlserver"),
                        dir.resolve("camel-debezium-oracle"), dir.resolve("camel-debezium-db2"));
            case "camel-microprofile":
                return Arrays.asList(dir.resolve("camel-microprofile-config"),
                        dir.resolve("camel-microprofile-fault-tolerance"),
                        dir.resolve("camel-microprofile-health"));
            case "camel-spring-parent":
                return Arrays.asList(dir.resolve("camel-spring"),
                        dir.resolve("camel-spring-batch"), dir.resolve("camel-spring-cloud-config"),
                        dir.resolve("camel-spring-jdbc"), dir.resolve("camel-spring-ldap"),
                        dir.resolve("camel-spring-main"), dir.resolve("camel-spring-rabbitmq"),
                        dir.resolve("camel-spring-redis"), dir.resolve("camel-spring-security"),
                        dir.resolve("camel-spring-ws"), dir.resolve("camel-spring-xml"),
                        dir.resolve("camel-undertow-spring-security"),
                        dir.resolve("camel-spring-ai").resolve("camel-spring-ai-chat"),
                        dir.resolve("camel-spring-ai").resolve("camel-spring-ai-embeddings"),
                        dir.resolve("camel-spring-ai").resolve("camel-spring-ai-tools"),
                        dir.resolve("camel-spring-ai").resolve("camel-spring-ai-vector-store"));
            case "camel-test":
                return Arrays.asList(dir.resolve("camel-test-junit5"),
                        dir.resolve("camel-test-junit6"),
                        dir.resolve("camel-test-spring-junit5"),
                        dir.resolve("camel-test-spring-junit6"),
                        dir.resolve("camel-test-main-junit5"),
                        dir.resolve("camel-test-main-junit6"));
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
                        dir.resolve("camel-aws-cloudtrail"), dir.resolve("camel-aws-config"), dir.resolve("camel-aws-bedrock"),
                        dir.resolve("camel-aws2-textract"), dir.resolve("camel-aws2-transcribe"),
                        dir.resolve("camel-aws2-s3-vectors"));
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
            case "camel-ibm":
                return Arrays.asList(dir.resolve("camel-ibm-cos"),
                        dir.resolve("camel-ibm-secrets-manager"),
                        dir.resolve("camel-ibm-watson-language"),
                        dir.resolve("camel-ibm-watson-discovery"),
                        dir.resolve("camel-ibm-watson-text-to-speech"),
                        dir.resolve("camel-ibm-watson-speech-to-text"));
            case "camel-knative":
                return Collections.singletonList(dir.resolve("camel-knative-component"));
            case "camel-yaml-dsl":
                return Collections.singletonList(dir.resolve("camel-yaml-dsl"));
            default:
                return Collections.singletonList(dir);
        }
    }

    public static String annotationValue(AnnotationInstance ann, String key) {
        if (ann == null) {
            return null;
        }
        var v = ann.value(key);
        if (v == null) {
            return null;
        }
        var o = v.value();
        if (o == null) {
            return null;
        }
        var s = o.toString();
        return s == null || s.isBlank() ? null : s;
    }

    public static String annotationValue(AnnotationInstance ann, String key, String subKey) {
        if (ann == null) {
            return null;
        }
        var v = ann.value(key);
        if (v == null) {
            return null;
        }
        var o = v.value();
        if (o == null) {
            return null;
        }
        AnnotationValue[] arr = (AnnotationValue[]) o;
        if (arr.length == 0) {
            return null;
        }
        for (AnnotationValue av : arr) {
            String s = av.value().toString();
            String before = Strings.before(s, "=");
            if (subKey.equals(before)) {
                return Strings.after(s, "=");
            }
        }
        return null;
    }

    /**
     * Gets the JSON schema type.
     *
     * @param  type the java type
     * @return      the json schema type, is never null, but returns <tt>object</tt> as the generic type
     */
    public static String getType(String type, boolean enumType, boolean isDuration) {
        if (enumType) {
            return "enum";
        } else if (isDuration) {
            return "duration";
        } else if (type == null) {
            // return generic type for unknown type
            return "object";
        } else if (type.equals(URI.class.getName()) || type.equals(URL.class.getName())) {
            return "string";
        } else if (type.equals(File.class.getName())) {
            return "string";
        } else if (type.equals(Date.class.getName())) {
            return "string";
        } else if (type.startsWith("java.lang.Class")) {
            return "string";
        } else if (type.startsWith("java.util.List") || type.startsWith("java.util.Collection")) {
            return "array";
        } else if (type.equals(Duration.class.getName())) {
            return "duration";
        }

        String primitive = getPrimitiveType(type);
        if (primitive != null) {
            return primitive;
        }

        return "object";
    }

    /**
     * Gets the JSON schema primitive type.
     *
     * @param  name the java type
     * @return      the json schema primitive type, or <tt>null</tt> if not a primitive
     */
    public static String getPrimitiveType(String name) {
        // special for byte[] or Object[] as its common to use
        if ("java.lang.byte[]".equals(name) || "byte[]".equals(name)) {
            return "string";
        } else if ("java.lang.Byte[]".equals(name) || "Byte[]".equals(name)) {
            return "array";
        } else if ("java.lang.Object[]".equals(name) || "Object[]".equals(name)) {
            return "array";
        } else if ("java.lang.String[]".equals(name) || "String[]".equals(name)) {
            return "array";
        } else if ("java.lang.Character".equals(name) || "Character".equals(name) || "char".equals(name)) {
            return "string";
        } else if ("java.lang.String".equals(name) || "String".equals(name)) {
            return "string";
        } else if ("java.lang.Boolean".equals(name) || "Boolean".equals(name) || "boolean".equals(name)) {
            return "boolean";
        } else if ("java.lang.Integer".equals(name) || "Integer".equals(name) || "int".equals(name)) {
            return "integer";
        } else if ("java.lang.Long".equals(name) || "Long".equals(name) || "long".equals(name)) {
            return "integer";
        } else if ("java.lang.Short".equals(name) || "Short".equals(name) || "short".equals(name)) {
            return "integer";
        } else if ("java.lang.Byte".equals(name) || "Byte".equals(name) || "byte".equals(name)) {
            return "integer";
        } else if ("java.lang.Float".equals(name) || "Float".equals(name) || "float".equals(name)) {
            return "number";
        } else if ("java.lang.Double".equals(name) || "Double".equals(name) || "double".equals(name)) {
            return "number";
        }

        return null;
    }
}
