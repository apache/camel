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
package org.apache.camel.component.sjms.jms;

/**
 * Default strategy that handles dots and hyphens.
 * <p/>
 * This can be used for sending keys contain package names that is common by
 * Java frameworks.
 */
public class DefaultJmsKeyFormatStrategy implements JmsKeyFormatStrategy {

    @Override
    public String encodeKey(String key) {
        String answer = key.replace(".", "_DOT_");
        answer = answer.replace("-", "_HYPHEN_");
        return answer;
    }

    @Override
    public String decodeKey(String key) {
        String answer = key.replace("_HYPHEN_", "-");
        answer = answer.replace("_DOT_", ".");
        return answer;
    }

}
