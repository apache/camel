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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.camel.dsl.jbang.core.common.ExampleHelper;

final class DocHelper {

    private DocHelper() {
    }

    static String loadResourceContent(String resourcePath) {
        try (InputStream is = ExampleHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    static String downloadContent(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            try (InputStream is = conn.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    static String asciidocToMarkdown(String adoc) {
        StringBuilder sb = new StringBuilder();
        String[] lines = adoc.split("\n", -1);
        String pendingLang = null;
        boolean inCodeBlock = false;
        for (String line : lines) {
            if (!inCodeBlock && line.startsWith("[source")) {
                int comma = line.indexOf(',');
                int end = line.indexOf(']');
                if (comma >= 0 && end > comma) {
                    pendingLang = line.substring(comma + 1, end).trim();
                } else {
                    pendingLang = "";
                }
                continue;
            }
            if (line.equals("----")) {
                if (inCodeBlock) {
                    sb.append("```\n");
                    inCodeBlock = false;
                } else {
                    sb.append("```").append(pendingLang != null ? pendingLang : "").append('\n');
                    pendingLang = null;
                    inCodeBlock = true;
                }
                continue;
            }
            if (inCodeBlock) {
                sb.append(line).append('\n');
                continue;
            }
            pendingLang = null;
            if (line.startsWith("include::")) {
                continue;
            }
            if (line.startsWith("=")) {
                if (line.startsWith("==== ")) {
                    sb.append("#### ").append(line.substring(5)).append('\n');
                } else if (line.startsWith("=== ")) {
                    sb.append("### ").append(line.substring(4)).append('\n');
                } else if (line.startsWith("== ")) {
                    sb.append("## ").append(line.substring(3)).append('\n');
                } else if (line.startsWith("= ")) {
                    sb.append("# ").append(line.substring(2)).append('\n');
                } else {
                    sb.append(line).append('\n');
                }
                continue;
            }
            String converted = line;
            converted = convertImages(converted);
            converted = convertLinks(converted);
            sb.append(converted).append('\n');
        }
        if (inCodeBlock) {
            sb.append("```\n");
        }
        return sb.toString();
    }

    private static String convertImages(String line) {
        int idx = 0;
        StringBuilder sb = new StringBuilder();
        while (idx < line.length()) {
            int imgStart = line.indexOf("image::", idx);
            if (imgStart < 0) {
                sb.append(line, idx, line.length());
                break;
            }
            sb.append(line, idx, imgStart);
            int bracketOpen = line.indexOf('[', imgStart);
            int bracketClose = bracketOpen >= 0 ? line.indexOf(']', bracketOpen) : -1;
            if (bracketOpen >= 0 && bracketClose >= 0) {
                String file = line.substring(imgStart + 7, bracketOpen);
                String alt = line.substring(bracketOpen + 1, bracketClose);
                sb.append("![").append(alt).append("](").append(file).append(')');
                idx = bracketClose + 1;
            } else {
                sb.append("image::");
                idx = imgStart + 7;
            }
        }
        return sb.toString();
    }

    private static String convertLinks(String line) {
        int idx = 0;
        StringBuilder sb = new StringBuilder();
        while (idx < line.length()) {
            int linkStart = line.indexOf("link:", idx);
            if (linkStart < 0) {
                sb.append(line, idx, line.length());
                break;
            }
            sb.append(line, idx, linkStart);
            int bracketOpen = line.indexOf('[', linkStart);
            int bracketClose = bracketOpen >= 0 ? line.indexOf(']', bracketOpen) : -1;
            if (bracketOpen >= 0 && bracketClose >= 0) {
                String url = line.substring(linkStart + 5, bracketOpen);
                String text = line.substring(bracketOpen + 1, bracketClose);
                sb.append('[').append(text).append("](").append(url).append(')');
                idx = bracketClose + 1;
            } else {
                sb.append("link:");
                idx = linkStart + 5;
            }
        }
        return sb.toString();
    }
}
