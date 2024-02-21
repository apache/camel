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
package org.apache.camel.dsl.jbang.core.common;

import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.Jsoner;
import org.apache.camel.util.json.Yytoken;
import org.fusesource.jansi.Ansi;

public final class JSonHelper {

    private JSonHelper() {
    }

    /**
     * Prints the JSon in pretty mode with no color
     */
    public static String prettyPrint(String json, int spaces) {
        return Jsoner.prettyPrint(json, spaces);
    }

    /**
     * Prints the JSon with ANSi color (similar to jq)
     */
    public static String colorPrint(String json, int spaces, boolean pretty) {
        return Jsoner.colorPrint(json, spaces, pretty, new Jsoner.ColorPrintElement() {
            Yytoken.Types prev;

            @Override
            public String color(Yytoken.Types type, Object value) {
                String s = value != null ? value.toString() : "null";
                switch (type) {
                    case COLON, COMMA, LEFT_SQUARE, RIGHT_SQUARE, LEFT_BRACE, RIGHT_BRACE ->
                        s = Ansi.ansi().bgDefault().bold().a(s).reset().toString();
                    case VALUE -> {
                        if (Yytoken.Types.COLON == prev) {
                            if (StringHelper.isQuoted(s)) {
                                s = Ansi.ansi().fg(Ansi.Color.GREEN).a(s).reset().toString();
                            } else {
                                s = Ansi.ansi().bgDefault().a(s).reset().toString();
                            }
                        } else {
                            s = Ansi.ansi().fgBright(Ansi.Color.BLUE).a(s).reset().toString();
                        }
                    }
                    default -> {
                    }
                }
                prev = type;
                return s;
            }
        });
    }
}
