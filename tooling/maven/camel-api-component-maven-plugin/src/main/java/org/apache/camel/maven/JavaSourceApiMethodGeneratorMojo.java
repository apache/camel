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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Parses ApiMethod signatures from source.
 */
@Mojo(name = "fromSource", requiresDependencyResolution = ResolutionScope.TEST, requiresProject = true,
      defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class JavaSourceApiMethodGeneratorMojo extends AbstractApiMethodGeneratorMojo {

    static {
        // set Java AWT to headless before using Swing HTML parser
        System.setProperty("java.awt.headless", "true");
    }

    protected static final String DEFAULT_EXCLUDE_PACKAGES = "javax?\\.lang.*";

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
    public List<SignatureModel> getSignatureList() throws MojoExecutionException {
        // signatures as map from signature with no arg names to arg names from JavadocParser
        Map<String, SignatureModel> result = new LinkedHashMap<>();

        final Pattern packagePatterns = Pattern.compile(excludePackages);
        final Pattern classPatterns = (excludeClasses != null) ? Pattern.compile(excludeClasses) : null;
        final Pattern includeMethodPatterns = (includeMethods != null) ? Pattern.compile(includeMethods) : null;
        final Pattern excludeMethodPatterns = (excludeMethods != null) ? Pattern.compile(excludeMethods) : null;

        // for proxy class and super classes not matching excluded packages or classes
        for (Class<?> aClass = getProxyType();
             aClass != null && !packagePatterns.matcher(aClass.getPackage().getName()).matches()
                     && (classPatterns == null || !classPatterns.matcher(aClass.getSimpleName()).matches());
             aClass = aClass.getSuperclass()) {

            log.debug("Processing {}", aClass.getName());
            String sourcePath = aClass.getName();
            String nestedClass = null;
            int pos = sourcePath.indexOf('$');
            if (pos != -1) {
                nestedClass = sourcePath.substring(pos + 1);
                sourcePath = sourcePath.substring(0, pos);
            }
            sourcePath = sourcePath.replace('.', '/') + ".java";

            // read source java text for class
            log.debug("Loading source: {}", sourcePath);
            JavaSourceParser parser;
            try (InputStream inputStream = getProjectClassLoader().getResourceAsStream(sourcePath)) {
                if (inputStream == null) {
                    log.debug("Java source not found on classpath for {}", aClass.getName());
                    break;
                }

                parser = new JavaSourceParser();
                parser.parse(inputStream, nestedClass);
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            // look for parse errors
            final String parseError = parser.getErrorMessage();
            if (parseError != null) {
                throw new MojoExecutionException(parseError);
            }

            // get public method signature
            for (String method : parser.getMethodSignatures()) {
                if (!result.containsKey(method)
                        && (includeMethodPatterns == null || includeMethodPatterns.matcher(method).find())
                        && (excludeMethodPatterns == null || !excludeMethodPatterns.matcher(method).find())) {

                    String signature = method;
                    method = method.replace("public ", "");
                    int whitespace = method.indexOf(' ');
                    int leftBracket = method.indexOf('(');
                    String name = method.substring(whitespace + 1, leftBracket);

                    SignatureModel model = new SignatureModel();
                    model.setSignature(method);
                    model.setApiDescription(parser.getClassDoc());
                    model.setMethodDescription(parser.getMethodDocs().get(name));
                    model.setParameterDescriptions(parser.getParameterDocs().get(name));
                    model.setParameterTypes(parser.getParameterTypes().get(signature));

                    result.put(method, model);
                }
            }

        }

        if (result.isEmpty()) {
            throw new MojoExecutionException(
                    "No public non-static methods found, "
                                             + "make sure source JAR is available as project scoped=provided and optional=true dependency");
        }
        return new ArrayList<>(result.values());
    }

}
