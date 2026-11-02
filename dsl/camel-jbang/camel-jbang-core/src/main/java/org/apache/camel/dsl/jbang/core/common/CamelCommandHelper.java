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

import org.apache.camel.dsl.jbang.run.RunPipeline;
import org.apache.camel.dsl.jbang.run.common.XmlHelper;
import org.apache.camel.util.json.Jsoner;
import org.apache.camel.util.xml.pretty.XmlPrettyPrinter;
import org.jline.jansi.Ansi;

public final class CamelCommandHelper {
    public static final String CAMEL_INSTANCE_TYPE = RunPipeline.CAMEL_INSTANCE_TYPE;

    private CamelCommandHelper() {
    }

    public static String extractState(int status) {
        if (status <= 4) {
            return "Starting";
        } else if (status == 5) {
            return "Running";
        } else if (status == 6) {
            return "Suspending";
        } else if (status == 7) {
            return "Suspended";
        } else if (status == 8) {
            return "Terminating";
        } else if (status == 9) {
            return "Terminated";
        } else {
            return "Terminated";
        }
    }

    public static String valueAsStringPretty(Object value, boolean loggingColor) {
        if (value == null) {
            return "null";
        }
        boolean json = false;
        String s = value.toString();
        if (!s.isEmpty()) {
            try {
                s = Jsoner.unescape(s);
                if (loggingColor) {
                    s = JSonHelper.colorPrint(s, 2, true);
                } else {
                    s = JSonHelper.prettyPrint(s, 2);
                }
                if (s != null && !s.isEmpty()) {
                    json = true;
                }
            } catch (Exception e) {
                // ignore as not json
            }
            if (s == null || s.isEmpty()) {
                s = value.toString();
            }
            if (!json) {
                // try with xml
                try {
                    s = Jsoner.unescape(s);
                    if (loggingColor) {
                        s = xmlColorPrint(s, 2, true);
                    } else {
                        s = XmlHelper.prettyPrint(s, 2);
                    }
                } catch (Exception e) {
                    // ignore as not xml
                }
            }
            if (s == null || s.isEmpty()) {
                s = value.toString();
            }
        }
        if (s == null) {
            return "null";
        }
        return s;
    }

    /**
     * Prints the XML with ANSi color (similar to jq)
     */
    private static String xmlColorPrint(String xml, int spaces, boolean pretty) throws Exception {
        return XmlPrettyPrinter.colorPrint(xml, spaces, pretty, new XmlPrettyPrinter.ColorPrintElement() {
            @Override
            public String color(int type, String value) {
                String s = value != null ? value : "null";
                if (type == XmlPrettyPrinter.ColorPrintElement.DECLARATION) {
                    s = Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(s).reset().toString();
                } else if (type == XmlPrettyPrinter.ColorPrintElement.ELEMENT) {
                    s = Ansi.ansi().fgBright(Ansi.Color.BLUE).a(s).reset().toString();
                } else if (type == XmlPrettyPrinter.ColorPrintElement.VALUE) {
                    s = Ansi.ansi().fgDefault().a(s).reset().toString();
                } else if (type == XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_KEY) {
                    s = Ansi.ansi().fg(Ansi.Color.MAGENTA).a(s).reset().toString();
                } else if (type == XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_VALUE
                        || type == XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_QUOTE) {
                    s = Ansi.ansi().fg(Ansi.Color.GREEN).a(s).reset().toString();
                }
                return s;
            }
        });
    }
}
