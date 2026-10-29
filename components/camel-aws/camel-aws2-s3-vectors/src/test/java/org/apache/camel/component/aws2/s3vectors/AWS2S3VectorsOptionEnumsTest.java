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
package org.apache.camel.component.aws2.s3vectors;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3vectors.model.DataType;
import software.amazon.awssdk.services.s3vectors.model.DistanceMetric;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dataType and distanceMetric options offer only the values that the AWS SDK knows.
 */
class AWS2S3VectorsOptionEnumsTest {

    @Test
    void dataTypeOffersOnlyTheValuesOfTheSdk() throws Exception {
        assertThat(enumsOf("dataType"))
                .containsExactlyInAnyOrderElementsOf(DataType.knownValues().stream().map(DataType::toString).toList());
    }

    @Test
    void distanceMetricOffersOnlyTheValuesOfTheSdk() throws Exception {
        assertThat(enumsOf("distanceMetric"))
                .containsExactlyInAnyOrderElementsOf(
                        DistanceMetric.knownValues().stream().map(DistanceMetric::toString).toList());
    }

    private static List<String> enumsOf(String option) throws Exception {
        try (InputStream is = Objects.requireNonNull(AWS2S3VectorsOptionEnumsTest.class.getResourceAsStream(
                "/META-INF/org/apache/camel/component/aws2/s3vectors/aws2-s3-vectors.json"));
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject json = (JsonObject) Jsoner.deserialize(reader);
            JsonObject properties = json.getMap("properties");
            JsonObject property = properties.getMap(option);
            List<?> values = property.getCollection("enum");
            return values.stream().map(String.class::cast).toList();
        }
    }
}
