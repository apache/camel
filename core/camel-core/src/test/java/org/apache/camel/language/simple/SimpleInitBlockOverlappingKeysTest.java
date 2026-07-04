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
package org.apache.camel.language.simple;

import org.apache.camel.LanguageTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the init-block key substitution ordering bug (L2). When one key is a prefix of another (e.g.
 * "$n" and "$name"), the shorter key must not be replaced first, or "$name" becomes "${variable.n}ame". The fix sorts
 * keys by descending length before substitution.
 */
public class SimpleInitBlockOverlappingKeysTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    /**
     * Two keys where the short key ("n") is a prefix of the long key ("name"). Without the fix, replacing "$n" first
     * corrupts "$name" into "${variable.n}ame".
     */
    private static final String OVERLAPPING_KEYS = """
            $init{
              $n := 'Bob';
              $name := 'Alice';
            }init$
            full=$name,short=$n
            """;

    /**
     * Three-level overlap: "v", "val", "value". The longest must be replaced first.
     */
    private static final String THREE_LEVEL_OVERLAP = """
            $init{
              $v := 'one';
              $val := 'two';
              $value := 'three';
            }init$
            $value,$val,$v
            """;

    @Test
    public void testOverlappingKeysTwoLevels() throws Exception {
        assertExpression(exchange, OVERLAPPING_KEYS, "full=Alice,short=Bob\n");
    }

    @Test
    public void testOverlappingKeysThreeLevels() throws Exception {
        assertExpression(exchange, THREE_LEVEL_OVERLAP, "three,two,one\n");
    }
}
