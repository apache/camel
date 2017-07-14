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
package org.apache.camel.maven;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.text.ChangedCharSetException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.parser.DTD;
import javax.swing.text.html.parser.Parser;
import javax.swing.text.html.parser.TagElement;

import org.apache.commons.lang.StringEscapeUtils;

/**
 * Parses Javadoc HTML to get Method Signatures from Method Sumary. Supports Java 6, 7 and 8 Javadoc formats.
 */
public class JavadocParser extends Parser {

    private static final String NON_BREAKING_SPACE = "\u00A0";
    private static final String JAVA6_NON_BREAKING_SPACE = "&nbsp";

    private final String hrefPattern;

    private ParserState parserState;
    private String methodWithTypes;
    private StringBuilder methodTextBuilder = new StringBuilder();

    private List<String> methods = new ArrayList<String>();
    private Map<String, String> methodText = new HashMap<String, String>();
    private String errorMessage;

    public JavadocParser(DTD dtd, String docPath) {
        super(dtd);
        this.hrefPattern = docPath + "#";
        parserState = ParserState.INIT;
    }

    public void reset() {
        parserState = ParserState.INIT;

        methodWithTypes = null;
        methodTextBuilder = new StringBuilder();

        methods.clear();
        methodText.clear();

        errorMessage = null;
    }

    @Override
    protected void startTag(TagElement tag) throws ChangedCharSetException {
        super.startTag(tag);

        final HTML.Tag htmlTag = tag.getHTMLTag();
        if (htmlTag != null) {
            if (HTML.Tag.A.equals(htmlTag)) {
                final SimpleAttributeSet attributes = getAttributes();
                final Object name = attributes.getAttribute(HTML.Attribute.NAME);
                if (name != null) {
                    final String nameAttr = (String) name;
                    if (parserState == ParserState.INIT
                        && ("method_summary".equals(nameAttr) || "method.summary".equals(nameAttr))) {
                        parserState = ParserState.METHOD_SUMMARY;
                    } else if (parserState == ParserState.METHOD) {
                        if (methodWithTypes == null) {

                            final String hrefAttr = (String) attributes.getAttribute(HTML.Attribute.HREF);
                            if (hrefAttr != null && hrefAttr.contains(hrefPattern)) {
                                // unescape HTML
                                String methodSignature = hrefAttr.substring(hrefAttr.indexOf('#') + 1);
                                final int firstHyphen = methodSignature.indexOf('-');
                                if (firstHyphen != -1) {
                                    final int lastHyphen = methodSignature.lastIndexOf('-');
                                    methodSignature = methodSignature.substring(0, firstHyphen) + "("
                                        + methodSignature.substring(firstHyphen + 1, lastHyphen) + ")";
                                    methodSignature = methodSignature.replaceAll("-", ",");
                                }
                                // support varargs
                                if (methodSignature.contains("...)")) {
                                    methodSignature = methodSignature.replaceAll("\\.\\.\\.\\)", "[])");
                                }
                                // map Java8 array types
                                if (methodSignature.contains(":A")) {
                                    methodSignature = methodSignature.replaceAll(":A", "[]");
                                }
                                methodWithTypes = unescapeHtml(methodSignature);
                            }
                        } else {
                            final String title = (String) attributes.getAttribute(HTML.Attribute.TITLE);
                            if (title != null) {
                                // append package name to type name text
                                methodTextBuilder.append(title.substring(title.lastIndexOf(' '))).append('.');
                            }
                        }
                    }
                }
            } else if (parserState == ParserState.METHOD_SUMMARY && HTML.Tag.CODE.equals(htmlTag)) {
                parserState = ParserState.METHOD;
            }
        }
    }

    private static String unescapeHtml(String htmlString) {
        String result = StringEscapeUtils.unescapeHtml(htmlString).replaceAll(NON_BREAKING_SPACE, " ")
            .replaceAll(JAVA6_NON_BREAKING_SPACE, " ");
        try {
            result = URLDecoder.decode(result, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {

        }
        return result;
    }

    @Override
    protected void handleEmptyTag(TagElement tag) {
        if (parserState == ParserState.METHOD && HTML.Tag.CODE.equals(tag.getHTMLTag())) {
            if (methodWithTypes != null) {
                // process collected method data
                methods.add(methodWithTypes);
                this.methodText.put(methodWithTypes, getArgSignature());

                // clear the text builder for next method
                methodTextBuilder.delete(0, methodTextBuilder.length());
                methodWithTypes = null;
            }

            parserState = ParserState.METHOD_SUMMARY;
        } else if (parserState == ParserState.METHOD_SUMMARY
            && !methods.isEmpty()
            && HTML.Tag.TABLE.equals(tag.getHTMLTag())) {
            // end of method summary table
            parserState = ParserState.INIT;
        }
    }

    private String getArgSignature() {
        final String typeString = methodWithTypes.substring(methodWithTypes.indexOf('(') + 1, methodWithTypes.indexOf(')'));
        if (typeString.isEmpty()) {
            return "()";
        }

        // unescape HTML method text
        String plainText = unescapeHtml(methodTextBuilder.toString());
        // support varargs
        if (plainText.contains("...")) {
            plainText = plainText.replaceAll("\\.\\.\\.", "[]");
        }
        return plainText.substring(plainText.indexOf('('), plainText.indexOf(')') + 1);
    }

    @Override
    protected void handleText(char[] text) {
        if (parserState == ParserState.METHOD && methodWithTypes != null) {
            methodTextBuilder.append(text);
        }
    }

    @Override
    protected void handleError(int ln, String msg) {
        if (msg.startsWith("exception ")) {
            this.errorMessage = "Exception parsing Javadoc line " + ln + ": " + msg;
        }
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<String> getMethods() {
        return methods;
    }

    public Map<String, String> getMethodText() {
        return methodText;
    }

    private enum ParserState {
        INIT, METHOD_SUMMARY, METHOD
    }
}
