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
package org.apache.camel.maven;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.text.ChangedCharSetException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.parser.DTD;
import javax.swing.text.html.parser.Parser;
import javax.swing.text.html.parser.TagElement;

import org.apache.camel.util.StringHelper;
import org.apache.commons.lang.StringEscapeUtils;

import static org.apache.camel.tooling.util.JavadocHelper.sanitizeDescription;

/**
 * Parses Javadoc HTML to get Method Signatures from Method Summary. Supports 8 and 11 Javadoc formats.
 */
public class JavadocParser extends Parser {

    private static final String NON_BREAKING_SPACE = "\u00A0";
    private static final String JAVA6_NON_BREAKING_SPACE = "&nbsp";

    private final String hrefPattern;

    private ParserState parserState;
    private String methodWithTypes;
    private StringBuilder methodTextBuilder = new StringBuilder();

    private List<String> methods = new ArrayList<>();
    private Map<String, String> methodText = new HashMap<>();
    private Map<String, Map<String, String>> parameters = new LinkedHashMap<>();
    private Map<String, String> currentParameters;
    private boolean parametersJavadoc;
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
        parameters.clear();
        currentParameters = null;
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
                final Object id = attributes.getAttribute(HTML.Attribute.ID);
                if (name != null || id != null) {
                    final String nameAttr = (String) name;
                    final String idAttr = (String) id;
                    if (parserState == ParserState.INIT
                            && ("method_summary".equals(nameAttr) || "method.summary".equals(nameAttr)
                                    || "method_summary".equals(idAttr) || "method.summary".equals(idAttr))) {
                        parserState = ParserState.METHOD_SUMMARY;
                    } else if (parserState == ParserState.INIT
                            && ("method_detail".equals(nameAttr) || "method.detail".equals(nameAttr)
                                    || "method_detail".equals(idAttr) || "method.detail".equals(idAttr))) {
                        parserState = ParserState.METHOD_DETAIL;
                    } else if (parserState == ParserState.METHOD) {
                        if (methodWithTypes == null) {

                            final String hrefAttr = (String) attributes.getAttribute(HTML.Attribute.HREF);
                            if (hrefAttr != null && (hrefAttr.contains(hrefPattern) || hrefAttr.charAt(0) == '#')) {
                                // unescape HTML
                                String methodSignature = hrefAttr.substring(hrefAttr.indexOf('#') + 1);
                                final int firstHyphen = methodSignature.indexOf('-');
                                if (firstHyphen != -1) {
                                    final int lastHyphen = methodSignature.lastIndexOf('-');
                                    methodSignature = methodSignature.substring(0, firstHyphen) + "("
                                                      + methodSignature.substring(firstHyphen + 1, lastHyphen) + ")";
                                    methodSignature = methodSignature.replace('-', ',');
                                }
                                // support varargs
                                if (methodSignature.contains("...)")) {
                                    methodSignature = methodSignature.replace("...)", "[])");
                                }
                                // map Java8 array types
                                if (methodSignature.contains(":A")) {
                                    methodSignature = methodSignature.replace(":A", "[]");
                                }
                                methodWithTypes = unescapeHtml(methodSignature);
                            }
                        } else {
                            final String title = (String) attributes.getAttribute(HTML.Attribute.TITLE);
                            if (title != null) {
                                // append package name to type name text
                                methodTextBuilder.append(title, title.lastIndexOf(' '), title.length()).append('.');
                            }
                        }
                    }
                }
            } else if (parserState == ParserState.METHOD_SUMMARY && HTML.Tag.CODE.equals(htmlTag)) {
                parserState = ParserState.METHOD;
            } else if (parserState == ParserState.METHOD_DETAIL && HTML.Tag.H4.equals(htmlTag)) {
                parserState = ParserState.METHOD_DETAIL_METHOD;
            } else if (parserState == ParserState.METHOD_DETAIL && HTML.Tag.SPAN.equals(htmlTag)) {
                Object clazz = getAttributes().getAttribute(HTML.Attribute.CLASS);
                if ("paramLabel".equals(clazz)) {
                    parserState = ParserState.METHOD_DETAIL_PARAM;
                }
            } else if (parserState == ParserState.METHOD_DETAIL_PARAM) {
                if (HTML.Tag.CODE.equals(htmlTag) || HTML.Tag.DD.equals(htmlTag) || HTML.Tag.DL.equals(htmlTag)
                        || HTML.Tag.DT.equals(htmlTag) || HTML.Tag.UL.equals(htmlTag)) {

                    // okay so we need to grab javadoc for each parameter from the method signature
                    // these parameters are documented elsewhere in the html reports, so we need
                    // to find the span class where they start and then keep reading tags until there are no more parameters
                    // unfortunately the end tag is not consistent whether there are 1 or more parameters
                    // and therefore we need a bit of hacky code

                    String text = methodTextBuilder.toString().trim();
                    if (!text.isEmpty() && parametersJavadoc || (text.length() > 11 && text.startsWith("Parameters:"))) {
                        parametersJavadoc = true;
                        if (text.startsWith("Parameters:")) {
                            text = text.substring(11);
                        }
                        String key = StringHelper.before(text, " ");
                        String desc = StringHelper.after(text, " ");
                        if (key != null) {
                            key = key.trim();
                        }
                        if (desc != null) {
                            // remove leading - and whitespaces
                            while (desc.startsWith("-")) {
                                desc = desc.substring(1);
                                desc = desc.trim();
                            }
                            desc = sanitizeDescription(desc, false);
                            if (desc != null && !desc.isEmpty()) {
                                // upper case first letter
                                char ch = desc.charAt(0);
                                if (Character.isAlphabetic(ch) && !Character.isUpperCase(ch)) {
                                    desc = Character.toUpperCase(ch) + desc.substring(1);
                                }
                                // remove ending dot if there is the text is just alpha or whitespace
                                boolean removeDot = true;
                                char[] arr = desc.toCharArray();
                                for (int i = 0; i < arr.length; i++) {
                                    ch = arr[i];
                                    boolean accept = Character.isAlphabetic(ch) || Character.isWhitespace(ch) || ch == '\''
                                            || ch == '-' || ch == '_';
                                    boolean last = i == arr.length - 1;
                                    accept |= last && ch == '.';
                                    if (!accept) {
                                        removeDot = false;
                                        break;
                                    }
                                }
                                if (removeDot && desc.endsWith(".")) {
                                    desc = desc.substring(0, desc.length() - 1);
                                }
                                desc = desc.trim();
                            }
                        }
                        if (key != null && desc != null && currentParameters != null) {
                            currentParameters.put(key, desc);
                        }
                        methodTextBuilder.delete(0, methodTextBuilder.length());
                        if (!HTML.Tag.DD.equals(htmlTag) && !HTML.Tag.CODE.equals(htmlTag)) {
                            parserState = ParserState.METHOD_DETAIL;
                            parametersJavadoc = false;
                        }
                    }
                }
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
        } else if (parserState == ParserState.METHOD_DETAIL_METHOD && HTML.Tag.H4.equals(tag.getHTMLTag())) {
            final Object end = getAttributes().getAttribute(HTML.Attribute.ENDTAG);
            if ("true".equals(end)) {
                String methodName = methodTextBuilder.toString();
                parameters.putIfAbsent(methodName, new HashMap<>());
                currentParameters = parameters.get(methodName);
                methodTextBuilder.delete(0, methodTextBuilder.length());
                parserState = ParserState.METHOD_DETAIL;
            }
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
            plainText = plainText.replace("...", "[]");
        }
        return plainText.substring(plainText.indexOf('('), plainText.indexOf(')') + 1);
    }

    @Override
    protected void handleText(char[] text) {
        if (parserState == ParserState.METHOD && methodWithTypes != null) {
            methodTextBuilder.append(text);
        } else if (parserState == ParserState.METHOD_DETAIL_METHOD) {
            methodTextBuilder.append(text);
        } else if (parserState == ParserState.METHOD_DETAIL_PARAM) {
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

    public Map<String, Map<String, String>> getParameters() {
        return parameters;
    }

    private enum ParserState {
        INIT,
        METHOD_SUMMARY,
        METHOD,
        METHOD_DETAIL,
        METHOD_DETAIL_METHOD,
        METHOD_DETAIL_PARAM
    }
}
