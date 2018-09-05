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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for API based generation MOJOs.
 */
public abstract class AbstractGeneratorMojo extends AbstractMojo {

    protected static final String PREFIX = "org.apache.camel.";
    protected static final String OUT_PACKAGE = PREFIX + "component.internal";
    protected static final String COMPONENT_PACKAGE = PREFIX + "component";

    private static VelocityEngine engine;
    private static ClassLoader projectClassLoader;

    private static boolean sharedProjectState;

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

    protected AbstractGeneratorMojo() {
        clearSharedProjectState();
    }

    public static void setSharedProjectState(boolean sharedProjectState) {
        AbstractGeneratorMojo.sharedProjectState = sharedProjectState;
    }

    protected static void clearSharedProjectState() {
        if (!sharedProjectState) {
            projectClassLoader = null;
        }
    }

    protected static VelocityEngine getEngine() throws MojoExecutionException {
        if (engine == null) {
            // initialize velocity to load resources from class loader and use Log4J
            Properties velocityProperties = new Properties();
            velocityProperties.setProperty(RuntimeConstants.RESOURCE_LOADER, "cloader");
            velocityProperties.setProperty("cloader.resource.loader.class", ClasspathResourceLoader.class.getName());
            final Logger velocityLogger = LoggerFactory.getLogger("org.apache.camel.maven.Velocity");
            velocityProperties.setProperty(RuntimeConstants.RUNTIME_LOG_NAME, velocityLogger.getName());
            try {
                engine = new VelocityEngine(velocityProperties);
                engine.init();
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
            
        }
        return engine;
    }

    protected ClassLoader getProjectClassLoader() throws MojoExecutionException {
        if (projectClassLoader == null)  {
            final List classpathElements;
            try {
                classpathElements = project.getTestClasspathElements();
            } catch (org.apache.maven.artifact.DependencyResolutionRequiredException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
            final URL[] urls = new URL[classpathElements.size()];
            int i = 0;
            for (Iterator it = classpathElements.iterator(); it.hasNext(); i++) {
                try {
                    urls[i] = new File((String) it.next()).toURI().toURL();
                    log.debug("Adding project path " + urls[i]);
                } catch (MalformedURLException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
            final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            projectClassLoader = new URLClassLoader(urls, tccl != null ? tccl : getClass().getClassLoader());
        }
        return projectClassLoader;
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
        Template template = null;
        try {
            template = getEngine().getTemplate(templateName, "UTF-8");
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        // generate file
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outFile));
            template.merge(context, writer);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (VelocityException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignore) { }
            }
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
}
