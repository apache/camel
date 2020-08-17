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
package org.apache.camel.component.aws.ddbstream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class ShardListAfterSequenceParametrisedTest {
    private ShardList undertest;

    public static Collection<Object[]> parameters() {
        List<Object[]> results = new ArrayList<>();
        results.add(new Object[] { "0", "a" });
        results.add(new Object[] { "3", "a" });
        results.add(new Object[] { "6", "b" });
        results.add(new Object[] { "8", "b" });
        results.add(new Object[] { "15", "c" });
        results.add(new Object[] { "16", "d" });
        results.add(new Object[] { "18", "d" });
        results.add(new Object[] { "25", "d" });
        results.add(new Object[] { "30", "d" });
        return results;
    }

    @BeforeEach
    public void setup() throws Exception {
        undertest = new ShardList();
        undertest.addAll(ShardListTest.createShardsWithSequenceNumbers(null,
                "a", "1", "5",
                "b", "8", "15",
                "c", "16", "16",
                "d", "20", null));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void assertions(String inputSequenceNumber, String expectedShardId) throws Exception {
        assertEquals(expectedShardId, undertest.afterSeq(inputSequenceNumber).getShardId());
    }
}
