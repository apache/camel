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
package org.apache.camel.component.jms;

import org.apache.camel.util.StringHelper;

/**
 * Default strategy that handles dots and hyphens.
 * <p/>
 * This can be used for sending keys containing package names that is common by Java frameworks.
 */
public class DefaultJmsKeyFormatStrategy implements JmsKeyFormatStrategy {

    private static final String DOT = ".";
    private static final String DOT_REPLACEMENT = "_DOT_";
    private static final String HYPHEN = "-";
    private static final String HYPHEN_REPLACEMENT = "_HYPHEN_";

    @Override
    public String encodeKey(String key) {
        String answer = StringHelper.replaceAll(key, DOT, DOT_REPLACEMENT);
        answer = StringHelper.replaceAll(answer, HYPHEN, HYPHEN_REPLACEMENT);
        return answer;
    }

    @Override
    public String decodeKey(String key) {
        String answer = StringHelper.replaceAll(key, DOT_REPLACEMENT, DOT);
        answer = StringHelper.replaceAll(answer, HYPHEN_REPLACEMENT, HYPHEN);
        return answer;
    }

}
