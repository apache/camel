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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.ChangedCharSetException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.parser.DTD;
import javax.swing.text.html.parser.Parser;
import javax.swing.text.html.parser.TagElement;

import org.apache.camel.util.component.ApiMethodParser;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.IOUtil;

/**
 * Parses ApiMethod signatures from Javadoc.
 */
@Mojo(name = "fromJavadoc", requiresDependencyResolution = ResolutionScope.TEST, requiresProject = true,
        defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class JavadocApiMethodGeneratorMojo extends AbstractApiMethodGeneratorMojo {

    static {
        // set Java AWT to headless before using Swing HTML parser
        System.setProperty("java.awt.headless", "true");
    }

    protected static final String DEFAULT_EXCLUDE_PACKAGES = "javax?\\.lang.*";
    private static final Pattern ARGTYPES_PATTERN = Pattern.compile("\\s*([^<\\s,]+\\s*(<[^>]+>)?)\\s*,?");
    private static final Pattern RAW_ARGTYPES_PATTERN = Pattern.compile("\\s*([^<\\s,]+)\\s*(<[^>]+>)?\\s*,?");

    @Parameter(property = PREFIX + "excludePackages", defaultValue = DEFAULT_EXCLUDE_PACKAGES)
    protected String excludePackages;

    @Parameter(property = PREFIX + "excludeClasses")
    protected String excludeClasses;

    @Parameter(property = PREFIX + "includeMethods")
    protected String includeMethods;

    @Parameter(property = PREFIX + "excludeMethods")
    protected String excludeMethods;

    @Parameter(property = PREFIX + "includeStaticMethods")
    protected Boolean includeStaticMethods;

    @Override
    public List<String> getSignatureList() throws MojoExecutionException {
        // signatures as map from signature with no arg names to arg names from JavadocParser
        Map<String, String> result = new HashMap<String, String>();

        final Pattern packagePatterns = Pattern.compile(excludePackages);
        final Pattern classPatterns = (excludeClasses != null) ? Pattern.compile(excludeClasses) : null;
        final Pattern includeMethodPatterns = (includeMethods != null) ? Pattern.compile(includeMethods) : null;
        final Pattern excludeMethodPatterns = (excludeMethods != null) ? Pattern.compile(excludeMethods) : null;

        // for proxy class and super classes not matching excluded packages or classes
        for (Class aClass = getProxyType();
             aClass != null && !packagePatterns.matcher(aClass.getPackage().getName()).matches()
                     && (classPatterns == null || !classPatterns.matcher(aClass.getSimpleName()).matches());
             aClass = aClass.getSuperclass()) {

            log.debug("Processing " + aClass.getName());
            final String javaDocPath = aClass.getName().replaceAll("\\.", "/") + ".html";

            // read javadoc html text for class
            InputStream inputStream = null;
            try {
                inputStream = getProjectClassLoader().getResourceAsStream(javaDocPath);
                if (inputStream == null) {
                    log.debug("JavaDoc not found on classpath for " + aClass.getName());
                    break;
                }
                // transform the HTML to get method summary as text
                // dummy DTD
                final DTD dtd = DTD.getDTD("html.dtd");
                final JavadocParser htmlParser = new JavadocParser(dtd, javaDocPath);
                htmlParser.parse(new InputStreamReader(inputStream, "UTF-8"));

                // look for parse errors
                final String parseError = htmlParser.getErrorMessage();
                if (parseError != null) {
                    throw new MojoExecutionException(parseError);
                }

                // get public method signature
                final Map<String, String> methodMap = htmlParser.getMethodText();
                for (String method : htmlParser.getMethods()) {
                    if (!result.containsKey(method)
                            && (includeMethodPatterns == null || includeMethodPatterns.matcher(method).find())
                            && (excludeMethodPatterns == null || !excludeMethodPatterns.matcher(method).find())) {

                        final int leftBracket = method.indexOf('(');
                        final String name = method.substring(0, leftBracket);
                        final String args = method.substring(leftBracket + 1, method.length() - 1);
                        String[] types;
                        if (args.isEmpty()) {
                            types = new String[0];
                        } else {
                            // get raw types from args
                            final List<String> rawTypes = new ArrayList<String>();
                            final Matcher argTypesMatcher = RAW_ARGTYPES_PATTERN.matcher(args);
                            while (argTypesMatcher.find()) {
                                rawTypes.add(argTypesMatcher.group(1));
                            }
                            types = rawTypes.toArray(new String[rawTypes.size()]);
                        }
                        final String resultType = getResultType(aClass, name, types);
                        if (resultType != null) {
                            final StringBuilder signature = new StringBuilder(resultType);
                            signature.append(" ").append(name).append(methodMap.get(method));
                            result.put(method, signature.toString());
                        }
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } finally {
                IOUtil.close(inputStream);
            }
        }

        if (result.isEmpty()) {
            throw new MojoExecutionException("No public non-static methods found, "
                    + "make sure Javadoc is available as project test dependency");
        }
        return new ArrayList<String>(result.values());
    }

    private String getResultType(Class<?> aClass, String name, String[] types) throws MojoExecutionException {
        Class<?>[] argTypes = new Class<?>[types.length];
        final ClassLoader classLoader = getProjectClassLoader();
        for (int i = 0; i < types.length; i++) {
            try {
                try {
                    argTypes[i] = ApiMethodParser.forName(types[i], classLoader);
                } catch (ClassNotFoundException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            } catch (IllegalArgumentException e) {
                throw new MojoExecutionException(e.getCause().getMessage(), e.getCause());
            }
        }

        // return null for non-public methods, and for non-static methods if includeStaticMethods is null or false
        String result = null;
        try {
            final Method method = aClass.getMethod(name, argTypes);
            int modifiers = method.getModifiers();
            if (!Modifier.isStatic(modifiers) || Boolean.TRUE.equals(includeStaticMethods)) {
                result = method.getReturnType().getCanonicalName();
            }
        } catch (NoSuchMethodException e) {
            // could be a non-public method
            try {
                aClass.getDeclaredMethod(name, argTypes);
            } catch (NoSuchMethodException e1) {
                throw new MojoExecutionException(e1.getMessage(), e1);
            }
        }

        return result;
    }

    private static class JavadocParser extends Parser {
        private static final String NON_BREAKING_SPACE = "\u00A0";
        private String hrefPattern;

        private ParserState parserState;
        private String methodWithTypes;
        private StringBuilder methodTextBuilder = new StringBuilder();

        private List<String> methods = new ArrayList<String>();
        private Map<String, String> methodText = new HashMap<String, String>();
        private String errorMessage;

        public JavadocParser(DTD dtd, String docPath) {
            super(dtd);
            this.hrefPattern = docPath + "#";
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
                        if (parserState == null && "method_summary".equals(nameAttr)) {
                            parserState = ParserState.METHOD_SUMMARY;
                        } else if (parserState == ParserState.METHOD_SUMMARY && nameAttr.startsWith("methods_inherited_from_class_")) {
                            parserState = null;
                        } else if (parserState == ParserState.METHOD && methodWithTypes == null) {
                            final Object href = attributes.getAttribute(HTML.Attribute.HREF);
                            if (href != null) {
                                String hrefAttr = (String) href;
                                if (hrefAttr.contains(hrefPattern)) {
                                    // unescape HTML
                                    methodWithTypes = unescapeHtml(hrefAttr.substring(hrefAttr.indexOf('#') + 1));
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
            return StringEscapeUtils.unescapeHtml(htmlString).replaceAll(NON_BREAKING_SPACE, " ");
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
            }
        }

        private String getArgSignature() {
            final String typeString = methodWithTypes.substring(methodWithTypes.indexOf('(') + 1, methodWithTypes.indexOf(')'));
            if (typeString.isEmpty()) {
                return "()";
            }

            // split types list
            final List<String> typeList = new ArrayList<String>();
            final Matcher typeMatcher = ARGTYPES_PATTERN.matcher(typeString);
            while (typeMatcher.find()) {
                typeList.add(typeMatcher.group(1).replaceAll(" ", ""));
            }

            // unescape HTML method text
            final String plainText = unescapeHtml(methodTextBuilder.toString());
            final String argsString = plainText.substring(plainText.indexOf('(') + 1, plainText.indexOf(')'));
            final Matcher argMatcher = ApiMethodParser.ARGS_PATTERN.matcher(argsString);
            final List<String> argNames = new ArrayList<String>();
            while (argMatcher.find()) {
                argNames.add(argMatcher.group(3));
            }

            // make sure number of types and names match
            if (typeList.size() != argNames.size()) {
                throw new IllegalArgumentException("Unexpected Javadoc error, different number of arg types and names");
            }

            final String[] names = argNames.toArray(new String[argNames.size()]);
            StringBuilder builder = new StringBuilder("(");
            int i = 0;
            for (String type : typeList) {
                // split on space or non-breaking space
                builder.append(type).append(" ").append(names[i]).append(",");
            }
            builder.deleteCharAt(builder.length() - 1);
            builder.append(")");
            return builder.toString();
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

        private String getErrorMessage() {
            return errorMessage;
        }

        private List<String> getMethods() {
            return methods;
        }

        private Map<String, String> getMethodText() {
            return methodText;
        }
    }

    private static enum ParserState {
        METHOD_SUMMARY, METHOD;
    }
}
