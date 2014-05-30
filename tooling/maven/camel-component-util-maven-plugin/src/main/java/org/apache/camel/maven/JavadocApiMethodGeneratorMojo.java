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
import java.util.regex.Pattern;
import javax.swing.text.ChangedCharSetException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.parser.DTD;
import javax.swing.text.html.parser.Parser;
import javax.swing.text.html.parser.TagElement;

import org.apache.camel.util.component.ApiMethodParser;
import org.apache.camel.util.component.ArgumentSubstitutionParser;
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

    protected static final String DEFAULT_EXCLUDE_PACKAGES = "javax?\\.lang.*";

    @Parameter(property = "camel.component.util.excludePackages", defaultValue = DEFAULT_EXCLUDE_PACKAGES)
    protected String excludePackages;

    @Parameter(property = "camel.component.util.excludeClasses")
    protected String excludeClasses;

    @Parameter(property = "camel.component.util.excludeMethods")
    protected String excludeMethods;

    @Override
    protected ApiMethodParser createAdapterParser(Class proxyType) {
        return new ArgumentSubstitutionParser(proxyType, getArgumentSubstitutions());
    }

    @Override
    public List<String> getSignatureList() throws MojoExecutionException {
        // signatures as map from signature with no arg names to arg names from JavadocParser
        Map<String, String> result = new HashMap<String, String>();

        final Pattern packagePatterns = Pattern.compile(excludePackages);
        Pattern classPatterns = null;
        if (excludeClasses != null) {
            classPatterns = Pattern.compile(excludeClasses);
        }
        Pattern methodPatterns = null;
        if (excludeMethods != null) {
            methodPatterns = Pattern.compile(excludeMethods);
        }

        // for proxy class and super classes not matching excluded packages or classes
        for (Class aClass = getProxyType();
             aClass != null && !packagePatterns.matcher(aClass.getPackage().getName()).matches() &&
                     (classPatterns == null || !classPatterns.matcher(aClass.getSimpleName()).matches());
             aClass = aClass.getSuperclass()) {

            LOG.debug("Processing " + aClass.getName());
            final String javaDocPath = aClass.getName().replaceAll("\\.", "/") + ".html";

            // read javadoc html text for class
            InputStream inputStream = null;
            try {
                inputStream = getProjectClassLoader().getResourceAsStream(javaDocPath);
                if (inputStream == null) {
                    LOG.debug("JavaDoc not found on classpath for " + aClass.getName());
                    break;
                }
                // transform the HTML to get method summary as text
                // dummy DTD
                final DTD dtd = DTD.getDTD("html.dtd");
                final JavadocParser htmlParser = new JavadocParser(dtd, javaDocPath);
                htmlParser.parse(new InputStreamReader(inputStream, "UTF-8"));

                // get public method signature
                final Map<String, String> methodMap = htmlParser.getMethodText();
                for (String method : htmlParser.getMethods()) {
                    if (!result.containsKey(method) &&
                            (methodPatterns == null || !methodPatterns.matcher(method).find())) {

                        final int leftBracket = method.indexOf('(');
                        final String name = method.substring(0, leftBracket);
                        final String args = method.substring(leftBracket + 1, method.length() - 1);
                        String[] types;
                        if (args.isEmpty()) {
                            types = new String[0];
                        } else {
                            types = args.split(",");
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
            throw new MojoExecutionException("No public non-static methods found, " +
                    "make sure Javadoc is available as project test dependency");
        }
        return new ArrayList<String>(result.values());
    }

    private String getResultType(Class<?> aClass, String name, String[] types) throws MojoExecutionException {
        Class<?>[] argTypes = new Class<?>[types.length];
        final ClassLoader classLoader = getProjectClassLoader();
        for (int i = 0; i < types.length; i++) {
            try {
                try {
                    argTypes[i] = ApiMethodParser.forName(types[i].trim(), classLoader);
                } catch (ClassNotFoundException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            } catch (IllegalArgumentException e) {
                throw new MojoExecutionException(e.getCause().getMessage(), e.getCause());
            }
        }
        try {
            final Method method = aClass.getMethod(name, argTypes);
            // only include non-static public methods
            int modifiers = method.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                return method.getReturnType().getCanonicalName();
            } else {
                return null;
            }
        } catch (NoSuchMethodException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private class JavadocParser extends Parser {
        private String hrefPattern;

        private ParserState parserState;
        private String methodWithTypes;
        private StringBuilder methodTextBuilder = new StringBuilder();

        private List<String> methods = new ArrayList<String>();
        private Map<String, String> methodText = new HashMap<String, String>();

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
                                    methodWithTypes = hrefAttr.substring(hrefAttr.indexOf('#') + 1);
                                }
                            }
                        }
                    }
                } else if (parserState == ParserState.METHOD_SUMMARY && HTML.Tag.CODE.equals(htmlTag)) {
                    parserState = ParserState.METHOD;
                }
            }
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
            final String[] types = typeString.split(",");
            String argText = methodTextBuilder.toString().replaceAll("&nbsp;", " ").replaceAll("&nbsp", " ");
            final String[] args = argText.substring(argText.indexOf('(') + 1, argText.indexOf(')')).split(",");
            StringBuilder builder = new StringBuilder("(");
            for (int i = 0; i < types.length; i++) {
                final String[] arg = args[i].trim().split(" ");
                builder.append(types[i]).append(" ").append(arg[1].trim()).append(",");
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
