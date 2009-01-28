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
package org.apache.camel.guice.maven;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.mojo.exec.AbstractExecMojo;

/**
 * Runs a Camel using the
 * <code>jndi.properties</code> file on the classpath to
 * way to <a href="http://camel.apache.org/guice.html">bootstrap via Guice</a>
 *
 * @goal embedded
 * @requiresDependencyResolution runtime
 * @execute phase="test-compile"
 */
public class EmbeddedMojo extends AbstractExecMojo {

    /**
     * The duration to run the application for which by default is in milliseconds.
     * A value <= 0 will run forever.
     * Adding a s indicates seconds - eg "5s" means 5 seconds.
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
    protected String outputDirectory;

    /**
     * Allows the DOT file generation to be disabled
     *
     * @parameter expression="true"
     * @readonly
     */
    protected boolean dotEnabled;

    /**
     * Allows the routes from multiple contexts to be aggregated into one DOT file (in addition to the individual files)
     *
     * @parameter expression="false"
     * @readonly
     */
    protected boolean dotAggregationEnabled;

    /**
     * Project classpath.
     *
     * @parameter expression="${project.testClasspathElements}"
     * @required
     * @readonly
     */
    private List classpathElements;

    /**
     * The main class to execute.
     *
     * @parameter expression="${camel.mainClass}"
     *            default-value="org.apache.camel.guice.Main"
     * @required
     */
    private String mainClass;

    /**
     * This method will run the mojo
     */
    public void execute() throws MojoExecutionException {
        try {
            executeWithoutWrapping();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed: " + e, e);
        }
    }

    public void executeWithoutWrapping() throws MalformedURLException, ClassNotFoundException,
        NoSuchMethodException, IllegalAccessException, MojoExecutionException {
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader newLoader = createClassLoader(null);
            Thread.currentThread().setContextClassLoader(newLoader);
            runCamel(newLoader);
        } finally {
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
    public String getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Setter for the output directory.
     *
     * @param inOutputDirectory The value of output directory.
     */
    public void setOutputDirectory(String inOutputDirectory) {
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

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public boolean isDotAggregationEnabled() {
        return dotAggregationEnabled;
    }

    public void setDotAggregationEnabled(boolean dotAggregationEnabled) {
        this.dotAggregationEnabled = dotAggregationEnabled;
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void runCamel(ClassLoader newLoader) throws ClassNotFoundException, NoSuchMethodException,
        IllegalAccessException, MojoExecutionException {

        getLog().debug("Running Camel in: " + newLoader);
        Class<?> type = newLoader.loadClass(mainClass);
        Method method = type.getMethod("main", String[].class);
        String[] arguments = createArguments();
        getLog().debug("Starting the Camel Main with arguments: " + Arrays.asList(arguments));

        try {
            method.invoke(null, new Object[] {arguments});
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            throw new MojoExecutionException("Failed: " + t, t);
        }
    }

    protected String[] createArguments() {

        ArrayList<String> args = new ArrayList<String>(5);
        if (isDotEnabled()) {
            args.add("-outdir");
            args.add(getOutputDirectory());
        }

        if (isDotAggregationEnabled()) {
            args.add("-aggregate-dot");
            args.add("true");
        }

        args.add("-duration");
        args.add(getDuration());

        return args.toArray(new String[0]);
    }

    public ClassLoader createClassLoader(ClassLoader parent) throws MalformedURLException {
        getLog().debug("Using classpath: " + classpathElements);

        int size = classpathElements.size();
        URL[] urls = new URL[size];
        for (int i = 0; i < size; i++) {
            String name = (String) classpathElements.get(i);
            File file = new File(name);
            urls[i] = file.toURL();
            getLog().debug("URL: " + urls[i]);
        }
        URLClassLoader loader = new URLClassLoader(urls, parent);
        return loader;
    }
}