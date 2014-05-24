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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.camel.util.component.ApiMethodParser;
import org.apache.log4j.Logger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.Log4JLogChute;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

/**
 * Base Mojo class for ApiMethod generators.
 */
public abstract class ApiMethodGeneratorMojo extends AbstractMojo {

    protected static final String PREFIX = "camel.component.util.";

    // used for velocity logging, to avoid creating velocity.log
    private final Logger LOG = Logger.getLogger(this.getClass());

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/camelComponent")
    protected File outDir;

    @Parameter(defaultValue = "org.apache.camel.util.component")
    protected String outPackage;

    @Parameter(required = true, property = PREFIX + "proxyClass")
    protected String proxyClass;

    // cached fields
    private Class<?> proxyType;
    private ClassLoader projectClassLoader;
    private VelocityEngine engine;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        // initialize velocity
        initVelocityEngine();

        // load proxy class and get enumeration file to generate
        final Class proxyType = getProxyType();

        // create parser
        ApiMethodParser parser = createAdapterParser(proxyType);
        parser.setSignatures(getSignatureList());
        parser.setClassLoader(getProjectClassLoader());

        // parse signatures
        final List<ApiMethodParser.ApiMethodModel> models = parser.parse();

        // generate enumeration from model
        generateEnum(models);
    }

    protected ApiMethodParser createAdapterParser(Class proxyType) {
        return new ApiMethodParser(proxyType){};
    }

    private void initVelocityEngine() {
        // initialize velocity to load resources from class loader and use Log4J
        Properties velocityProperties = new Properties();
        velocityProperties.setProperty(RuntimeConstants.RESOURCE_LOADER, "cloader");
        velocityProperties.setProperty("cloader.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityProperties.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, Log4JLogChute.class.getName());
        velocityProperties.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM + ".log4j.logger", LOG.getName());
        engine = new VelocityEngine(velocityProperties);
        engine.init();
    }

    private void generateEnum(List<ApiMethodParser.ApiMethodModel> models) throws MojoExecutionException {
        final File apiMethodFile = getApiMethodFile();
        // ensure parent directories exist
        apiMethodFile.getParentFile().mkdirs();

        // set template parameters
        VelocityContext context = new VelocityContext();
        context.put("generatedDate", new Date().toString());
        context.put("packageName", outPackage);
        context.put("enumName", getEnumName());
        context.put("models", models);
        context.put("proxyType", getProxyType());
        context.put("helper", getClass());

        // load velocity template
        final Template template = engine.getTemplate("/api-method-enum.vm", "UTF-8");

        // generate Enumeration
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(apiMethodFile));
            template.merge(context, writer);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignore) {}
            }
        }
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

    private ClassLoader getProjectClassLoader() throws MojoExecutionException {
        if (projectClassLoader == null)  {
            final List classpathElements;
            try {
                classpathElements = project.getRuntimeClasspathElements();
            } catch (org.apache.maven.artifact.DependencyResolutionRequiredException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
            final URL[] urls = new URL[classpathElements.size()];
            int i = 0;
            for (Iterator it = classpathElements.iterator(); it.hasNext(); i++) {
                try {
                    urls[i] = new File((String) it.next()).toURI().toURL();
                } catch (MalformedURLException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
            projectClassLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
        }
        return projectClassLoader;
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

}
