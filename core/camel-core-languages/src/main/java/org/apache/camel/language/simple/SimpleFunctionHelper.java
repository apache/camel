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

import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;

public final class SimpleFunctionHelper {

    private SimpleFunctionHelper() {
    }

    public static String ifStartsWithReturnRemainder(String prefix, String text) {
        if (text.startsWith(prefix)) {
            String remainder = text.substring(prefix.length());
            if (!remainder.isEmpty()) {
                return remainder;
            }
        }
        return null;
    }

    public static List<String> splitOgnl(String remainder) {
        List<String> methods = OgnlHelper.splitOgnl(remainder);
        List<String> answer = new ArrayList<>();
        for (String m : methods) {
            if (m.startsWith(".")) {
                m = m.substring(1);
            }
            boolean index = m.startsWith("[") && m.endsWith("]");
            if (index) {
                String last = answer.isEmpty() ? null : answer.get(answer.size() - 1);
                boolean lastIndex = last != null && last.startsWith("[") && last.endsWith("]");
                if (lastIndex) {
                    answer.set(answer.size() - 1, last + m);
                } else {
                    answer.add(m);
                }
            } else {
                answer.add(m);
            }
        }
        return answer;
    }

    public static String ognlCodeMethods(String remainder, String type) {
        StringBuilder sb = new StringBuilder(256);

        if (remainder != null) {
            List<String> methods = splitOgnl(remainder);
            for (String m : methods) {
                if (m.startsWith("(")) {
                    sb.append(m);
                    continue;
                }

                String index = StringHelper.betweenOuterPair(m, '[', ']');
                if (index != null) {
                    m = StringHelper.before(m, "[");
                }

                if (m != null && m.equals("length")) {
                    if (type != null && type.contains("[]")) {
                        sb.append(".length");
                        continue;
                    }
                }

                if (m != null) {
                    m = OgnlHelper.methodAsDoubleQuotes(m);
                }

                if (m != null && !m.isEmpty()) {
                    sb.append(".");
                    char ch = m.charAt(m.length() - 1);
                    if (Character.isAlphabetic(ch)) {
                        if (!m.startsWith("get")) {
                            sb.append("get");
                            sb.append(Character.toUpperCase(m.charAt(0)));
                            sb.append(m.substring(1));
                        } else {
                            sb.append(m);
                        }
                        sb.append("()");
                    } else {
                        sb.append(m);
                    }
                }

                if (index != null) {
                    sb.append(".get(");
                    try {
                        long lon = Long.parseLong(index);
                        sb.append(lon);
                        if (lon > Integer.MAX_VALUE) {
                            sb.append("l");
                        }
                    } catch (Exception e) {
                        index = StringHelper.removeLeadingAndEndingQuotes(index);
                        sb.append("\"");
                        sb.append(index);
                        sb.append("\"");
                    }
                    sb.append(")");
                }
            }
        }

        if (!sb.isEmpty()) {
            return sb.toString();
        } else {
            return remainder;
        }
    }

    public static String appendClass(String type) {
        type = StringHelper.removeQuotes(type);
        if (!type.endsWith(".class")) {
            type = type + ".class";
        }
        return type;
    }

    public static String parseInHeader(String function) {
        String remainder;
        remainder = ifStartsWithReturnRemainder("in.headers", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("in.header", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("headers", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("header", function);
        }
        return remainder;
    }

    public static String parseVariable(String function) {
        String remainder = ifStartsWithReturnRemainder("variables", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("variable", function);
        }
        return remainder;
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
