/**
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
package org.apache.camel.component.aws.ddbstream;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


@RunWith(Parameterized.class)
public class BigIntComparisonsTest {


    private final BigIntComparisons condition;
    private final int smaller;
    private final int bigger;
    private final boolean result;

    public BigIntComparisonsTest(BigIntComparisons condition, int smaller, int bigger, boolean result) {
        this.condition = condition;
        this.smaller = smaller;
        this.bigger = bigger;
        this.result = result;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        List<Object[]> results = new ArrayList<>();

        results.add(new Object[]{BigIntComparisons.Conditions.LT, 1, 5, true});
        results.add(new Object[]{BigIntComparisons.Conditions.LTEQ, 1, 5, true});
        results.add(new Object[]{BigIntComparisons.Conditions.LT, 1, 1, false});
        results.add(new Object[]{BigIntComparisons.Conditions.LTEQ, 1, 1, true});
        results.add(new Object[]{BigIntComparisons.Conditions.LT, 5, 1, false});
        results.add(new Object[]{BigIntComparisons.Conditions.LTEQ, 5, 1, false});

        return results;
    }

    @Test
    public void test() throws Exception {
        assertThat(condition.matches(BigInteger.valueOf(smaller), BigInteger.valueOf(bigger)), is(result));
    }

}