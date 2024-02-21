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
package org.apache.camel.util.json;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JSonerColorPrintTest {

    @Test
    public void testColor() throws Exception {
        InputStream is = new FileInputStream("src/test/resources/bean.json");
        String json = loadText(is);

        String color = Jsoner.colorPrint(json, new Jsoner.ColorPrintElement() {
            Yytoken.Types prev;

            @Override
            public String color(Yytoken.Types type, Object value) {
                String answer;
                if (Yytoken.Types.VALUE == type) {
                    if (Yytoken.Types.COLON == prev) {
                        // value
                        answer = "GREEN" + value.toString();
                    } else {
                        // value
                        answer = "BLUE" + value.toString();
                    }
                } else {
                    answer = value.toString();
                }
                prev = type;
                return answer;
            }
        });

        Assertions.assertTrue(color.contains("BLUE\"title\": GREEN\"Bean\""));
    }

    public static String loadText(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        InputStreamReader isr = new InputStreamReader(in);

        try {
            BufferedReader reader = new BufferedReader(isr);

            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    line = builder.toString();
                    return line;
                }

                builder.append(line);
                builder.append("\n");
            }
        } finally {
            isr.close();
            in.close();
        }
    }

}
