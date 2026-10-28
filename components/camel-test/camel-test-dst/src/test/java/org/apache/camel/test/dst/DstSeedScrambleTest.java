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
package org.apache.camel.test.dst;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seed of a run is scrambled before it seeds the random source: consecutive seeds must not draw correlated first
 * values, and a seed must still replay exactly.
 */
class DstSeedScrambleTest {

    private static final int SEEDS = 1000;

    @Test
    void unscrambledRandomDrawsCorrelatedFirstValuesForConsecutiveSeeds() {
        // the reason for the scramble: with new Random(seed), the first coin flip of seeds 1..1000 is always the same
        Set<Integer> firstCoin = new HashSet<>();
        for (long seed = 1; seed <= SEEDS; seed++) {
            firstCoin.add(new Random(seed).nextInt(2));
        }
        assertEquals(1, firstCoin.size());
    }

    @Test
    void firstDrawsOfConsecutiveSeedsAreWellSpread() {
        int[] coin = new int[2];
        int[] four = new int[4];
        int[] ten = new int[10];
        int chance = 0;
        Set<Long> between = new HashSet<>();
        for (long seed = 1; seed <= SEEDS; seed++) {
            coin[first(seed, d -> (long) d.nextInt(2)).intValue()]++;
            four[first(seed, d -> (long) d.pick(List.of(0, 1, 2, 3))).intValue()]++;
            ten[first(seed, d -> (long) d.nextInt(10)).intValue()]++;
            chance += first(seed, d -> d.chance(0.5) ? 1L : 0L).intValue();
            between.add(first(seed, d -> d.between(0, 999)));
        }
        // expected 500, 250 and 100 per bucket; the bounds are more than 4 standard deviations away
        assertBuckets(coin, 430, 570);
        assertBuckets(four, 180, 320);
        assertBuckets(ten, 60, 140);
        assertTrue(chance > 430 && chance < 570, "chance(0.5) held in " + chance + " of " + SEEDS + " seeds");
        // 1000 draws of 1000 values: about 632 distinct values are expected
        assertTrue(between.size() > 580, "only " + between.size() + " distinct first values of between(0, 999)");
    }

    @Test
    void aSeedReplaysExactly() {
        assertEquals(draws(4711), draws(4711));
        assertNotEquals(draws(4711), draws(4712));
    }

    @Test
    void differentSeedsGetDifferentRandomSources() {
        Set<Long> scrambled = new HashSet<>();
        for (long seed = 0; seed < 100_000; seed++) {
            scrambled.add(Dst.scramble(seed));
        }
        assertEquals(100_000, scrambled.size());
    }

    private interface Draw {
        Long draw(Dst dst);
    }

    private static Long first(long seed, Draw draw) {
        Dst dst = new Dst(seed);
        try {
            return draw.draw(dst);
        } finally {
            dst.close();
        }
    }

    private static List<Long> draws(long seed) {
        Dst dst = new Dst(seed);
        try {
            List<Long> answer = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                answer.add(dst.between(0, 1_000_000));
            }
            return answer;
        } finally {
            dst.close();
        }
    }

    private static void assertBuckets(int[] buckets, int min, int max) {
        for (int i = 0; i < buckets.length; i++) {
            int n = buckets[i];
            assertTrue(n >= min && n <= max, "value " + i + " drawn first by " + n + " of " + SEEDS + " seeds");
        }
    }
}
