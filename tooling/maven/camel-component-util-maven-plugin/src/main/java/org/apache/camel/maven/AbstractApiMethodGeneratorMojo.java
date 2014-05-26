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

import java.io.File;
import java.util.List;

import org.apache.camel.util.component.ApiMethodParser;
import org.apache.camel.util.component.ArgumentSubstitutionParser;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.velocity.VelocityContext;

/**
 * Base Mojo class for ApiMethod generators.
 */
public abstract class AbstractApiMethodGeneratorMojo extends AbstractGeneratorMojo {

    @Parameter(required = true, property = PREFIX + "proxyClass")
    protected String proxyClass;

    @Parameter(property = PREFIX + "substitutions")
    protected Substitution[] substitutions = new Substitution[0];

    // cached fields
    private Class<?> proxyType;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        // load proxy class and get enumeration file to generate
        final Class proxyType = getProxyType();

        // create parser
        ApiMethodParser parser = createAdapterParser(proxyType);
        parser.setSignatures(getSignatureList());
        parser.setClassLoader(getProjectClassLoader());

        // parse signatures
        final List<ApiMethodParser.ApiMethodModel> models = parser.parse();

        // generate enumeration from model
        mergeTemplate(getApiMethodContext(models), getApiMethodFile(), "/api-method-enum.vm");
    }

    protected ApiMethodParser createAdapterParser(Class proxyType) {
        return new ArgumentSubstitutionParser(proxyType, getArgumentSubstitutions()){};
    }

    private VelocityContext getApiMethodContext(List<ApiMethodParser.ApiMethodModel> models) throws MojoExecutionException {
        VelocityContext context = new VelocityContext();
        context.put("packageName", outPackage);
        context.put("enumName", getEnumName());
        context.put("models", models);
        context.put("proxyType", getProxyType());
        context.put("helper", getClass());
        return context;
    }

    public abstract List<String> getSignatureList() throws MojoExecutionException;

    public Class getProxyType() throws MojoExecutionException {
        if (proxyType == null) {
            // load proxy class from Project runtime dependencies
            try {
                proxyType = getProjectClassLoader().loadClass(proxyClass);
            } catch (ClassNotFoundException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
        return proxyType;
    }

    public File getApiMethodFile() throws MojoExecutionException {
        final StringBuilder fileName = new StringBuilder();
        fileName.append(outPackage.replaceAll("\\.", File.separator)).append(File.separator);
        fileName.append(getEnumName()).append(".java");
        return new File(outDir, fileName.toString());
    }

    private String getEnumName() throws MojoExecutionException {
        return getProxyType().getSimpleName() + "ApiMethod";
    }

    public static String getType(Class<?> clazz) {
        if (clazz.isArray()) {
            // create a zero length array and get the class from the instance
            return "new " + clazz.getCanonicalName().replaceAll("\\[\\]", "[0]") + ".getClass()";
        } else {
            return clazz.getCanonicalName() + ".class";
        }
    }

    public ArgumentSubstitutionParser.Substitution[] getArgumentSubstitutions() {
        ArgumentSubstitutionParser.Substitution[] subs = new ArgumentSubstitutionParser.Substitution[substitutions.length];
        for (int i = 0; i < substitutions.length; i++) {
            subs[i] = new ArgumentSubstitutionParser.Substitution(substitutions[i].getMethod(),
                    substitutions[i].getArgName(), substitutions[i].getArgType(), substitutions[i].getReplacement());
        }
        return subs;
    }
}
