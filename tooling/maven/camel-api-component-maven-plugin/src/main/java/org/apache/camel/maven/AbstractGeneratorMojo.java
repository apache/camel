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

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Date;
import java.util.Properties;

import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.util.function.ThrowingHelper;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.codehaus.plexus.build.BuildContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for API based generation MOJOs.
 */
public abstract class AbstractGeneratorMojo extends AbstractMojo {

    protected static final String PREFIX = "org.apache.camel.";
    protected static final String OUT_PACKAGE = PREFIX + "component.internal";
    protected static final String COMPONENT_PACKAGE = PREFIX + "component";

    // used for velocity logging, to avoid creating velocity.log
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    @Parameter(defaultValue = OUT_PACKAGE)
    protected String outPackage;

    @Parameter(required = true, property = PREFIX + "scheme")
    protected String scheme;

    @Parameter(required = true, property = PREFIX + "componentName")
    protected String componentName;

    @Parameter(required = true, defaultValue = COMPONENT_PACKAGE)
    protected String componentPackage;

    @Parameter(required = true, defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    private ClassLoader projectClassLoader;

    // Thread-safe deferred-construction singleton via nested static class
    private static class VelocityEngineHolder {
        private static final VelocityEngine ENGINE;
        static {
            // initialize velocity to load resources from class loader and use Log4J
            Properties velocityProperties = new Properties();
            velocityProperties.setProperty(RuntimeConstants.RESOURCE_LOADERS, "cloader");
            velocityProperties.setProperty("resource.loader.cloader.class", ClasspathResourceLoader.class.getName());
            Logger velocityLogger = LoggerFactory.getLogger("org.apache.camel.maven.Velocity");
            velocityProperties.setProperty(RuntimeConstants.RUNTIME_LOG_NAME, velocityLogger.getName());
            ENGINE = new VelocityEngine(velocityProperties);
            ENGINE.init();
        }
    }

    protected static VelocityEngine getEngine() throws MojoExecutionException {
        try {
            return VelocityEngineHolder.ENGINE;
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    @Override
    public final void execute() throws MojoExecutionException {
        try {
            // Expensive construction of ClassLoader
            setProjectClassLoader(buildProjectClassLoader());
            executeInternal();
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            projectClassLoader = null; // Eagerly discard in the case of FAE semantics
        }
    }

    /**
     * Template Method which assumes {@link #projectClassLoader} is set.
     */
    protected abstract void executeInternal() throws Exception;

    protected ClassLoader getProjectClassLoader() {
        return projectClassLoader;
    }

    protected void setProjectClassLoader(ClassLoader projectClassLoader) {
        this.projectClassLoader = projectClassLoader;
    }

    private ClassLoader buildProjectClassLoader() throws DependencyResolutionRequiredException, MalformedURLException {
        URL[] urls = project.getTestClasspathElements().stream()
                .map(File::new)
                .map(ThrowingHelper.wrapAsFunction(e -> e.toURI().toURL()))
                .peek(url -> log.debug("Adding project path {}", url))
                .toArray(URL[]::new);

        // if there are no urls then its because we are testing ourselves, then add the urls for source so java source parser can find them
        if (urls.length == 0) {
            urls = new URL[] { new URL("file:src/main/java/"), new URL("file:src/test/java/") };
        }

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return new URLClassLoader(urls, tccl != null ? tccl : getClass().getClassLoader());
    }

    protected void mergeTemplate(VelocityContext context, File outFile, String templateName) throws MojoExecutionException {
        // ensure parent directories exist
        final File outDir = outFile.getParentFile();
        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            throw new MojoExecutionException("Error creating directory " + outDir);
        }

        // add generated date
        context.put("generatedDate", new Date().toString());
        // add output package
        context.put("packageName", outPackage);
        context.put("newLine", "\n");

        // load velocity template
        Template template;
        try {
            template = getEngine().getTemplate(templateName, "UTF-8");
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        // generate file
        try {
            StringWriter writer = new StringWriter();
            template.merge(context, writer);
            updateResource(null, outFile.toPath(), writer.toString());
        } catch (VelocityException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    public static String getCanonicalName(Class<?> type) {
        // remove java.lang prefix for default Java package
        String canonicalName = type.getCanonicalName();
        final int pkgEnd = canonicalName.lastIndexOf('.');
        if (pkgEnd > 0 && canonicalName.substring(0, pkgEnd).equals("java.lang")) {
            canonicalName = canonicalName.substring(pkgEnd + 1);
        }
        return canonicalName;
    }

    public static void updateResource(BuildContext buildContext, Path out, String data) {
        try {
            if (FileUtil.updateFile(out, data)) {
                refresh(buildContext, out);
            }
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    public static void refresh(BuildContext buildContext, Path file) {
        if (buildContext != null) {
            buildContext.refresh(file.toFile());
        }
    }

}
