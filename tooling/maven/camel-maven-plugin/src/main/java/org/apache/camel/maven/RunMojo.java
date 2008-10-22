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
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.artifact.MavenMetadataSource;
import org.codehaus.mojo.exec.AbstractExecMojo;
import org.codehaus.mojo.exec.ExecutableDependency;
import org.codehaus.mojo.exec.Property;

/**
 * Runs a CamelContext using any Spring XML configuration files found in
 * <code>META-INF/spring/*.xml</code> and <code>camel-*.xml</code> and
 * starting up the context.
 *
 * @goal run
 * @requiresDependencyResolution runtime
 * @execute phase="test-compile"
 */
public class RunMojo extends AbstractExecMojo {

    // TODO
    // this code is based on a copy-and-paste of maven-exec-plugin
    //
    // If we could avoid the mega-cut-n-paste it would really really help!
    // ideally all I wanna do is auto-default 2 values!
    // namely the main and the command line arguments..

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The duration to run the application for which by default is in
     * milliseconds. A value <= 0 will run forever.
     * Adding a s indicates seconds - eg "5s" means 5 seconds.
     *
     * @parameter expression="-1"
     *
     */
    protected String duration;

    /**
     * The DOT output directory name used to generate the DOT diagram of the
     * route definitions
     *
     * @parameter expression="${project.build.directory}/site/cameldoc"
     * @readonly
     */
    protected String dotDir;

    /**
     * Allows the DOT file generation to be disabled
     *
     * @parameter expression="true"
     * @readonly
     */
    protected boolean dotEnabled;

    /**
     * @component
     */
    private ArtifactResolver artifactResolver;

    /**
     * @component
     */
    private ArtifactFactory artifactFactory;

    /**
     * @component
     */
    private ArtifactMetadataSource metadataSource;

    /**
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository localRepository;

    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     */
    private List remoteRepositories;

    /**
     * @component
     */
    private MavenProjectBuilder projectBuilder;

    /**
     * @parameter expression="${plugin.artifacts}"
     * @readonly
     */
    private List pluginDependencies;

    /**
     * Whether to enable the debugger or not
     *
     * @parameter expression="${camel.debug}"
     *            default-value="false"
     * @required
     */
    private boolean debug;

    /**
     * Whether to enable the tracer or not
     *
     * @parameter expression="${camel.trace}"
     *            default-value="false"
     * @required
     */
    private boolean trace;

    /**
     * Output all routes to the specified XML file
     *
     * @parameter expression="${camel.routesOutputFile}"
     */
    private String routesOutputFile;    
    
    /**
     * The main class to execute.
     *
     * @parameter expression="${camel.mainClass}"
     *            default-value="org.apache.camel.spring.Main"
     * @required
     */
    private String mainClass;

    /**
     * The classpath based application context uri that spring want to gets.
     *
     * @parameter expression="${camel.applicationContextUri}"
     */
    private String applicationContextUri;

    /**
     * The filesystem based application context uri that spring want to gets.
     *
     * @parameter expression="${camel.fileApplicationContextUri}"
     */
    private String fileApplicationContextUri;

    /**
     * The class arguments.
     *
     * @parameter expression="${camel.arguments}"
     */
    private String[] arguments;

    /**
     * A list of system properties to be passed. Note: as the execution is not
     * forked, some system properties required by the JVM cannot be passed here.
     * Use MAVEN_OPTS or the exec:exec instead. See the user guide for more
     * information.
     *
     * @parameter
     */
    private Property[] systemProperties;

    /**
     * Deprecated; this is not needed anymore. Indicates if mojo should be kept
     * running after the mainclass terminates. Usefull for serverlike apps with
     * deamonthreads.
     *
     * @parameter expression="${camel.keepAlive}" default-value="false"
     */
    private boolean keepAlive;

    /**
     * Indicates if the project dependencies should be used when executing the
     * main class.
     *
     * @parameter expression="${camel.includeProjectDependencies}"
     *            default-value="true"
     */
    private boolean includeProjectDependencies;

    /**
     * Indicates if this plugin's dependencies should be used when executing the
     * main class. <p/> This is useful when project dependencies are not
     * appropriate. Using only the plugin dependencies can be particularly
     * useful when the project is not a java project. For example a mvn project
     * using the csharp plugins only expects to see dotnet libraries as
     * dependencies.
     *
     * @parameter expression="${camel.includePluginDependencies}"
     *            default-value="false"
     */
    private boolean includePluginDependencies;

    /**
     * If provided the ExecutableDependency identifies which of the plugin
     * dependencies contains the executable class. This will have the affect of
     * only including plugin dependencies required by the identified
     * ExecutableDependency. <p/> If includeProjectDependencies is set to
     * <code>true</code>, all of the project dependencies will be included on
     * the executable's classpath. Whether a particular project dependency is a
     * dependency of the identified ExecutableDependency will be irrelevant to
     * its inclusion in the classpath.
     *
     * @parameter
     * @optional
     */
    private ExecutableDependency executableDependency;

    /**
     * Wether to interrupt/join and possibly stop the daemon threads upon
     * quitting. <br/> If this is <code>false</code>, maven does nothing
     * about the daemon threads. When maven has no more work to do, the VM will
     * normally terminate any remaining daemon threads.
     * <p>
     * In certain cases (in particular if maven is embedded), you might need to
     * keep this enabled to make sure threads are properly cleaned up to ensure
     * they don't interfere with subsequent activity. In that case, see
     * {@link #daemonThreadJoinTimeout} and
     * {@link #stopUnresponsiveDaemonThreads} for further tuning.
     * </p>
     *
     * @parameter expression="${camel.cleanupDaemonThreads} default-value="true"
     */
    private boolean cleanupDaemonThreads;

    /**
     * This defines the number of milliseconds to wait for daemon threads to
     * quit following their interruption.<br/> This is only taken into account
     * if {@link #cleanupDaemonThreads} is <code>true</code>. A value &lt;=0
     * means to not timeout (i.e. wait indefinitely for threads to finish).
     * Following a timeout, a warning will be logged.
     * <p>
     * Note: properly coded threads <i>should</i> terminate upon interruption
     * but some threads may prove problematic: as the VM does interrupt daemon
     * threads, some code may not have been written to handle interruption
     * properly. For example java.util.Timer is known to not handle
     * interruptions in JDK &lt;= 1.6. So it is not possible for us to
     * infinitely wait by default otherwise maven could hang. A sensible default
     * value has been chosen, but this default value <i>may change</i> in the
     * future based on user feedback.
     * </p>
     *
     * @parameter expression="${camel.daemonThreadJoinTimeout}"
     *            default-value="15000"
     */
    private long daemonThreadJoinTimeout;

    /**
     * Wether to call {@link Thread#stop()} following a timing out of waiting
     * for an interrupted thread to finish. This is only taken into account if
     * {@link #cleanupDaemonThreads} is <code>true</code> and the
     * {@link #daemonThreadJoinTimeout} threshold has been reached for an
     * uncooperative thread. If this is <code>false</code>, or if
     * {@link Thread#stop()} fails to get the thread to stop, then a warning is
     * logged and Maven will continue on while the affected threads (and related
     * objects in memory) linger on. Consider setting this to <code>true</code>
     * if you are invoking problematic code that you can't fix. An example is
     * {@link java.util.Timer} which doesn't respond to interruption. To have
     * <code>Timer</code> fixed, vote for <a
     * href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6336543">this
     * bug</a>.
     *
     * @parameter expression="${camel.stopUnresponsiveDaemonThreads}
     *            default-value="false"
     */
    private boolean stopUnresponsiveDaemonThreads;

    /**
     * Deprecated this is not needed anymore.
     *
     * @parameter expression="${camel.killAfter}" default-value="-1"
     */
    private long killAfter;

    private Properties originalSystemProperties;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *                 threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (killAfter != -1) {
            getLog().warn("Warning: killAfter is now deprecated. Do you need it ? Please comment on MEXEC-6.");
        }

        // lets create the command line arguments to pass in...
        List<String> args = new ArrayList<String>();
        if (dotDir != null && dotEnabled) {
            args.add("-o");
            args.add(dotDir);
        }
        if (debug) {
            args.add("-x");
        }
        if (trace) {
            args.add("-t");
        }

        if (routesOutputFile != null) {
            args.add("-output");
            args.add(routesOutputFile);
        }        
        
        if (applicationContextUri != null) {
            args.add("-ac");
            args.add(applicationContextUri);
        } else if (fileApplicationContextUri != null) {
            args.add("-fa");
            args.add(fileApplicationContextUri);
        }

        args.add("-d");
        args.add(duration);
        if (arguments != null) {
            args.addAll(Arrays.asList(arguments));
        }
        arguments = new String[args.size()];
        args.toArray(arguments);

        if (getLog().isDebugEnabled()) {
            StringBuffer msg = new StringBuffer("Invoking : ");
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

        IsolatedThreadGroup threadGroup = new IsolatedThreadGroup(mainClass /* name */);
        Thread bootstrapThread = new Thread(threadGroup, new Runnable() {
            public void run() {
                try {
                    Method main = Thread.currentThread().getContextClassLoader().loadClass(mainClass)
                        .getMethod("main", new Class[] {String[].class});
                    if (!main.isAccessible()) {
                        getLog().debug("Setting accessibility to true in order to invoke main().");
                        main.setAccessible(true);
                    }
                    main.invoke(main, new Object[] {arguments});
                } catch (Exception e) { // just pass it on
                    Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), e);
                }
            }
        }, mainClass + ".main()");
        bootstrapThread.setContextClassLoader(getClassLoader());
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

    class IsolatedThreadGroup extends ThreadGroup {
        Throwable uncaughtException; // synchronize access to this

        public IsolatedThreadGroup(String name) {
            super(name);
        }

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
            Collection threads = getActiveThreads(threadGroup);
            for (Iterator iter = threads.iterator(); iter.hasNext();) {
                Thread thread = (Thread)iter.next();
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
        long startTime = System.currentTimeMillis();
        Set uncooperativeThreads = new HashSet(); // these were not responsive
        // to interruption
        for (Collection threads = getActiveThreads(threadGroup); !threads.isEmpty(); threads = getActiveThreads(threadGroup), threads
            .removeAll(uncooperativeThreads)) {
            // Interrupt all threads we know about as of this instant (harmless
            // if spuriously went dead (! isAlive())
            // or if something else interrupted it ( isInterrupted() ).
            for (Iterator iter = threads.iterator(); iter.hasNext();) {
                Thread thread = (Thread)iter.next();
                getLog().debug("interrupting thread " + thread);
                thread.interrupt();
            }
            // Now join with a timeout and call stop() (assuming flags are set
            // right)
            for (Iterator iter = threads.iterator(); iter.hasNext();) {
                Thread thread = (Thread)iter.next();
                if (!thread.isAlive()) {
                    continue; // and, presumably it won't show up in
                    // getActiveThreads() next iteration
                }
                if (daemonThreadJoinTimeout <= 0) {
                    joinThread(thread, 0); // waits until not alive; no timeout
                    continue;
                }
                long timeout = daemonThreadJoinTimeout - (System.currentTimeMillis() - startTime);
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
                getLog().debug("strange; " + activeCount + " thread(s) still active in the group "
                                   + threadGroup + " such as " + threadsArray[0]);
            }
        }
    }

    private Collection getActiveThreads(ThreadGroup threadGroup) {
        Thread[] threads = new Thread[threadGroup.activeCount()];
        int numThreads = threadGroup.enumerate(threads);
        Collection result = new ArrayList(numThreads);
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
            for (int i = 0; i < systemProperties.length; i++) {
                Property systemProperty = systemProperties[i];
                String value = systemProperty.getValue();
                System.setProperty(systemProperty.getKey(), value == null ? "" : value);
            }
        }
    }

    /**
     * Set up a classloader for the execution of the main class.
     *
     * @return the classloader
     * @throws MojoExecutionException
     */
    private ClassLoader getClassLoader() throws MojoExecutionException {
        List classpathURLs = new ArrayList();
        this.addRelevantPluginDependenciesToClasspath(classpathURLs);
        this.addRelevantProjectDependenciesToClasspath(classpathURLs);

        getLog().info("Classpath = " + classpathURLs);
        return new URLClassLoader((URL[])classpathURLs.toArray(new URL[classpathURLs.size()]));
    }

    /**
     * Add any relevant project dependencies to the classpath. Indirectly takes
     * includePluginDependencies and ExecutableDependency into consideration.
     *
     * @param path classpath of {@link java.net.URL} objects
     * @throws MojoExecutionException
     */
    private void addRelevantPluginDependenciesToClasspath(List path) throws MojoExecutionException {
        if (hasCommandlineArgs()) {
            arguments = parseCommandlineArgs();
        }

        try {
            Iterator iter = this.determineRelevantPluginDependencies().iterator();
            while (iter.hasNext()) {
                Artifact classPathElement = (Artifact)iter.next();
                getLog().debug("Adding plugin dependency artifact: " + classPathElement.getArtifactId()
                                   + " to classpath");
                path.add(classPathElement.getFile().toURL());
            }
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Error during setting up classpath", e);
        }

    }

    /**
     * Add any relevant project dependencies to the classpath. Takes
     * includeProjectDependencies into consideration.
     *
     * @param path classpath of {@link java.net.URL} objects
     * @throws MojoExecutionException
     */
    private void addRelevantProjectDependenciesToClasspath(List path) throws MojoExecutionException {
        if (this.includeProjectDependencies) {
            try {
                getLog().debug("Project Dependencies will be included.");

                URL mainClasses = new File(project.getBuild().getOutputDirectory()).toURL();
                getLog().debug("Adding to classpath : " + mainClasses);
                path.add(mainClasses);

                Set dependencies = project.getArtifacts();

                // system scope dependencies are not returned by maven 2.0. See
                // MEXEC-17
                dependencies.addAll(getAllNonTestScopedDependencies());

                Iterator iter = dependencies.iterator();
                while (iter.hasNext()) {
                    Artifact classPathElement = (Artifact)iter.next();
                    getLog().debug("Adding project dependency artifact: " + classPathElement.getArtifactId()
                                       + " to classpath");
                    File file = classPathElement.getFile();
                    if (file != null) {
                        path.add(file.toURL());
                    }
                }

            } catch (MalformedURLException e) {
                throw new MojoExecutionException("Error during setting up classpath", e);
            }
        } else {
            getLog().debug("Project Dependencies will be excluded.");
        }

    }

    private Collection getAllNonTestScopedDependencies() throws MojoExecutionException {
        List answer = new ArrayList();

        for (Iterator artifacts = getAllDependencies().iterator(); artifacts.hasNext();) {
            Artifact artifact = (Artifact)artifacts.next();

            // do not add test artifacts
            if (!artifact.getScope().equals(Artifact.SCOPE_TEST)) {
                answer.add(artifact);
            }
        }
        return answer;
    }

    // generic method to retrieve all the transitive dependencies
    private Collection getAllDependencies() throws MojoExecutionException {
        List artifacts = new ArrayList();

        for (Iterator dependencies = project.getDependencies().iterator(); dependencies.hasNext();) {
            Dependency dependency = (Dependency)dependencies.next();

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
                                                                         type, classifier, scope, optional);

            if (scope.equalsIgnoreCase(Artifact.SCOPE_SYSTEM)) {
                art.setFile(new File(dependency.getSystemPath()));
            }

            List exclusions = new ArrayList();
            for (Iterator j = dependency.getExclusions().iterator(); j.hasNext();) {
                Exclusion e = (Exclusion)j.next();
                exclusions.add(e.getGroupId() + ":" + e.getArtifactId());
            }

            ArtifactFilter newFilter = new ExcludesArtifactFilter(exclusions);

            art.setDependencyFilter(newFilter);

            artifacts.add(art);
        }

        return artifacts;
    }

    /**
     * Determine all plugin dependencies relevant to the executable. Takes
     * includePlugins, and the executableDependency into consideration.
     *
     * @return a set of Artifact objects. (Empty set is returned if there are no
     *         relevant plugin dependencies.)
     * @throws MojoExecutionException
     */
    private Set determineRelevantPluginDependencies() throws MojoExecutionException {
        Set relevantDependencies;
        if (this.includePluginDependencies) {
            if (this.executableDependency == null) {
                getLog().debug("All Plugin Dependencies will be included.");
                relevantDependencies = new HashSet(this.pluginDependencies);
            } else {
                getLog().debug("Selected plugin Dependencies will be included.");
                Artifact executableArtifact = this.findExecutableArtifact();
                Artifact executablePomArtifact = this.getExecutablePomArtifact(executableArtifact);
                relevantDependencies = this.resolveExecutableDependencies(executablePomArtifact);
            }
        } else {
            relevantDependencies = Collections.EMPTY_SET;
            getLog().debug("Plugin Dependencies will be excluded.");
        }
        return relevantDependencies;
    }

    /**
     * Get the artifact which refers to the POM of the executable artifact.
     *
     * @param executableArtifact this artifact refers to the actual assembly.
     * @return an artifact which refers to the POM of the executable artifact.
     */
    private Artifact getExecutablePomArtifact(Artifact executableArtifact) {
        return this.artifactFactory.createBuildArtifact(executableArtifact.getGroupId(), executableArtifact
            .getArtifactId(), executableArtifact.getVersion(), "pom");
    }

    /**
     * Examine the plugin dependencies to find the executable artifact.
     *
     * @return an artifact which refers to the actual executable tool (not a POM)
     * @throws MojoExecutionException
     */
    private Artifact findExecutableArtifact() throws MojoExecutionException {
        // ILimitedArtifactIdentifier execToolAssembly =
        // this.getExecutableToolAssembly();

        Artifact executableTool = null;
        for (Iterator iter = this.pluginDependencies.iterator(); iter.hasNext();) {
            Artifact pluginDep = (Artifact)iter.next();
            if (this.executableDependency.matches(pluginDep)) {
                executableTool = pluginDep;
                break;
            }
        }

        if (executableTool == null) {
            throw new MojoExecutionException("No dependency of the plugin matches the specified executableDependency."
                                                 + "  Specified executableToolAssembly is: "
                                                 + executableDependency.toString());
        }

        return executableTool;
    }

    private Set resolveExecutableDependencies(Artifact executablePomArtifact) throws MojoExecutionException {

        Set executableDependencies;
        try {
            MavenProject executableProject = this.projectBuilder.buildFromRepository(executablePomArtifact,
                                                                                     this.remoteRepositories,
                                                                                     this.localRepository);

            // get all of the dependencies for the executable project
            List dependencies = executableProject.getDependencies();

            // make Artifacts of all the dependencies
            Set dependencyArtifacts = MavenMetadataSource.createArtifacts(this.artifactFactory, dependencies,
                                                                          null, null, null);

            // not forgetting the Artifact of the project itself
            dependencyArtifacts.add(executableProject.getArtifact());

            // resolve all dependencies transitively to obtain a comprehensive
            // list of assemblies
            ArtifactResolutionResult result = artifactResolver.resolveTransitively(dependencyArtifacts,
                                                                                   executablePomArtifact,
                                                                                   Collections.EMPTY_MAP,
                                                                                   this.localRepository,
                                                                                   this.remoteRepositories,
                                                                                   metadataSource, null,
                                                                                   Collections.EMPTY_LIST);
            executableDependencies = result.getArtifacts();

        } catch (Exception ex) {
            throw new MojoExecutionException("Encountered problems resolving dependencies of the executable "
                                             + "in preparation for its execution.", ex);
        }

        return executableDependencies;
    }

    /**
     * Stop program execution for nn millis.
     *
     * @param millis the number of millis-seconds to wait for, <code>0</code>
     *                stops program forever.
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
