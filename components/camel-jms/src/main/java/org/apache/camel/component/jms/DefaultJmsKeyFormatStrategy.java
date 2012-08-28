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
package org.apache.camel.component.jms;

import java.util.regex.Pattern;

/**
 * Default strategy that handles dots and hyphens.
 * <p/>
 * This can be used for sending keys containing package names that is common by Java frameworks.
 *
 * @version 
 */
public class DefaultJmsKeyFormatStrategy implements JmsKeyFormatStrategy {

    // use pre compiled patterns as they are faster
    private static final String DOT = ".";
    private static final Pattern DOT_PATTERN = Pattern.compile(DOT, Pattern.LITERAL);

    private static final String DOT_REPLACEMENT = "_DOT_";
    private static final Pattern DOT_REPLACEMENT_PATTERN = Pattern.compile(DOT_REPLACEMENT, Pattern.LITERAL);

    private static final String HYPHEN = "-";
    private static final Pattern HYPHEN_PATTERN = Pattern.compile(HYPHEN, Pattern.LITERAL);

    private static final String HYPHEN_REPLACEMENT = "_HYPHEN_";
    private static final Pattern HYPHEN_REPLACEMENT_PATTERN = Pattern.compile(HYPHEN_REPLACEMENT, Pattern.LITERAL);

    public String encodeKey(String key) {
        String answer = DOT_PATTERN.matcher(key).replaceAll(DOT_REPLACEMENT);
        answer = HYPHEN_PATTERN.matcher(answer).replaceAll(HYPHEN_REPLACEMENT);
        return answer;
    }

    public String decodeKey(String key) {
        String answer = HYPHEN_REPLACEMENT_PATTERN.matcher(key).replaceAll(HYPHEN);
        answer = DOT_REPLACEMENT_PATTERN.matcher(answer).replaceAll(DOT);
        return answer;
    }

}
