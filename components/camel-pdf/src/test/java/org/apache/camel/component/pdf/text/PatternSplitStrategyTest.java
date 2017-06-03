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
package org.apache.camel.component.pdf.text;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PatternSplitStrategyTest {
    private static final String PATTERN = "\n";
    private PatternSplitStrategy patternSplitStrategy = new PatternSplitStrategy(PATTERN);

    @Test
    public void testSplit() throws Exception {
        Collection<String> split = patternSplitStrategy.split("hello" + PATTERN + "world");
        assertEquals(2, split.size());
        assertEquals("world", new ArrayList<String>(split).get(1));
    }
}