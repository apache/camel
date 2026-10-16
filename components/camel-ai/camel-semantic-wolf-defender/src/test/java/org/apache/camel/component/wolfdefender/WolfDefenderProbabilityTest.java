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
package org.apache.camel.component.wolfdefender;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class WolfDefenderProbabilityTest {
    @Test
    void mapsPositiveClassAndUsesStableSoftmax() {
        assertThat(probability((float) Math.log(0.97), (float) Math.log(0.03))).isCloseTo(0.03, within(1e-8));
        assertThat(probability(10000, 10000)).isEqualTo(0.5);
        assertThat(probability(-Float.MAX_VALUE, Float.MAX_VALUE)).isEqualTo(1);
        assertThat(probability(Float.MAX_VALUE, -Float.MAX_VALUE)).isZero();
        assertThat(probability(-10000, -10000)).isEqualTo(0.5);
    }

    @ParameterizedTest
    @MethodSource("malformed")
    void rejectsMalformedAndNonFiniteOutput(Object output) {
        assertThatThrownBy(() -> WolfDefenderInference.injectionProbability(output))
                .hasMessageContaining("finite logits with shape [1, 2]");
    }

    static Stream<Arguments> malformed() {
        return Stream.of(null, new float[0][], new float[][] { null }, new float[][] { { 1 } },
                new float[][] { { 1, 2, 3 } }, new float[][] { { 1, 2 }, { 3, 4 } },
                new double[][] { { 1, 2 } }, new float[][] { { Float.NaN, 1 } },
                new float[][] { { 1, Float.POSITIVE_INFINITY } }, new float[][] { { Float.NEGATIVE_INFINITY, 1 } })
                .map(value -> Arguments.of(new Object[] { value }));
    }

    private static double probability(float benign, float injection) {
        return WolfDefenderInference.injectionProbability(new float[][] { { benign, injection } });
    }
}
