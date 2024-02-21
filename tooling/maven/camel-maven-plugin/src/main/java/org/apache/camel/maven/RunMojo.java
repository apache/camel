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
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.artifact.MavenMetadataSource;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.codehaus.mojo.exec.AbstractExecMojo;
import org.codehaus.mojo.exec.ExecutableDependency;
import org.codehaus.mojo.exec.Property;

/**
 * Runs a CamelContext using any Spring configuration files found in <code>META-INF/spring/*.xml</code>, and
 * <code>camel-*.xml</code> and starting up the context.
 */
@Mojo(name = "run", defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class RunMojo extends AbstractExecMojo {

    private static final String LOG4J_TEMPLATE = ""
                                                 + "appender.stdout.type = Console\n"
                                                 + "appender.stdout.name = out\n"
                                                 + "appender.stdout.layout.type = PatternLayout\n"
                                                 + "appender.stdout.layout.pattern = %style{%d{yyyy-MM-dd HH:mm:ss.SSS}}{Dim} %highlight{%5p} %style{%pid}{Magenta} %style{---}{Dim} %style{[%15.15t]}{Dim} %style{%-40.40c}{Cyan} : %m%n\n"
                                                 + "rootLogger.level = @@@LOGGING_LEVEL@@@\n"
                                                 + "rootLogger.appenderRef.out.ref = out\n";

    // this code is based on a copy-and-paste of maven-exec-plugin
    //
    // If we could avoid the mega-cut-n-paste it would really really help!
    // ideally all I wanna do is auto-default 2 values!
    // namely the main and the command line arguments..

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * Sets the time duration (seconds) that the application will run for before terminating. A value <= 0 will run
     * forever.
     */
    @Parameter(property = "camel.duration", defaultValue = "-1")
    protected String duration;

    /**
     * Sets the idle time duration (seconds) duration that the application can be idle before terminating. A value <= 0
     * will run forever.
     */
    @Parameter(property = "camel.durationIdle", defaultValue = "-1")
    protected String durationIdle;

    /**
     * Sets the duration of maximum number of messages that the application will process before terminating.
     */
    @Parameter(property = "camel.duration.maxMessages", defaultValue = "-1")
    protected String durationMaxMessages;

    /**
     * Whether to log the classpath when starting
     */
    @Parameter(property = "camel.logClasspath", defaultValue = "false")
    protected boolean logClasspath;

    /**
     * Whether to use built-in console logging (uses log4j), which does not require to add any logging dependency to
     * your project.
     *
     * However, the logging is fixed to log to the console, with a color style that is similar to Spring Boot.
     *
     * You can change the root logging level to: FATAL, ERROR, WARN, INFO, DEBUG, TRACE, OFF
     */
    @Parameter(property = "camel.loggingLevel", defaultValue = "OFF")
    protected String loggingLevel;

    /**
     * Whether to use Kamelet (camel-main-kamelet) when running, instead of Spring
     */
    @Parameter(property = "camel.useKamelet")
    protected Boolean useKamelet;

    protected String extendedPluginDependencyArtifactId;

    @Component
    protected ArtifactResolver artifactResolver;

    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private ArtifactMetadataSource metadataSource;

    @Parameter(property = "localRepository")
    private ArtifactRepository localRepository;

    @Parameter(property = "project.remoteArtifactRepositories")
    private List<ArtifactRepository> remoteRepositories;

    @Component
    private MavenProjectBuilder projectBuilder;

    @Parameter(property = "plugin.artifacts")
    private List<Artifact> pluginDependencies;

    /**
     * Whether to enable the tracer or not
     */
    @Parameter(property = "camel.trace")
    private boolean trace;

    /**
     * The main class to execute.
     */
    @Parameter(property = "camel.mainClass")
    private String mainClass;

    /**
     * The classpath based application context uri that spring want to gets.
     */
    @Parameter(property = "camel.applicationContextUri")
    private String applicationContextUri;

    /**
     * The filesystem based application context uri that spring want to gets.
     */
    @Parameter(property = "camel.fileApplicationContextUri")
    private String fileApplicationContextUri;

    /**
     * The configureAdmin persistent id, it will be used when loading the camel context from blueprint.
     */
    @Parameter(property = "camel.configAdminPid")
    private String configAdminPid;

    /**
     * The configureAdmin persistent file name, it will be used when loading the camel context from blueprint.
     */
    @Parameter(property = "camel.configAdminFileName")
    private String configAdminFileName;

    /**
     * The class arguments.
     */
    @Parameter(property = "camel.arguments")
    private String[] arguments;

    /**
     * A list of system properties to be passed. Note: as the execution is not forked, some system properties required
     * by the JVM cannot be passed here. Use MAVEN_OPTS or the exec:exec instead. See the user guide for more
     * information.
     */
    private Property[] systemProperties;

    /**
     * Deprecated; this is not needed anymore. Indicates if mojo should be kept running after the mainclass terminates.
     * Usefull for serverlike apps with deamonthreads.
     */
    @Parameter(property = "camel.keepAlive")
    private boolean keepAlive;

    /**
     * Indicates if the project dependencies should be used when executing the main class.
     */
    @Parameter(property = "camel.includeProjectDependencies", defaultValue = "true")
    private boolean includeProjectDependencies;

    /**
     * Indicates if this plugin's dependencies should be used when executing the main class.
     * <p/>
     * This is useful when project dependencies are not appropriate. Using only the plugin dependencies can be
     * particularly useful when the project is not a java project. For example a mvn project using the csharp plugins
     * only expects to see dotnet libraries as dependencies.
     */
    @Parameter(property = "camel.includePluginDependencies", defaultValue = "false")
    private boolean includePluginDependencies;

    /**
     * If provided the ExecutableDependency identifies which of the plugin dependencies contains the executable class.
     * This will have the affect of only including plugin dependencies required by the identified ExecutableDependency.
     * <p/>
     * If includeProjectDependencies is set to <code>true</code>, all of the project dependencies will be included on
     * the executable's classpath. Whether a particular project dependency is a dependency of the identified
     * ExecutableDependency will be irrelevant to its inclusion in the classpath.
     */
    @Parameter(property = "camel.executableDependency")
    private ExecutableDependency executableDependency;

    /**
     * Whether to interrupt/join and possibly stop the daemon threads upon quitting. <br/>
     * If this is <code>false</code>, maven does nothing about the daemon threads. When maven has no more work to do,
     * the VM will normally terminate any remaining daemon threads.
     * <p>
     * In certain cases (in particular if maven is embedded), you might need to keep this enabled to make sure threads
     * are properly cleaned up to ensure they don't interfere with subsequent activity. In that case, see
     * {@link #daemonThreadJoinTimeout} and {@link #stopUnresponsiveDaemonThreads} for further tuning.
     * </p>
     */
    @Parameter(property = "camel.cleanupDaemonThreads", defaultValue = "true")
    private boolean cleanupDaemonThreads;

    /**
     * This defines the number of milliseconds to wait for daemon threads to quit following their interruption.<br/>
     * This is only taken into account if {@link #cleanupDaemonThreads} is <code>true</code>. A value &lt;=0 means to
     * not timeout (i.e. wait indefinitely for threads to finish). Following a timeout, a warning will be logged.
     * <p>
     * Note: properly coded threads <i>should</i> terminate upon interruption but some threads may prove problematic: as
     * the VM does interrupt daemon threads, some code may not have been written to handle interruption properly. For
     * example java.util.Timer is known to not handle interruptions in JDK &lt;= 1.6. So it is not possible for us to
     * infinitely wait by default otherwise maven could hang. A sensible default value has been chosen, but this default
     * value <i>may change</i> in the future based on user feedback.
     * </p>
     */
    @Parameter(property = "camel.daemonThreadJoinTimeout", defaultValue = "15000")
    private long daemonThreadJoinTimeout;

    /**
     * Wether to call {@link Thread#stop()} following a timing out of waiting for an interrupted thread to finish. This
     * is only taken into account if {@link #cleanupDaemonThreads} is <code>true</code> and the
     * {@link #daemonThreadJoinTimeout} threshold has been reached for an uncooperative thread. If this is
     * <code>false</code>, or if {@link Thread#stop()} fails to get the thread to stop, then a warning is logged and
     * Maven will continue on while the affected threads (and related objects in memory) linger on. Consider setting
     * this to <code>true</code> if you are invoking problematic code that you can't fix. An example is
     * {@link java.util.Timer} which doesn't respond to interruption. To have <code>Timer</code> fixed, vote for
     * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6336543">this bug</a>.
     */
    @Parameter(property = "camel.stopUnresponsiveDaemonThreads", defaultValue = "15000")
    private boolean stopUnresponsiveDaemonThreads;

    private Properties originalSystemProperties;

    private String extraPluginDependencyArtifactId;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the threads it generated failed.
     * @throws MojoFailureException   something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        String skip = System.getProperties().getProperty("maven.test.skip");
        if (skip == null || "false".equals(skip)) {
            // lets log a INFO about how to skip tests if you want to, so you can run faster
            getLog().info("You can skip tests from the command line using: mvn " + goal() + " -Dmaven.test.skip=true");
        }

        boolean usingKameletMain;
        if (useKamelet != null) {
            // use configured value
            usingKameletMain = useKamelet;
        } else {
            // auto detect if we have blueprint
            usingKameletMain = detectKameletOnClassPath();
        }

        // lets create the command line arguments to pass in...
        List<String> args = new ArrayList<>();
        if (trace) {
            args.add("-t");
        }

        if (applicationContextUri != null) {
            args.add("-ac");
            args.add(applicationContextUri);
        } else if (fileApplicationContextUri != null) {
            args.add("-fa");
            args.add(fileApplicationContextUri);
        }

        if (!duration.equals("-1")) {
            args.add("-d");
            args.add(duration);
        }
        if (!durationIdle.equals("-1")) {
            args.add("-di");
            args.add(durationIdle);
        }
        if (!durationMaxMessages.equals("-1")) {
            args.add("-dm");
            args.add(durationMaxMessages);
        }
        if (arguments != null) {
            args.addAll(Arrays.asList(arguments));
        }

        if (mainClass == null && usingKameletMain) {
            mainClass = "org.apache.camel.main.KameletMain";
            // must include plugin dependencies for kamelet
            extraPluginDependencyArtifactId = "camel-kamelet-main";
            getLog().info("Using " + mainClass + " to initiate a CamelContext");
        } else if (mainClass != null) {
            getLog().info("Using custom " + mainClass + " to initiate a CamelContext");
        } else {
            // use spring by default
            getLog().info("Using org.apache.camel.spring.Main to initiate a CamelContext");
            mainClass = "org.apache.camel.spring.Main";
        }

        arguments = new String[args.size()];
        args.toArray(arguments);

        if (getLog().isDebugEnabled()) {
            StringBuilder msg = new StringBuilder("Invoking: ");
            msg.append(mainClass);
            msg.append(".main(");
            for (int i = 0; i < arguments.length; i++) {
                if (i > 0) {
                    msg.append(", ");
                }
                msg.append(arguments[i]);
            }
            msg.append(")");
            getLog().debug(msg);
        }

        final ClassLoader loader = getClassLoader();
        IsolatedThreadGroup threadGroup = new IsolatedThreadGroup(mainClass /* name */);

        if (useKamelet != null && usingKameletMain && !detectKameletOnClassPath()) {
            throw new MojoFailureException(
                    "Cannot run Kamelet Main because camel-kamelet-main JAR is not available on classpath");
        }

        final Thread bootstrapThread = new Thread(threadGroup, new Runnable() {
            public void run() {
                try {
                    beforeBootstrapCamel();

                    getLog().info("Starting Camel ...");
                    Method main = Thread.currentThread().getContextClassLoader()
                            .loadClass(mainClass).getMethod("main", String[].class);
                    main.invoke(null, new Object[] { arguments });

                    afterBootstrapCamel();
                } catch (Exception e) { // just pass it on
                    // let it be printed so end users can see the exception on the console
                    getLog().error("*************************************");
                    getLog().error("Error occurred while running main from: " + mainClass);
                    getLog().error(e);
                    getLog().error("*************************************");
                    Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), e);
                }
            }
        }, mainClass + ".main()");

        bootstrapThread.setContextClassLoader(loader);
        setSystemProperties();

        bootstrapThread.start();
        joinNonDaemonThreads(threadGroup);
        // It's plausible that spontaneously a non-daemon thread might be
        // created as we try and shut down,
        // but it's too late since the termination condition (only daemon
        // threads) has been triggered.
        if (keepAlive) {
            getLog().warn("Warning: keepAlive is now deprecated and obsolete. Do you need it? Please comment on MEXEC-6.");
            waitFor(0);
        }

        if (cleanupDaemonThreads) {

            terminateThreads(threadGroup);

            try {
                threadGroup.destroy();
            } catch (IllegalThreadStateException e) {
                getLog().warn("Couldn't destroy threadgroup " + threadGroup, e);
            }
        }

        if (originalSystemProperties != null) {
            System.setProperties(originalSystemProperties);
        }

        synchronized (threadGroup) {
            if (threadGroup.uncaughtException != null) {
                throw new MojoExecutionException(null, threadGroup.uncaughtException);
            }
        }

        registerSourceRoots();
    }

    protected String goal() {
        return "camel:run";
    }

    /**
     * Allows plugin extensions to do custom logic before bootstrapping Camel.
     */
    protected void beforeBootstrapCamel() throws Exception {
        // noop
    }

    /**
     * Allows plugin extensions to do custom logic after bootstrapping Camel.
     */
    protected void afterBootstrapCamel() throws Exception {
        // noop
    }

    class IsolatedThreadGroup extends ThreadGroup {
        Throwable uncaughtException; // synchronize access to this

        IsolatedThreadGroup(String name) {
            super(name);
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            if (throwable instanceof ThreadDeath) {
                return; // harmless
            }
            boolean doLog = false;
            synchronized (this) {
                // only remember the first one
                if (uncaughtException == null) {
                    uncaughtException = throwable; // will be reported
                    // eventually
                } else {
                    doLog = true;
                }
            }
            if (doLog) {
                getLog().warn("an additional exception was thrown", throwable);
            }
        }
    }

    private void joinNonDaemonThreads(ThreadGroup threadGroup) {
        boolean foundNonDaemon;
        do {
            foundNonDaemon = false;
            Collection<Thread> threads = getActiveThreads(threadGroup);
            for (Thread thread : threads) {
                if (thread.isDaemon()) {
                    continue;
                }
                foundNonDaemon = true; // try again; maybe more threads were
                // created while we were busy
                joinThread(thread, 0);
            }
        } while (foundNonDaemon);
    }

    private void joinThread(Thread thread, long timeoutMsecs) {
        try {
            getLog().debug("joining on thread " + thread);
            thread.join(timeoutMsecs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // good practice if don't throw
            getLog().warn("interrupted while joining against thread " + thread, e); // not
            // expected!
        }
        // generally abnormal
        if (thread.isAlive()) {
            getLog().warn("thread " + thread + " was interrupted but is still alive after waiting at least "
                          + timeoutMsecs + "msecs");
        }
    }

    private void terminateThreads(ThreadGroup threadGroup) {
        StopWatch watch = new StopWatch();
        Set<Thread> uncooperativeThreads = new HashSet<>(); // these were not responsive
        // to interruption
        for (Collection<Thread> threads = getActiveThreads(threadGroup);
             !threads.isEmpty();
             threads = getActiveThreads(threadGroup), threads
                     .removeAll(uncooperativeThreads)) {
            // Interrupt all threads we know about as of this instant (harmless
            // if spuriously went dead (! isAlive())
            // or if something else interrupted it ( isInterrupted() ).
            for (Thread thread : threads) {
                getLog().debug("interrupting thread " + thread);
                thread.interrupt();
            }
            // Now join with a timeout and call stop() (assuming flags are set
            // right)
            for (Thread thread : threads) {
                if (!thread.isAlive()) {
                    continue; // and, presumably it won't show up in
                    // getActiveThreads() next iteration
                }
                if (daemonThreadJoinTimeout <= 0) {
                    joinThread(thread, 0); // waits until not alive; no timeout
                    continue;
                }
                long timeout = daemonThreadJoinTimeout - watch.taken();
                if (timeout > 0) {
                    joinThread(thread, timeout);
                }
                if (!thread.isAlive()) {
                    continue;
                }
                uncooperativeThreads.add(thread); // ensure we don't process
                // again
                if (stopUnresponsiveDaemonThreads) {
                    getLog().warn("thread " + thread + " will be Thread.stop()'ed");
                    thread.stop();
                } else {
                    getLog().warn("thread " + thread
                                  + " will linger despite being asked to die via interruption");
                }
            }
        }
        if (!uncooperativeThreads.isEmpty()) {
            getLog().warn("NOTE: "
                          + uncooperativeThreads.size()
                          + " thread(s) did not finish despite being asked to "
                          + " via interruption. This is not a problem with exec:java, it is a problem with the running code."
                          + " Although not serious, it should be remedied.");
        } else {
            int activeCount = threadGroup.activeCount();
            if (activeCount != 0) {
                // TODO this may be nothing; continue on anyway; perhaps don't
                // even log in future
                Thread[] threadsArray = new Thread[1];
                threadGroup.enumerate(threadsArray);
                if (getLog().isDebugEnabled()) {
                    getLog().debug("strange; " + activeCount + " thread(s) still active in the group "
                                   + threadGroup + " such as " + threadsArray[0]);
                }
            }
        }
    }

    private Collection<Thread> getActiveThreads(ThreadGroup threadGroup) {
        Thread[] threads = new Thread[threadGroup.activeCount()];
        int numThreads = threadGroup.enumerate(threads);
        Collection<Thread> result = new ArrayList<>(numThreads);
        for (int i = 0; i < threads.length && threads[i] != null; i++) {
            result.add(threads[i]);
        }
        // note: result should be modifiable
        return result;
    }

    /**
     * Pass any given system properties to the java system properties.
     */
    private void setSystemProperties() {
        if (systemProperties != null) {
            originalSystemProperties = System.getProperties();
            for (Property systemProperty : systemProperties) {
                String value = systemProperty.getValue();
                System.setProperty(systemProperty.getKey(), value == null ? "" : value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean detectKameletOnClassPath() {
        List<Dependency> deps = project.getCompileDependencies();
        for (Dependency dep : deps) {
            if ("org.apache.camel".equals(dep.getGroupId()) && "camel-kamelet-main".equals(dep.getArtifactId())) {
                getLog().info("camel-kamelet-main detected on classpath");
                return true;
            }
        }

        // maybe there are Kamelet YAML files
        List<Resource> resources = project.getResources();
        for (Resource res : resources) {
            File dir = new File(res.getDirectory());
            File kamelets = new File(dir, "kamelets");
            if (kamelets.exists() && kamelets.isDirectory()) {
                getLog().info("Kamelets YAML files detected in directory " + kamelets);
                return true;
            }
        }

        return false;
    }

    /**
     * Set up a classloader for the execution of the main class.
     *
     * @return                        the classloader
     * @throws MojoExecutionException
     */
    private ClassLoader getClassLoader() throws MojoExecutionException, MojoFailureException {
        final List<Artifact> classpath = getClasspath();
        final List<URL> classpathURLs = new ArrayList<>(classpath.size());
        try {
            for (Artifact artifact : classpath) {
                File file = artifact.getFile();
                if (file != null) {
                    classpathURLs.add(file.toURI().toURL());
                }
            }
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Error during setting up classpath", e);
        }

        if (logClasspath) {
            getLog().info("Classpath:");
            for (URL url : classpathURLs) {
                getLog().info("  " + url.getFile());
            }
        }
        return new URLClassLoader(classpathURLs.toArray(new URL[0]));
    }

    /**
     * @return the list of artifacts corresponding to the classpath to use when launching the application
     */
    protected List<Artifact> getClasspath() throws MojoExecutionException, MojoFailureException {
        final List<Artifact> classpath = new ArrayList<>();
        // project classpath must be first
        this.addRelevantProjectDependenciesToClasspath(classpath);
        // and extra plugin classpath
        this.addExtraPluginDependenciesToClasspath(classpath);
        // and plugin classpath last
        this.addRelevantPluginDependenciesToClasspath(classpath);

        if (!loggingLevel.equals("OFF")) {
            getLog().info("Using built-in logging level: " + loggingLevel);
            // and extra plugin classpath
            this.addConsoleLogDependenciesToClasspath(classpath);
            // setup logging which can only be done by copying log4j.properties to project output to be in classpath
            try {
                String out = LOG4J_TEMPLATE.replace("@@@LOGGING_LEVEL@@@", loggingLevel);
                IOHelper.writeText(out, new File(project.getBuild().getOutputDirectory() + "/log4j2.properties"));
            } catch (Exception e) {
                throw new MojoFailureException("Error configuring loggingLevel", e);
            }
        }
        return classpath;
    }

    /**
     * Add any relevant project dependencies to the classpath. Indirectly takes includePluginDependencies and
     * ExecutableDependency into consideration.
     *
     * @param  classpath              the list of artifacts representing the classpath to which artifacts should be
     *                                added
     * @throws MojoExecutionException
     */
    private void addRelevantPluginDependenciesToClasspath(List<Artifact> classpath) throws MojoExecutionException {
        if (hasCommandlineArgs()) {
            arguments = parseCommandlineArgs();
        }

        for (Artifact classPathElement : this.determineRelevantPluginDependencies()) {
            // we must skip org.osgi.core, otherwise we get a
            // java.lang.NoClassDefFoundError: org.osgi.vendor.framework property not set
            if (classPathElement.getArtifactId().equals("org.osgi.core")) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Skipping org.osgi.core -> " + classPathElement.getGroupId() + "/"
                                   + classPathElement.getArtifactId() + "/" + classPathElement.getVersion());
                }
                continue;
            }

            getLog().debug("Adding plugin dependency artifact: " + classPathElement.getArtifactId()
                           + " to classpath");
            classpath.add(classPathElement);
        }

    }

    /**
     * Add any relevant project dependencies to the classpath. Indirectly takes includePluginDependencies and
     * ExecutableDependency into consideration.
     *
     * @param  classpath              the list of artifacts representing the classpath to which artifacts should be
     *                                added
     * @throws MojoExecutionException
     */
    private void addExtraPluginDependenciesToClasspath(List<Artifact> classpath) throws MojoExecutionException {
        if (extraPluginDependencyArtifactId == null && extendedPluginDependencyArtifactId == null) {
            return;
        }

        final Set<Artifact> artifacts = new HashSet<>(this.pluginDependencies);
        for (Artifact artifact : artifacts) {
            if (artifact.getArtifactId().equals(extraPluginDependencyArtifactId)
                    || artifact.getArtifactId().equals(extendedPluginDependencyArtifactId)) {
                getLog().debug("Adding extra plugin dependency artifact: " + artifact.getArtifactId()
                               + " to classpath");
                classpath.add(artifact);

                // add the transient dependencies of this artifact
                Set<Artifact> deps = resolveExecutableDependencies(artifact, true);
                if (deps != null) {
                    for (Artifact dep : deps) {
                        getLog().debug("Adding extra plugin dependency artifact: " + dep.getArtifactId()
                                       + " to classpath");
                        classpath.add(dep);
                    }
                }
            }
        }
    }

    /**
     * Adds the JARs needed for using the built-in logging to console
     */
    private void addConsoleLogDependenciesToClasspath(List<Artifact> classpath) {
        Set<Artifact> artifacts = new HashSet<>(this.pluginDependencies);
        for (Artifact artifact : artifacts) {
            // add these loggers in the beginning so they are first
            if (artifact.getArtifactId().equals("jansi")) {
                // jansi for logging in color
                classpath.add(0, artifact);
            } else if (artifact.getGroupId().equals("org.apache.logging.log4j")) {
                // add log4j as this is needed
                classpath.add(0, artifact);
            } else if (artifact.getArtifactId().equals("camel-maven-plugin")) {
                // add ourselves
                classpath.add(0, artifact);
            }
        }
    }

    /**
     * Add any relevant project dependencies to the classpath. Takes includeProjectDependencies into consideration.
     *
     * @param  classpath              the list of artifacts representing the classpath to which artifacts should be
     *                                added
     * @throws MojoExecutionException
     */
    private void addRelevantProjectDependenciesToClasspath(List<Artifact> classpath) throws MojoExecutionException {
        if (this.includeProjectDependencies) {
            getLog().debug("Project Dependencies will be included.");

            File mainClasses = new File(project.getBuild().getOutputDirectory());
            getLog().debug("Adding to classpath : " + mainClasses);
            classpath.add(
                    new ProjectArtifact(project) {
                        @Override
                        public File getFile() {
                            return mainClasses;
                        }
                    });

            Set<Artifact> dependencies = CastUtils.cast(project.getArtifacts());

            // system scope dependencies are not returned by maven 2.0. See
            // MEXEC-17
            dependencies.addAll(getAllNonTestScopedDependencies());

            for (Artifact classPathElement : dependencies) {
                getLog().debug("Adding project dependency artifact: " + classPathElement.getArtifactId()
                               + " to classpath");
                classpath.add(classPathElement);
            }
        } else {
            getLog().debug("Project Dependencies will be excluded.");
        }
    }

    private Collection<Artifact> getAllNonTestScopedDependencies() throws MojoExecutionException {
        List<Artifact> answer = new ArrayList<>();

        for (Artifact artifact : getAllDependencies()) {

            // do not add test artifacts
            if (!artifact.getScope().equals(Artifact.SCOPE_TEST)) {
                answer.add(artifact);
            }
        }
        return answer;
    }

    // generic method to retrieve all the transitive dependencies
    private Collection<Artifact> getAllDependencies() throws MojoExecutionException {
        List<Artifact> artifacts = new ArrayList<>();

        for (Dependency dependency : project.getDependencies()) {
            String groupId = dependency.getGroupId();
            String artifactId = dependency.getArtifactId();

            VersionRange versionRange;
            try {
                versionRange = VersionRange.createFromVersionSpec(dependency.getVersion());
            } catch (InvalidVersionSpecificationException e) {
                throw new MojoExecutionException("unable to parse version", e);
            }

            String type = dependency.getType();
            if (type == null) {
                type = "jar";
            }
            String classifier = dependency.getClassifier();
            boolean optional = dependency.isOptional();
            String scope = dependency.getScope();
            if (scope == null) {
                scope = Artifact.SCOPE_COMPILE;
            }

            Artifact art = this.artifactFactory.createDependencyArtifact(groupId, artifactId, versionRange,
                    type, classifier, scope, null, optional);

            if (scope.equalsIgnoreCase(Artifact.SCOPE_SYSTEM)) {
                art.setFile(new File(dependency.getSystemPath()));
            }

            List<String> exclusions = new ArrayList<>();
            for (Exclusion exclusion : dependency.getExclusions()) {
                exclusions.add(exclusion.getGroupId() + ":" + exclusion.getArtifactId());
            }

            ArtifactFilter newFilter = new ExcludesArtifactFilter(exclusions);

            art.setDependencyFilter(newFilter);

            artifacts.add(art);
        }

        return artifacts;
    }

    /**
     * Determine all plugin dependencies relevant to the executable. Takes includePlugins, and the executableDependency
     * into consideration.
     *
     * @return                        a set of Artifact objects. (Empty set is returned if there are no relevant plugin
     *                                dependencies.)
     * @throws MojoExecutionException
     */
    private Set<Artifact> determineRelevantPluginDependencies() throws MojoExecutionException {
        Set<Artifact> relevantDependencies;
        if (this.includePluginDependencies) {
            if (this.executableDependency == null) {
                getLog().debug("All Plugin Dependencies will be included.");
                relevantDependencies = new HashSet<>(this.pluginDependencies);
            } else {
                getLog().debug("Selected plugin Dependencies will be included.");
                Artifact executableArtifact = this.findExecutableArtifact();
                Artifact executablePomArtifact = this.getExecutablePomArtifact(executableArtifact);
                relevantDependencies = this.resolveExecutableDependencies(executablePomArtifact, false);
            }
        } else {
            getLog().debug("Only Direct Plugin Dependencies will be included.");
            PluginDescriptor descriptor = (PluginDescriptor) getPluginContext().get("pluginDescriptor");
            try {
                relevantDependencies = artifactResolver
                        .resolveTransitively(MavenMetadataSource
                                .createArtifacts(this.artifactFactory,
                                        descriptor.getPlugin().getDependencies(),
                                        null, null, null),
                                this.project.getArtifact(),
                                Collections.emptyMap(),
                                this.localRepository,
                                this.remoteRepositories,
                                metadataSource,
                                new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME),
                                Collections.emptyList())
                        .getArtifacts();
            } catch (Exception ex) {
                throw new MojoExecutionException(
                        "Encountered problems resolving dependencies of the plugin "
                                                 + "in preparation for its execution.",
                        ex);
            }
        }
        return relevantDependencies;
    }

    /**
     * Get the artifact which refers to the POM of the executable artifact.
     *
     * @param  executableArtifact this artifact refers to the actual assembly.
     * @return                    an artifact which refers to the POM of the executable artifact.
     */
    private Artifact getExecutablePomArtifact(Artifact executableArtifact) {
        return this.artifactFactory.createBuildArtifact(executableArtifact.getGroupId(), executableArtifact
                .getArtifactId(), executableArtifact.getVersion(), "pom");
    }

    /**
     * Examine the plugin dependencies to find the executable artifact.
     *
     * @return                        an artifact which refers to the actual executable tool (not a POM)
     * @throws MojoExecutionException
     */
    @Override
    protected Artifact findExecutableArtifact() throws MojoExecutionException {
        // ILimitedArtifactIdentifier execToolAssembly =
        // this.getExecutableToolAssembly();

        Artifact executableTool = null;
        for (Artifact pluginDep : this.pluginDependencies) {
            if (this.executableDependency.matches(pluginDep)) {
                executableTool = pluginDep;
                break;
            }
        }

        if (executableTool == null) {
            throw new MojoExecutionException(
                    "No dependency of the plugin matches the specified executableDependency."
                                             + "  Specified executableToolAssembly is: "
                                             + executableDependency.toString());
        }

        return executableTool;
    }

    private Set<Artifact> resolveExecutableDependencies(Artifact executablePomArtifact, boolean ignoreFailures)
            throws MojoExecutionException {

        Set<Artifact> executableDependencies = null;
        try {
            MavenProject executableProject = this.projectBuilder.buildFromRepository(executablePomArtifact,
                    this.remoteRepositories,
                    this.localRepository);

            // get all the dependencies for the executable project
            List<Dependency> dependencies = executableProject.getDependencies();

            // make Artifacts of all the dependencies
            Set<Artifact> dependencyArtifacts
                    = MavenMetadataSource.createArtifacts(this.artifactFactory, dependencies,
                            null, null, null);

            // not forgetting the Artifact of the project itself
            dependencyArtifacts.add(executableProject.getArtifact());

            // resolve runtime dependencies transitively to obtain a comprehensive list of assemblies
            ArtifactResolutionResult result = artifactResolver.resolveTransitively(dependencyArtifacts,
                    executablePomArtifact,
                    Collections.emptyMap(),
                    this.localRepository,
                    this.remoteRepositories,
                    metadataSource, new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME),
                    Collections.emptyList());
            executableDependencies = CastUtils.cast(result.getArtifacts());

        } catch (Exception ex) {
            if (ignoreFailures) {
                getLog().debug("Ignoring maven resolving dependencies failure " + ex.getMessage());
            } else {
                throw new MojoExecutionException(
                        "Encountered problems resolving dependencies of the executable "
                                                 + "in preparation for its execution.",
                        ex);
            }
        }

        return executableDependencies;
    }

    /**
     * Stop program execution for nn millis.
     *
     * @param millis the number of millis-seconds to wait for, <code>0</code> stops program forever.
     */
    private void waitFor(long millis) {
        Object lock = new Object();
        synchronized (lock) {
            try {
                lock.wait(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // good practice if don't throw
                getLog().warn("Spuriously interrupted while waiting for " + millis + "ms", e);
            }
        }
    }

}
