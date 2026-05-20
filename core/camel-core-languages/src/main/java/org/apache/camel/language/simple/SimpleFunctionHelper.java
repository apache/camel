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

import java.util.ArrayList;
import java.util.List;

public final class SimpleFunctionHelper {

    private SimpleFunctionHelper() {
    }

    public static String[] codeSplitSafe(String input, char separator, boolean trim, boolean keepQuotes) {
        if (input == null) {
            return null;
        }

        if (input.indexOf(separator) == -1) {
            if (input.length() > 1) {
                char ch = input.charAt(0);
                char ch2 = input.charAt(input.length() - 1);
                boolean singleQuoted = ch == '\'' && ch2 == '\'';
                boolean doubleQuoted = ch == '"' && ch2 == '"';
                if (!keepQuotes && (singleQuoted || doubleQuoted)) {
                    input = input.substring(1, input.length() - 1);
                    // do not trim quoted text
                } else if (trim) {
                    input = input.trim();
                }
            }
            return new String[] { input };
        }

        List<String> answer = new ArrayList<>();
        StringBuilder sb = new StringBuilder(256);

        int codeLevel = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean separating = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            char prev = i > 0 ? input.charAt(i - 1) : 0;
            boolean isQuoting = singleQuoted || doubleQuoted;
            boolean last = i == input.length() - 1;

            if (input.indexOf(BaseSimpleParser.CODE_START, i) == i) {
                codeLevel++;
                sb.append(BaseSimpleParser.CODE_START);
                i = i + BaseSimpleParser.CODE_START.length() - 1;
                continue;
            } else if (input.indexOf(BaseSimpleParser.CODE_END, i) == i) {
                codeLevel--;
                sb.append(BaseSimpleParser.CODE_END);
                i = i + BaseSimpleParser.CODE_END.length() - 1;
                continue;
            }
            if (codeLevel > 0) {
                sb.append(ch);
                continue;
            }

            if (!doubleQuoted && ch == '\'') {
                if (!singleQuoted) {
                    singleQuoted = true;
                    if (keepQuotes) {
                        sb.append(ch);
                    }
                    continue;
                } else if (prev != '\\') {
                    singleQuoted = false;
                    if (keepQuotes) {
                        sb.append(ch);
                    }
                    continue;
                }
            } else if (!singleQuoted && ch == '"') {
                if (!doubleQuoted) {
                    doubleQuoted = true;
                    if (keepQuotes) {
                        sb.append(ch);
                    }
                    continue;
                } else if (prev != '\\') {
                    doubleQuoted = false;
                    if (keepQuotes) {
                        sb.append(ch);
                    }
                    continue;
                }
            }

            if (isQuoting) {
                sb.append(ch);
                continue;
            }

            if (ch == separator) {
                if (separating) {
                    continue;
                }
                String s = sb.toString();
                if (trim) {
                    s = s.trim();
                }
                answer.add(s);
                sb.setLength(0);
                separating = true;
                continue;
            } else if (separating) {
                separating = false;
            }

            sb.append(ch);

            if (last) {
                String s = sb.toString();
                if (trim) {
                    s = s.trim();
                }
                answer.add(s);
            }
        }

        return answer.toArray(new String[0]);
    }
}
