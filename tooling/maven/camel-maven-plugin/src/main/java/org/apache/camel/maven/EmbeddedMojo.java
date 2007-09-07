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

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.mojo.exec.AbstractExecMojo;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

/**
 * Runs a CamelContext using any Spring XML configuration files found in
 * <code>META-INF/spring/*.xml</code> and <code>camel-*.xml</code>
 * and starting up the context; then generating
 * the DOT file before closing the context down.
 *
 * @goal embedded
 * @requiresDependencyResolution runtime
 * @execute phase="test-compile"
 */
public class EmbeddedMojo extends AbstractExecMojo {
    /**
     * Project classpath.
     *
     * @parameter expression="${project.testClasspathElements}"
     * @required
     * @readonly
     */
    private List classpathElements;
    /**
     * The path where the generated artifacts will be placed.
     *
     * @parameter expression="${basedir}/target/site/cameldoc"
     * @required
     * @readonly
     */
    private File outputDirectory;
    /**
     * The duration to run the application for which by default is in milliseconds.
     *
     * @parameter expression="-1"
     * @readonly
     */
    protected String duration;
    /**
     * The DOT File name used to generate the DOT diagram of the route definitions
     *
     * @parameter expression="${project.build.directory}/site/cameldoc/routes.dot"
     * @readonly
     */
    protected String dotOutputDir;
    /**
     * Allows the DOT file generation to be disabled
     *
     * @parameter expression="true"
     * @readonly
     */
    protected boolean dotEnabled;

    /**
     * This method will run the mojo
     */
    public void execute() throws MojoExecutionException {
        try {
            executeWithoutWrapping();
        }
        catch (Exception e) {
            throw new MojoExecutionException("Failed: " + e, e);
        }
    }

    public void executeWithoutWrapping() throws MalformedURLException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, MojoExecutionException {
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader newLoader = createClassLoader(oldClassLoader);
            Thread.currentThread().setContextClassLoader(newLoader);
            runCamel(newLoader);
        }
        finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

    // Properties
    //-------------------------------------------------------------------------

    /**
     * Getter for property output directory.
     *
     * @return The value of output directory.
     */
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Setter for the output directory.
     *
     * @param inOutputDirectory The value of output directory.
     */
    public void setOutputDirectory(final File inOutputDirectory) {
        this.outputDirectory = inOutputDirectory;
    }

    public List getClasspathElements() {
        return classpathElements;
    }

    public void setClasspathElements(List classpathElements) {
        this.classpathElements = classpathElements;
    }

    public boolean isDotEnabled() {
        return dotEnabled;
    }

    public void setDotEnabled(boolean dotEnabled) {
        this.dotEnabled = dotEnabled;
    }

    public String getDotOutputDir() {
        return dotOutputDir;
    }

    public void setDotOutputDir(String dotOutputDir) {
        this.dotOutputDir = dotOutputDir;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void runCamel(ClassLoader newLoader) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, MojoExecutionException {
        Class<?> type = newLoader.loadClass("org.apache.camel.spring.Main");
        Method method = type.getMethod("main", String[].class);
        String[] arguments = createArguments();
        getLog().debug("Starting the Camel Main with arguments: " + Arrays.asList(arguments));

        try {
            method.invoke(null, new Object[]{arguments});
        }
        catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            throw new MojoExecutionException("Failed: " + t, t);
        }
    }

    protected String[] createArguments() {
        if (dotEnabled) {
            return new String[]{"-duration", duration, "-outdir", dotOutputDir};
        }
        else {
            return new String[]{"-duration", duration};
        }
    }

    protected ClassLoader createClassLoader(ClassLoader parent) throws MalformedURLException {
        getLog().debug("Using classpath: " + classpathElements);

        int size = classpathElements.size();
        URL[] urls = new URL[size];
        for (int i = 0; i < size; i++) {
            String name = (String) classpathElements.get(i);
            File file = new File(name);
            urls[i] = file.toURL();
        }
        return new URLClassLoader(urls, parent);
    }
}