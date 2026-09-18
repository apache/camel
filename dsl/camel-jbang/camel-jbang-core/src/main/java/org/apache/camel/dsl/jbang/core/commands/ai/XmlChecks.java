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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The XSLT and XML checks of {@link SourceValidator}: the stylesheet compiles (Saxon when present, else the JDK), the
 * XML parses, prose after the root element.
 */
final class XmlChecks {

    private XmlChecks() {
    }

    /**
     * Compiles the stylesheet the way the xslt components do: with Saxon when it is on the classpath (preferred, XSLT
     * 3.0), otherwise with the JDK processor (XSLT 1.0). A stylesheet that declares version 2.0 or 3.0 when only the
     * JDK processor is available is checked for well-formed XML only, since it is meant for the xslt-saxon component.
     */
    public static List<String> validateXslt(String content) {
        List<String> msgs = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return msgs;
        }
        javax.xml.transform.TransformerFactory factory = saxonFactory();
        boolean saxon = factory != null;
        Matcher vm = XSLT_VERSION_PATTERN.matcher(content);
        String version = vm.find() ? vm.group(1) : "1.0";
        if (!saxon && !version.startsWith("1")) {
            return validateXml(content);
        }
        if (factory == null) {
            factory = javax.xml.transform.TransformerFactory.newInstance();
        }
        // compile only: no external DTD or imported stylesheet is fetched (the parse path disallows DTDs the same way)
        for (String attribute : new String[] {
                javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET }) {
            try {
                factory.setAttribute(attribute, "");
            } catch (IllegalArgumentException e) {
                // a factory that does not know the attribute
            }
        }
        List<String> collected = new ArrayList<>();
        javax.xml.transform.ErrorListener listener = new javax.xml.transform.ErrorListener() {
            @Override
            public void warning(javax.xml.transform.TransformerException e) {
            }

            @Override
            public void error(javax.xml.transform.TransformerException e) {
                collected.add(e.getMessageAndLocation());
            }

            @Override
            public void fatalError(javax.xml.transform.TransformerException e) {
                collected.add(e.getMessageAndLocation());
            }
        };
        try {
            factory.setErrorListener(listener);
            factory.newTemplates(new javax.xml.transform.stream.StreamSource(new java.io.StringReader(content)));
        } catch (Exception e) {
            if (collected.isEmpty()) {
                collected.add(e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }
        for (String c : new java.util.LinkedHashSet<>(collected)) {
            String text = c.replace("\n", " ").trim();
            if (text.contains("Content is not allowed in prolog")) {
                // text, a blank line or a markdown fence before <?xml or <xsl:stylesheet
                String first = content.stripLeading().isEmpty() ? "" : content.stripLeading().split("\n", 2)[0].trim();
                text += " (the stylesheet must start with <?xml ...?> or <xsl:stylesheet; the file starts with \""
                        + (first.length() > 40 ? first.substring(0, 40) + "..." : first)
                        + "\"; remove everything before it, including blank lines)";
            }
            if (text.contains("Content is not allowed in trailing section")) {
                // an explanation appended after </xsl:stylesheet>
                String after = afterRootElement(content);
                text += " (the file must end with </xsl:stylesheet>; it continues with \""
                        + (after.length() > 40 ? after.substring(0, 40) + "..." : after)
                        + "\": put explanations in an <!-- XML comment --> or leave them out)";
            }
            msgs.add("XSLT: " + text);
        }
        boolean prolog = msgs.stream().anyMatch(m -> m.contains("not allowed in prolog")
                || m.contains("not allowed in trailing section"));
        if (!msgs.isEmpty() && !saxon && !prolog) {
            msgs.set(0, msgs.get(0) + " (compiled with the JDK processor, XSLT 1.0; for XSLT 2.0 or 3.0 functions such as"
                        + " current-dateTime() declare version=\"2.0\" and use the xslt-saxon component)");
        }
        if (prolog) {
            // the generic "Could not compile stylesheet" line adds nothing next to the prolog message
            msgs.removeIf(m -> m.equals("XSLT: Could not compile stylesheet"));
        }
        return msgs;
    }

    /** The first non-blank line after the closing tag of the root element (empty when there is none). */
    static String afterRootElement(String content) {
        Matcher m = Pattern.compile("</[A-Za-z_:][A-Za-z0-9_:.-]*\\s*>\\s*$", Pattern.MULTILINE).matcher(content);
        int end = -1;
        while (m.find()) {
            end = m.end();
        }
        if (end < 0) {
            return "";
        }
        // the last closing tag is the root only when the rest of the file is not XML; take the first text line after
        // the closing root tag: the closing tag whose remainder contains no further tag
        Matcher all = Pattern.compile("</[A-Za-z_:][A-Za-z0-9_:.-]*\\s*>").matcher(content);
        while (all.find()) {
            String rest = content.substring(all.end());
            if (!rest.contains("<") || rest.stripLeading().startsWith("<!--") && !rest.contains("</")) {
                for (String line : rest.split("\n")) {
                    if (!line.isBlank()) {
                        return line.trim();
                    }
                }
                return "";
            }
        }
        return "";
    }

    static final Pattern XSLT_VERSION_PATTERN
            = Pattern.compile("<xsl:(?:stylesheet|transform)[^>]*\\sversion\\s*=\\s*[\"']([0-9.]+)[\"']");

    /** Saxon's factory when camel-xslt-saxon (or Saxon itself) is on the classpath, else null. */
    static javax.xml.transform.TransformerFactory saxonFactory() {
        try {
            Class<?> type = Class.forName("net.sf.saxon.TransformerFactoryImpl");
            return (javax.xml.transform.TransformerFactory) type.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            return null;
        }
    }

    /** Checks that the XML is well formed (an input file, a Camel XML DSL file or any other XML). */
    public static List<String> validateXml(String content) {
        List<String> msgs = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return msgs;
        }
        try {
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(null);
            db.parse(new org.xml.sax.InputSource(new java.io.StringReader(content)));
        } catch (org.xml.sax.SAXParseException e) {
            String hint = "";
            if (e.getMessage() != null && e.getMessage().contains("not allowed in trailing section")) {
                String after = afterRootElement(content);
                hint = " (the file must end with the closing tag of its root element; it continues with \""
                       + (after.length() > 40 ? after.substring(0, 40) + "..." : after)
                       + "\": put explanations in an <!-- XML comment --> or leave them out)";
            } else if (e.getMessage() != null && e.getMessage().contains("not allowed in prolog")) {
                String first = content.stripLeading().isEmpty() ? "" : content.stripLeading().split("\n", 2)[0].trim();
                hint = " (the file must start with <?xml ...?> or its root element; it starts with \""
                       + (first.length() > 40 ? first.substring(0, 40) + "..." : first) + "\")";
            }
            msgs.add("Line " + e.getLineNumber() + ": XML is not well formed: " + e.getMessage() + hint);
        } catch (Exception e) {
            msgs.add("XML is not well formed: " + e.getMessage());
        }
        return msgs;
    }

}
