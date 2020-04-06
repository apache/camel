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
package org.apache.maven.plugins.javadoc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.lang3.ClassUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.javadoc.options.BootclasspathArtifact;
import org.apache.maven.plugins.javadoc.options.DocletArtifact;
import org.apache.maven.plugins.javadoc.options.Group;
import org.apache.maven.plugins.javadoc.options.JavadocOptions;
import org.apache.maven.plugins.javadoc.options.JavadocPathArtifact;
import org.apache.maven.plugins.javadoc.options.OfflineLink;
import org.apache.maven.plugins.javadoc.options.ResourcesArtifact;
import org.apache.maven.plugins.javadoc.options.Tag;
import org.apache.maven.plugins.javadoc.options.Taglet;
import org.apache.maven.plugins.javadoc.options.TagletArtifact;
import org.apache.maven.plugins.javadoc.options.io.xpp3.JavadocOptionsXpp3Writer;
import org.apache.maven.plugins.javadoc.resolver.JavadocBundle;
import org.apache.maven.plugins.javadoc.resolver.ResourceResolver;
import org.apache.maven.plugins.javadoc.resolver.SourceResolverConfig;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.artifact.filter.resolve.AndFilter;
import org.apache.maven.shared.artifact.filter.resolve.PatternExclusionsFilter;
import org.apache.maven.shared.artifact.filter.resolve.PatternInclusionsFilter;
import org.apache.maven.shared.artifact.filter.resolve.ScopeFilter;
import org.apache.maven.shared.artifact.filter.resolve.TransformableFilter;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.apache.maven.wagon.PathUtils;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.LocationManager;
import org.codehaus.plexus.languages.java.jpms.ModuleNameSource;
import org.codehaus.plexus.languages.java.jpms.ResolvePathRequest;
import org.codehaus.plexus.languages.java.jpms.ResolvePathResult;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsResult;
import org.codehaus.plexus.languages.java.version.JavaVersion;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import static org.apache.maven.plugins.javadoc.JavadocUtil.isEmpty;
import static org.apache.maven.plugins.javadoc.JavadocUtil.isNotEmpty;
import static org.apache.maven.plugins.javadoc.JavadocUtil.toList;
import static org.apache.maven.plugins.javadoc.JavadocUtil.toRelative;

/**
 * Base class with majority of Javadoc functionalities.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @see <a href=
 *      "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html">
 *      The Java API Documentation Generator, 7</a>
 * @since 2.0
 */
public abstract class AbstractJavadocMojo extends AbstractMojo {
    /**
     * Classifier used in the name of the javadoc-options XML file, and in the
     * resources bundle artifact that gets attached to the project. This one is
     * used for non-test javadocs.
     *
     * @see #TEST_JAVADOC_RESOURCES_ATTACHMENT_CLASSIFIER
     * @since 2.7
     */
    public static final String JAVADOC_RESOURCES_ATTACHMENT_CLASSIFIER = "javadoc-resources";

    /**
     * Classifier used in the name of the javadoc-options XML file, and in the
     * resources bundle artifact that gets attached to the project. This one is
     * used for test-javadocs.
     *
     * @see #JAVADOC_RESOURCES_ATTACHMENT_CLASSIFIER
     * @since 2.7
     */
    public static final String TEST_JAVADOC_RESOURCES_ATTACHMENT_CLASSIFIER = "test-javadoc-resources";

    /**
     * The Javadoc script file name when <code>debug</code> parameter is on,
     * i.e. javadoc.bat or javadoc.sh
     */
    protected static final String DEBUG_JAVADOC_SCRIPT_NAME = "javadoc." + (SystemUtils.IS_OS_WINDOWS ? "bat" : "sh");

    /**
     * The <code>options</code> file name in the output directory when calling:
     * <code>javadoc.exe(or .sh) &#x40;options &#x40;packages | &#x40;argfile | &#x40;files</code>
     */
    protected static final String OPTIONS_FILE_NAME = "options";

    /**
     * The <code>packages</code> file name in the output directory when calling:
     * <code>javadoc.exe(or .sh) &#x40;options &#x40;packages | &#x40;argfile | &#x40;files</code>
     */
    protected static final String PACKAGES_FILE_NAME = "packages";

    /**
     * The <code>argfile</code> file name in the output directory when calling:
     * <code>javadoc.exe(or .sh) &#x40;options &#x40;packages | &#x40;argfile | &#x40;files</code>
     */
    protected static final String ARGFILE_FILE_NAME = "argfile";

    /**
     * The <code>files</code> file name in the output directory when calling:
     * <code>javadoc.exe(or .sh) &#x40;options &#x40;packages | &#x40;argfile | &#x40;files</code>
     */
    protected static final String FILES_FILE_NAME = "files";

    /**
     * The current class directory
     */
    private static final String RESOURCE_DIR = ClassUtils.getPackageName(JavadocReport.class).replace('.', '/');

    /**
     * Default css file name
     */
    private static final String DEFAULT_CSS_NAME = "stylesheet.css";

    /**
     * Default location for css
     */
    private static final String RESOURCE_CSS_DIR = RESOURCE_DIR + "/css";

    private static final String PACKAGE_LIST = "package-list";
    private static final String ELEMENT_LIST = "element-list";

    /**
     * For Javadoc options appears since Java 1.4. See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.1.html#summary">
     * What's New in Javadoc 1.4</a>
     *
     * @since 2.1
     */
    private static final JavaVersion SINCE_JAVADOC_1_4 = JavaVersion.parse("1.4");

    /**
     * For Javadoc options appears since Java 1.4.2. See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.2.html#commandlineoptions">
     * What's New in Javadoc 1.4.2</a>
     *
     * @since 2.1
     */
    private static final JavaVersion SINCE_JAVADOC_1_4_2 = JavaVersion.parse("1.4.2");

    /**
     * For Javadoc options appears since Java 5.0. See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.5.0.html#commandlineoptions">
     * What's New in Javadoc 5.0</a>
     *
     * @since 2.1
     */
    private static final JavaVersion SINCE_JAVADOC_1_5 = JavaVersion.parse("1.5");

    /**
     * For Javadoc options appears since Java 6.0. See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/index.html">
     * Javadoc Technology</a>
     *
     * @since 2.4
     */
    private static final JavaVersion SINCE_JAVADOC_1_6 = JavaVersion.parse("1.6");

    /**
     * For Javadoc options appears since Java 8.0. See <a href=
     * "http://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/index.html">
     * Javadoc Technology</a>
     *
     * @since 3.0.0
     */
    private static final JavaVersion SINCE_JAVADOC_1_8 = JavaVersion.parse("1.8");

    /**
     *
     */
    private static final JavaVersion JAVA_VERSION = JavaVersion.JAVA_SPECIFICATION_VERSION;

    // ----------------------------------------------------------------------
    // Mojo components
    // ----------------------------------------------------------------------
    
    /**
     * The Maven Project Object
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;
    
    /**
     * The current build session instance. This is used for toolchain manager
     * API calls.
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    /**
     * Specifies whether the Javadoc generation should be skipped.
     *
     * @since 2.5
     */
    @Parameter(property = "maven.javadoc.skip", defaultValue = "false")
    protected boolean skip;

    /**
     * Specifies if the build will fail if there are errors during javadoc
     * execution or not.
     *
     * @since 2.5
     */
    @Parameter(property = "maven.javadoc.failOnError", defaultValue = "true")
    protected boolean failOnError;

    /**
     * Specifies if the build will fail if there are warning during javadoc
     * execution or not.
     *
     * @since 3.0.1
     */
    @Parameter(property = "maven.javadoc.failOnWarnings", defaultValue = "false")
    protected boolean failOnWarnings;

    /**
     * Specifies to use the <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#standard">
     * options provided by the Standard Doclet</a> for a custom doclet. <br>
     * Example:
     * 
     * <pre>
     * &lt;docletArtifacts&gt;
     *   &lt;docletArtifact&gt;
     *     &lt;groupId&gt;com.sun.tools.doclets&lt;/groupId&gt;
     *     &lt;artifactId&gt;doccheck&lt;/artifactId&gt;
     *     &lt;version&gt;1.2b2&lt;/version&gt;
     *   &lt;/docletArtifact&gt;
     * &lt;/docletArtifacts&gt;
     * &lt;useStandardDocletOptions&gt;true&lt;/useStandardDocletOptions&gt;
     * </pre>
     *
     * @since 2.5
     */
    @Parameter(property = "useStandardDocletOptions", defaultValue = "true")
    protected boolean useStandardDocletOptions;
    
    /**
     * Creates links to existing javadoc-generated documentation of external
     * referenced classes. <br>
     * <b>Notes</b>:
     * <ol>
     * <li>only used if {@link #isOffline} is set to <code>false</code>.</li>
     * <li>all given links should have a fetchable <code>/package-list</code>
     * file. For instance:
     * 
     * <pre>
     * &lt;links&gt;
     *   &lt;link&gt;http://docs.oracle.com/javase/1.4.2/docs/api&lt;/link&gt;
     * &lt;links&gt;
     * </pre>
     * 
     * will be used because
     * <code>http://docs.oracle.com/javase/1.4.2/docs/api/package-list</code>
     * exists.</li>
     * <li>if {@link #detectLinks} is defined, the links between the project
     * dependencies are automatically added.</li>
     * <li>if {@link #detectJavaApiLink} is defined, a Java API link, based on
     * the Java version of the project's sources, will be added
     * automatically.</li>
     * </ol>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#link">link</a>.
     *
     * @see #detectLinks
     * @see #detectJavaApiLink
     */
    @Parameter(property = "links")
    protected ArrayList<String> links;
    
    /**
     * Specifies the destination directory where javadoc saves the generated
     * HTML files. <br>
     * 
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#d">javadoc
     *      d</a>
     */
    @Parameter(property = "destDir", alias = "destDir", defaultValue = "${project.build.directory}/apidocs", required = true)
    protected File outputDirectory;
    
    final LocationManager locationManager = new LocationManager();
    
    /**
     * Archiver manager
     *
     * @since 2.5
     */
    @Component
    private ArchiverManager archiverManager;

    @Component
    private ResourceResolver resourceResolver;

    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    @Component
    private DependencyResolver dependencyResolver;

    /**
     * Project builder
     *
     * @since 3.0
     */
    @Component
    private ProjectBuilder mavenProjectBuilder;

    /** */
    @Component
    private ToolchainManager toolchainManager;

    // ----------------------------------------------------------------------
    // Mojo parameters
    // ----------------------------------------------------------------------

    /**
     * The Maven Settings.
     *
     * @since 2.3
     */
    @Parameter(defaultValue = "${settings}", readonly = true, required = true)
    private Settings settings;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    private MojoExecution mojo;

    /**
     * Specify if the Javadoc should operate in offline mode.
     */
    @Parameter(defaultValue = "${settings.offline}", required = true, readonly = true)
    private boolean isOffline;

    /**
     * Specifies the Javadoc resources directory to be included in the Javadoc
     * (i.e. package.html, images...). <br/>
     * Could be used in addition of <code>docfilessubdirs</code> parameter.
     * <br/>
     * See <a href="#docfilessubdirs">docfilessubdirs</a>.
     *
     * @see #docfilessubdirs
     * @since 2.1
     */
    @Parameter(defaultValue = "${basedir}/src/main/javadoc")
    private File javadocDirectory;

    /**
     * Set an additional option(s) on the command line. All input will be passed
     * as-is to the {@code @options} file. You must take care of quoting and
     * escaping. Useful for a custom doclet.
     *
     * @since 3.0.0
     */
    @Parameter
    private String[] additionalOptions;

    /**
     * Set an additional Javadoc option(s) (i.e. JVM options) on the command
     * line. Example:
     * 
     * <pre>
     * &lt;additionalJOption&gt;-J-Xss128m&lt;/additionalJOption&gt;
     * </pre>
     * 
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#J">Jflag</a>.
     * <br/>
     * See <a href=
     * "http://java.sun.com/javase/technologies/hotspot/vmoptions.jsp">vmoptions</a>.
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/net/properties.html">Networking
     * Properties</a>.
     *
     * @since 2.3
     */
    @Parameter(property = "additionalJOption")
    private String additionalJOption;

    /**
     * Set additional JVM options for the execution of the javadoc command via
     * the '-J' option to javadoc. Example:
     * 
     * <pre>
     *     &lt;additionalJOptions&gt;
     *         &lt;additionalJOption&gt;-J-Xmx1g &lt;/additionalJOption&gt;
     *     &lt;/additionalJOptions&gt;
     * </pre>
     * 
     * @since 2.9
     */
    @Parameter
    private String[] additionalJOptions;

    /**
     * A list of artifacts containing resources which should be copied into the
     * Javadoc output directory (like stylesheets, icons, etc.). <br/>
     * Example:
     * 
     * <pre>
     * &lt;resourcesArtifacts&gt;
     *   &lt;resourcesArtifact&gt;
     *     &lt;groupId&gt;external.group.id&lt;/groupId&gt;
     *     &lt;artifactId&gt;external-resources&lt;/artifactId&gt;
     *     &lt;version&gt;1.0&lt;/version&gt;
     *   &lt;/resourcesArtifact&gt;
     * &lt;/resourcesArtifacts&gt;
     * </pre>
     * 
     * <br/>
     * See <a href=
     * "./apidocs/org/apache/maven/plugin/javadoc/options/ResourcesArtifact.html">Javadoc</a>.
     * <br/>
     *
     * @since 2.5
     */
    @Parameter(property = "resourcesArtifacts")
    private ResourcesArtifact[] resourcesArtifacts;

    /**
     * The local repository where the artifacts are located.
     */
    @Parameter(property = "localRepository")
    private ArtifactRepository localRepository;

    /**
     * The projects in the reactor for aggregation report.
     */
    @Parameter(property = "reactorProjects", readonly = true)
    private List<MavenProject> reactorProjects;

    /**
     * Set this to <code>true</code> to debug the Javadoc plugin. With this,
     * <code>javadoc.bat(or.sh)</code>, <code>options</code>,
     * <code>@packages</code> or <code>argfile</code> files are provided in the
     * output directory. <br/>
     *
     * @since 2.1
     */
    @Parameter(property = "debug", defaultValue = "false")
    private boolean debug;

    /**
     * Sets the absolute path of the Javadoc Tool executable to use. Since
     * version 2.5, a mere directory specification is sufficient to have the
     * plugin use "javadoc" or "javadoc.exe" respectively from this directory.
     *
     * @since 2.3
     */
    @Parameter(property = "javadocExecutable")
    private String javadocExecutable;

    /**
     * Version of the Javadoc Tool executable to use, ex. "1.3", "1.5".
     *
     * @since 2.3
     */
    @Parameter(property = "javadocVersion")
    private String javadocVersion;

    /**
     * Version of the Javadoc Tool executable to use.
     */
    private JavaVersion javadocRuntimeVersion;

    /**
     * Detect the Javadoc links for all dependencies defined in the project. The
     * detection is based on the default Maven conventions, i.e.:
     * <code>${project.url}/apidocs</code>. <br/>
     * For instance, if the project has a dependency to
     * <a href="http://commons.apache.org/lang/">Apache Commons Lang</a> i.e.:
     * 
     * <pre>
     * &lt;dependency&gt;
     *   &lt;groupId&gt;commons-lang&lt;/groupId&gt;
     *   &lt;artifactId&gt;commons-lang&lt;/artifactId&gt;
     * &lt;/dependency&gt;
     * </pre>
     * 
     * The added Javadoc <code>-link</code> parameter will be
     * <code>http://commons.apache.org/lang/apidocs</code>.
     *
     * @see #links
     * @since 2.6
     */
    @Parameter(property = "detectLinks", defaultValue = "false")
    private boolean detectLinks;

    /**
     * Detect the links for all modules defined in the project. <br/>
     * If {@link #reactorProjects} is defined in a non-aggregator way, it
     * generates default offline links between modules based on the defined
     * project's urls. For instance, if a parent project has two projects
     * <code>module1</code> and <code>module2</code>, the
     * <code>-linkoffline</code> will be: <br/>
     * The added Javadoc <code>-linkoffline</code> parameter for <b>module1</b>
     * will be
     * <code>/absolute/path/to/</code><b>module2</b><code>/target/site/apidocs</code>
     * <br/>
     * The added Javadoc <code>-linkoffline</code> parameter for <b>module2</b>
     * will be
     * <code>/absolute/path/to/</code><b>module1</b><code>/target/site/apidocs</code>
     *
     * @see #offlineLinks
     * @since 2.6
     */
    @Parameter(property = "detectOfflineLinks", defaultValue = "true")
    private boolean detectOfflineLinks;

    /**
     * Detect the Java API link for the current build, i.e.
     * <code>http://docs.oracle.com/javase/1.4.2/docs/api/</code> for Java
     * source 1.4. <br/>
     * By default, the goal detects the Javadoc API link depending the value of
     * the <code>source</code> parameter in the
     * <code>org.apache.maven.plugins:maven-compiler-plugin</code> (defined in
     * <code>${project.build.plugins}</code> or in
     * <code>${project.build.pluginManagement}</code>), or try to compute it
     * from the {@link #javadocExecutable} version. <br/>
     * See <a href=
     * "./apidocs/org/apache/maven/plugin/javadoc/AbstractJavadocMojo.html#DEFAULT_JAVA_API_LINKS">Javadoc</a>
     * for the default values. <br/>
     *
     * @see #links
     * @see #javaApiLinks
     * @see #DEFAULT_JAVA_API_LINKS
     * @since 2.6
     */
    @Parameter(property = "detectJavaApiLink", defaultValue = "true")
    private boolean detectJavaApiLink;

    /**
     * Use this parameter <b>only</b> if if you want to override the default
     * URLs. The key should match {@code api_x}, where {@code x} matches the
     * Java version. For example:
     * <dl>
     * <dt>api_1.5</dt>
     * <dd>https://docs.oracle.com/javase/1.5.0/docs/api/</dd>
     * <dt>api_1.8
     * <dt>
     * <dd>https://docs.oracle.com/javase/8/docs/api/</dd>
     * <dt>api_9</dd>
     * <dd>https://docs.oracle.com/javase/9/docs/api/</dd>
     * </dl>
     * 
     * @since 2.6
     */
    @Parameter(property = "javaApiLinks")
    private Properties javaApiLinks;

    /**
     * Flag controlling content validation of <code>package-list</code>
     * resources. If set, the content of <code>package-list</code> resources
     * will be validated.
     *
     * @since 2.8
     */
    @Parameter(property = "validateLinks", defaultValue = "false")
    private boolean validateLinks;

    // ----------------------------------------------------------------------
    // Javadoc Options - all alphabetical
    // ----------------------------------------------------------------------

    /**
     * Specifies the paths where the boot classes reside. The
     * <code>bootclasspath</code> can contain multiple paths by separating them
     * with a colon (<code>:</code>) or a semi-colon (<code>;</code>). <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#bootclasspath">bootclasspath</a>.
     * <br/>
     *
     * @since 2.5
     */
    @Parameter(property = "bootclasspath")
    private String bootclasspath;

    /**
     * Specifies the artifacts where the boot classes reside. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#bootclasspath">bootclasspath</a>.
     * <br/>
     * Example:
     * 
     * <pre>
     * &lt;bootclasspathArtifacts&gt;
     *   &lt;bootclasspathArtifact&gt;
     *     &lt;groupId&gt;my-groupId&lt;/groupId&gt;
     *     &lt;artifactId&gt;my-artifactId&lt;/artifactId&gt;
     *     &lt;version&gt;my-version&lt;/version&gt;
     *   &lt;/bootclasspathArtifact&gt;
     * &lt;/bootclasspathArtifacts&gt;
     * </pre>
     * 
     * <br/>
     * See <a href=
     * "./apidocs/org/apache/maven/plugin/javadoc/options/BootclasspathArtifact.html">Javadoc</a>.
     * <br/>
     *
     * @since 2.5
     */
    @Parameter(property = "bootclasspathArtifacts")
    private BootclasspathArtifact[] bootclasspathArtifacts;

    /**
     * Uses the sentence break iterator to determine the end of the first
     * sentence. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#breakiterator">breakiterator</a>.
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#summary">Java
     * 1.4</a>. <br/>
     */
    @Parameter(property = "breakiterator", defaultValue = "false")
    private boolean breakiterator;

    /**
     * Specifies the class file that starts the doclet used in generating the
     * documentation. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#doclet">doclet</a>.
     */
    @Parameter(property = "doclet")
    private String doclet;

    /**
     * Specifies the artifact containing the doclet starting class file
     * (specified with the <code>-doclet</code> option). <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#docletpath">docletpath</a>.
     * <br/>
     * Example:
     * 
     * <pre>
     * &lt;docletArtifact&gt;
     *   &lt;groupId&gt;com.sun.tools.doclets&lt;/groupId&gt;
     *   &lt;artifactId&gt;doccheck&lt;/artifactId&gt;
     *   &lt;version&gt;1.2b2&lt;/version&gt;
     * &lt;/docletArtifact&gt;
     * </pre>
     * 
     * <br/>
     * See <a href=
     * "./apidocs/org/apache/maven/plugin/javadoc/options/DocletArtifact.html">Javadoc</a>.
     * <br/>
     */
    @Parameter(property = "docletArtifact")
    private DocletArtifact docletArtifact;

    /**
     * Specifies multiple artifacts containing the path for the doclet starting
     * class file (specified with the <code>-doclet</code> option). <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#docletpath">docletpath</a>.
     * <br/>
     * Example:
     * 
     * <pre>
     * &lt;docletArtifacts&gt;
     *   &lt;docletArtifact&gt;
     *     &lt;groupId&gt;com.sun.tools.doclets&lt;/groupId&gt;
     *     &lt;artifactId&gt;doccheck&lt;/artifactId&gt;
     *     &lt;version&gt;1.2b2&lt;/version&gt;
     *   &lt;/docletArtifact&gt;
     * &lt;/docletArtifacts&gt;
     * </pre>
     * 
     * <br/>
     * See <a href=
     * "./apidocs/org/apache/maven/plugin/javadoc/options/DocletArtifact.html">Javadoc</a>.
     * <br/>
     *
     * @since 2.1
     */
    @Parameter(property = "docletArtifacts")
    private DocletArtifact[] docletArtifacts;

    /**
     * Specifies the path to the doclet starting class file (specified with the
     * <code>-doclet</code> option) and any jar files it depends on. The
     * <code>docletPath</code> can contain multiple paths by separating them
     * with a colon (<code>:</code>) or a semi-colon (<code>;</code>). <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#docletpath">docletpath</a>.
     */
    @Parameter(property = "docletPath")
    private String docletPath;

    /**
     * Specifies the encoding name of the source files. If not specificed, the
     * encoding value will be the value of the <code>file.encoding</code> system
     * property. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#encoding">encoding</a>.
     * <br/>
     * <b>Note</b>: In 2.4, the default value was locked to
     * <code>ISO-8859-1</code> to ensure reproducing build, but this was
     * reverted in 2.5. <br/>
     */
    @Parameter(property = "encoding", defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    /**
     * Unconditionally excludes the specified packages and their subpackages
     * from the list formed by <code>-subpackages</code>. Multiple packages can
     * be separated by commas (<code>,</code>), colons (<code>:</code>) or
     * semicolons (<code>;</code>).
     * <p>
     * Wildcards work as followed:
     * <ul>
     * <li>a wildcard at the beginning should match 1 or more folders</li>
     * <li>any other wildcard must match exactly one folder</li>
     * </ul>
     * </p>
     * <p>
     * Example:
     * 
     * <pre>
     * &lt;excludePackageNames&gt;*.internal:org.acme.exclude1.*:org.acme.exclude2&lt;/excludePackageNames&gt;
     * </pre>
     * 
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#exclude">exclude</a>.
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#summary">Java
     * 1.4</a>.
     * </p>
     */
    @Parameter(property = "excludePackageNames")
    private String excludePackageNames;

    /**
     * Specifies the directories where extension classes reside. Separate
     * directories in <code>extdirs</code> with a colon (<code>:</code>) or a
     * semi-colon (<code>;</code>). <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#extdirs">extdirs</a>.
     */
    @Parameter(property = "extdirs")
    private String extdirs;

    /**
     * Specifies the locale that javadoc uses when generating documentation.
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#locale">locale</a>.
     */
    @Parameter(property = "locale")
    private String locale;

    /**
     * Specifies the maximum Java heap size to be used when launching the
     * Javadoc tool. JVMs refer to this property as the <code>-Xmx</code>
     * parameter. Example: '512' or '512m'. The memory unit depends on the JVM
     * used. The units supported could be: <code>k</code>, <code>kb</code>,
     * <code>m</code>, <code>mb</code>, <code>g</code>, <code>gb</code>,
     * <code>t</code>, <code>tb</code>. If no unit specified, the default unit
     * is <code>m</code>.
     */
    @Parameter(property = "maxmemory")
    private String maxmemory;

    /**
     * Specifies the minimum Java heap size to be used when launching the
     * Javadoc tool. JVMs refer to this property as the <code>-Xms</code>
     * parameter. Example: '512' or '512m'. The memory unit depends on the JVM
     * used. The units supported could be: <code>k</code>, <code>kb</code>,
     * <code>m</code>, <code>mb</code>, <code>g</code>, <code>gb</code>,
     * <code>t</code>, <code>tb</code>. If no unit specified, the default unit
     * is <code>m</code>.
     */
    @Parameter(property = "minmemory")
    private String minmemory;

    /**
     * This option creates documentation with the appearance and functionality
     * of documentation generated by Javadoc 1.1. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#1.1">1.1</a>.
     * <br/>
     */
    @Parameter(property = "old", defaultValue = "false")
    private boolean old;

    /**
     * Specifies that javadoc should retrieve the text for the overview
     * documentation from the "source" file specified by path/filename and place
     * it on the Overview page (overview-summary.html). <br/>
     * <b>Note</b>: could be in conflict with &lt;nooverview/&gt;. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#overview">overview</a>.
     * <br/>
     */
    @Parameter(property = "overview", defaultValue = "${basedir}/src/main/javadoc/overview.html")
    private File overview;

    /**
     * Shuts off non-error and non-warning messages, leaving only the warnings
     * and errors appear, making them easier to view. <br/>
     * Note: was a standard doclet in Java 1.4.2 (refer to bug ID <a href=
     * "http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4714350">4714350</a>).
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#quiet">quiet</a>.
     * <br/>
     * Since Java 5.0. <br/>
     */
    @Parameter(property = "quiet", defaultValue = "false")
    private boolean quiet;

    /**
     * Specifies the access level for classes and members to show in the
     * Javadocs. Possible values are:
     * <ul>
     * <li><a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#public">public</a>
     * (shows only public classes and members)</li>
     * <li><a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#protected">protected</a>
     * (shows only public and protected classes and members)</li>
     * <li><a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#package">package</a>
     * (shows all classes and members not marked private)</li>
     * <li><a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#private">private</a>
     * (shows all classes and members)</li>
     * </ul>
     * <br/>
     */
    @Parameter(property = "show", defaultValue = "protected")
    private String show;

    /**
     * Necessary to enable javadoc to handle assertions introduced in J2SE v 1.4
     * source code or generics introduced in J2SE v5. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#source">source</a>.
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#summary">Java
     * 1.4</a>.
     */
    @Parameter(property = "source")
    private String source;

    /**
     * Provide source compatibility with specified release
     *
     * @since JDK 9
     * @since 3.1.0
     */
    @Parameter(defaultValue = "${maven.compiler.release}")
    private String release;

    /**
     * Specifies the source paths where the subpackages are located. The
     * <code>sourcepath</code> can contain multiple paths by separating them
     * with a colon (<code>:</code>) or a semi-colon (<code>;</code>). <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#sourcepath">sourcepath</a>.
     */
    @Parameter(property = "sourcepath")
    private String sourcepath;

    /**
     * Specifies the package directory where javadoc will be executed. Multiple
     * packages can be separated by colons (<code>:</code>). <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#subpackages">subpackages</a>.
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#summary">Java
     * 1.4</a>.
     */
    @Parameter(property = "subpackages")
    private String subpackages;

    /**
     * Provides more detailed messages while javadoc is running. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#verbose">verbose</a>.
     * <br/>
     */
    @Parameter(property = "verbose", defaultValue = "false")
    private boolean verbose;

    // ----------------------------------------------------------------------
    // Standard Doclet Options - all alphabetical
    // ----------------------------------------------------------------------

    /**
     * Specifies whether or not the author text is included in the generated
     * Javadocs. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#author">author</a>.
     * <br/>
     */
    @Parameter(property = "author", defaultValue = "true")
    private boolean author;

    /**
     * Specifies the text to be placed at the bottom of each output file.<br/>
     * If you want to use html you have to put it in a CDATA section, <br/>
     * eg.
     * <code>&lt;![CDATA[Copyright 2005, &lt;a href="http://www.mycompany.com">MyCompany, Inc.&lt;a>]]&gt;</code>
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#bottom">bottom</a>.
     * <br/>
     */
    @Parameter(property = "bottom", defaultValue = "Copyright &#169; {inceptionYear}&#x2013;{currentYear} {organizationName}. " + "All rights reserved.")
    private String bottom;

    /**
     * Specifies the HTML character set for this document. If not specificed,
     * the charset value will be the value of the <code>docencoding</code>
     * parameter. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#charset">charset</a>.
     * <br/>
     */
    @Parameter(property = "charset")
    private String charset;

    /**
     * Specifies the encoding of the generated HTML files. If not specificed,
     * the docencoding value will be <code>UTF-8</code>. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#docencoding">docencoding</a>.
     */
    @Parameter(property = "docencoding", defaultValue = "${project.reporting.outputEncoding}")
    private String docencoding;

    /**
     * Enables deep copying of the <code>&#42;&#42;/doc-files</code> directories
     * and the specifc <code>resources</code> directory from the
     * <code>javadocDirectory</code> directory (for instance,
     * <code>src/main/javadoc/com/mycompany/myapp/doc-files</code> and
     * <code>src/main/javadoc/resources</code>). <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#docfilessubdirs">
     * docfilessubdirs</a>. <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#summary">Java
     * 1.4</a>. <br/>
     * See <a href="#javadocDirectory">javadocDirectory</a>. <br/>
     *
     * @see #excludedocfilessubdir
     * @see #javadocDirectory
     */
    @Parameter(property = "docfilessubdirs", defaultValue = "false")
    private boolean docfilessubdirs;

    /**
     * Specifies specific checks to be performed on Javadoc comments. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#BEJEFABE">doclint</a>.
     *
     * @since 3.0.0
     */
    @Parameter(property = "doclint")
    private String doclint;

    /**
     * Specifies the title to be placed near the top of the overview summary
     * file. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#doctitle">doctitle</a>.
     * <br/>
     */
    @Parameter(property = "doctitle", defaultValue = "${project.name} ${project.version} API")
    private String doctitle;

    /**
     * Excludes any "doc-files" subdirectories with the given names. Multiple
     * patterns can be excluded by separating them with colons (<code>:</code>).
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#excludedocfilessubdir">
     * excludedocfilessubdir</a>. <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#summary">Java
     * 1.4</a>.
     *
     * @see #docfilessubdirs
     */
    @Parameter(property = "excludedocfilessubdir")
    private String excludedocfilessubdir;

    /**
     * Specifies the footer text to be placed at the bottom of each output file.
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#footer">footer</a>.
     */
    @Parameter(property = "footer")
    private String footer;

    /**
     * Separates packages on the overview page into whatever groups you specify,
     * one group per table. The packages pattern can be any package name, or can
     * be the start of any package name followed by an asterisk (<code>*</code>)
     * meaning "match any characters". Multiple patterns can be included in a
     * group by separating them with colons (<code>:</code>). <br/>
     * Example:
     * 
     * <pre>
     * &lt;groups&gt;
     *   &lt;group&gt;
     *     &lt;title&gt;Core Packages&lt;/title&gt;
     *     &lt;!-- To includes java.lang, java.lang.ref,
     *     java.lang.reflect and only java.util
     *     (i.e. not java.util.jar) --&gt;
     *     &lt;packages&gt;java.lang*:java.util&lt;/packages&gt;
     *   &lt;/group&gt;
     *   &lt;group&gt;
     *     &lt;title&gt;Extension Packages&lt;/title&gt;
     *     &nbsp;&lt;!-- To include javax.accessibility,
     *     javax.crypto, ... (among others) --&gt;
     *     &lt;packages&gt;javax.*&lt;/packages&gt;
     *   &lt;/group&gt;
     * &lt;/groups&gt;
     * </pre>
     * 
     * <b>Note</b>: using <code>java.lang.*</code> for <code>packages</code>
     * would omit the <code>java.lang</code> package but using
     * <code>java.lang*</code> will include it. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#group">group</a>.
     * <br/>
     * See <a href=
     * "./apidocs/org/apache/maven/plugin/javadoc/options/Group.html">Javadoc</a>.
     * <br/>
     */
    @Parameter
    private Group[] groups;

    /**
     * Specifies the header text to be placed at the top of each output file.
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#header">header</a>.
     */
    @Parameter(property = "header")
    private String header;

    /**
     * Specifies the path of an alternate help file path\filename that the HELP
     * link in the top and bottom navigation bars link to. <br/>
     * <b>Note</b>: could be in conflict with &lt;nohelp/&gt;. <br/>
     * The <code>helpfile</code> could be an absolute File path. <br/>
     * Since 2.6, it could be also be a path from a resource in the current
     * project source directories (i.e. <code>src/main/java</code>,
     * <code>src/main/resources</code> or <code>src/main/javadoc</code>) or from
     * a resource in the Javadoc plugin dependencies, for instance:
     * 
     * <pre>
     * &lt;helpfile&gt;path/to/your/resource/yourhelp-doc.html&lt;/helpfile&gt;
     * </pre>
     * 
     * Where <code>path/to/your/resource/yourhelp-doc.html</code> could be in
     * <code>src/main/javadoc</code>.
     * 
     * <pre>
     * &lt;build&gt;
     *   &lt;plugins&gt;
     *     &lt;plugin&gt;
     *       &lt;groupId&gt;org.apache.maven.plugins&lt;/groupId&gt;
     *       &lt;artifactId&gt;maven-javadoc-plugin&lt;/artifactId&gt;
     *       &lt;configuration&gt;
     *         &lt;helpfile&gt;path/to/your/resource/yourhelp-doc.html&lt;/helpfile&gt;
     *         ...
     *       &lt;/configuration&gt;
     *       &lt;dependencies&gt;
     *         &lt;dependency&gt;
     *           &lt;groupId&gt;groupId&lt;/groupId&gt;
     *           &lt;artifactId&gt;artifactId&lt;/artifactId&gt;
     *           &lt;version&gt;version&lt;/version&gt;
     *         &lt;/dependency&gt;
     *       &lt;/dependencies&gt;
     *     &lt;/plugin&gt;
     *     ...
     *   &lt;plugins&gt;
     * &lt;/build&gt;
     * </pre>
     * 
     * Where <code>path/to/your/resource/yourhelp-doc.html</code> is defined in
     * the <code>groupId:artifactId:version</code> javadoc plugin dependency.
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#helpfile">helpfile</a>.
     */
    @Parameter(property = "helpfile")
    private String helpfile;

    /**
     * Adds HTML meta keyword tags to the generated file for each class. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#keywords">keywords</a>.
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.2.html#commandlineoptions">
     * Java 1.4.2</a>. <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.5.0.html#commandlineoptions">
     * Java 5.0</a>. <br/>
     *
     * @since 2.1
     */
    @Parameter(property = "keywords", defaultValue = "false")
    private boolean keywords;

    /**
     * Creates an HTML version of each source file (with line numbers) and adds
     * links to them from the standard HTML documentation. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#linksource">linksource</a>.
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#summary">Java
     * 1.4</a>. <br/>
     */
    @Parameter(property = "linksource", defaultValue = "false")
    private boolean linksource;

    /**
     * Suppress the entire comment body, including the main description and all
     * tags, generating only declarations. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#nocomment">nocomment</a>.
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#summary">Java
     * 1.4</a>. <br/>
     */
    @Parameter(property = "nocomment", defaultValue = "false")
    private boolean nocomment;

    /**
     * Prevents the generation of any deprecated API at all in the
     * documentation. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#nodeprecated">nodeprecated</a>.
     * <br/>
     */
    @Parameter(property = "nodeprecated", defaultValue = "false")
    private boolean nodeprecated;

    /**
     * Prevents the generation of the file containing the list of deprecated
     * APIs (deprecated-list.html) and the link in the navigation bar to that
     * page. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#nodeprecatedlist">
     * nodeprecatedlist</a>. <br/>
     */
    @Parameter(property = "nodeprecatedlist", defaultValue = "false")
    private boolean nodeprecatedlist;

    /**
     * Omits the HELP link in the navigation bars at the top and bottom of each
     * page of output. <br/>
     * <b>Note</b>: could be in conflict with &lt;helpfile/&gt;. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#nohelp">nohelp</a>.
     * <br/>
     */
    @Parameter(property = "nohelp", defaultValue = "false")
    private boolean nohelp;

    /**
     * Omits the index from the generated docs. <br/>
     * <b>Note</b>: could be in conflict with &lt;splitindex/&gt;. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#noindex">noindex</a>.
     * <br/>
     */
    @Parameter(property = "noindex", defaultValue = "false")
    private boolean noindex;

    /**
     * Omits the navigation bar from the generated docs. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#nonavbar">nonavbar</a>.
     * <br/>
     */
    @Parameter(property = "nonavbar", defaultValue = "false")
    private boolean nonavbar;

    /**
     * Omits the entire overview page from the generated docs. <br/>
     * <b>Note</b>: could be in conflict with &lt;overview/&gt;. <br/>
     * Standard Doclet undocumented option. <br/>
     *
     * @since 2.4
     */
    @Parameter(property = "nooverview", defaultValue = "false")
    private boolean nooverview;

    /**
     * Omits qualifying package name from ahead of class names in output.
     * Example:
     * 
     * <pre>
     * &lt;noqualifier&gt;all&lt;/noqualifier&gt;
     * or
     * &lt;noqualifier&gt;packagename1:packagename2&lt;/noqualifier&gt;
     * </pre>
     * 
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#noqualifier">noqualifier</a>.
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#summary">Java
     * 1.4</a>.
     */
    @Parameter(property = "noqualifier")
    private String noqualifier;

    /**
     * Omits from the generated docs the "Since" sections associated with the
     * since tags. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#nosince">nosince</a>.
     * <br/>
     */
    @Parameter(property = "nosince", defaultValue = "false")
    private boolean nosince;

    /**
     * Suppresses the timestamp, which is hidden in an HTML comment in the
     * generated HTML near the top of each page. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#notimestamp">notimestamp</a>.
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.5.0.html#commandlineoptions">
     * Java 5.0</a>. <br/>
     *
     * @since 2.1
     */
    @Parameter(property = "notimestamp", defaultValue = "false")
    private boolean notimestamp;

    /**
     * Omits the class/interface hierarchy pages from the generated docs. <br>
     * 
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#notree">notree</a>
     *      option
     */
    @Parameter(property = "notree", defaultValue = "false")
    private boolean notree;

    /**
     * This option is a variation of <code>-link</code>; they both create links
     * to javadoc-generated documentation for external referenced classes. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#linkoffline">linkoffline</a>.
     * <br/>
     * Example:
     * 
     * <pre>
     * &lt;offlineLinks&gt;
     *   &lt;offlineLink&gt;
     *     &lt;url&gt;http://docs.oracle.com/javase/1.5.0/docs/api/&lt;/url&gt;
     *     &lt;location&gt;../javadoc/jdk-5.0/&lt;/location&gt;
     *   &lt;/offlineLink&gt;
     * &lt;/offlineLinks&gt;
     * </pre>
     * 
     * <br/>
     * <b>Note</b>: if {@link #detectOfflineLinks} is defined, the offline links
     * between the project modules are automatically added if the goal is
     * calling in a non-aggregator way. <br>
     * 
     * @see <a href=
     *      "./apidocs/org/apache/maven/plugin/javadoc/options/OfflineLink.html">Javadoc</a>.
     */
    @Parameter(property = "offlineLinks")
    private OfflineLink[] offlineLinks;

    /**
     * Specify the text for upper left frame. <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.2.html#commandlineoptions">
     * Java 1.4.2</a>.
     *
     * @since 2.1
     */
    @Parameter(property = "packagesheader")
    private String packagesheader;

    /**
     * Generates compile-time warnings for missing serial tags. <br/>
     * 
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#serialwarn">serialwarn</a>
     *      option
     */
    @Parameter(property = "serialwarn", defaultValue = "false")
    private boolean serialwarn;

    /**
     * Specify the number of spaces each tab takes up in the source. If no tab
     * is used in source, the default space is used. <br/>
     * Note: was <code>linksourcetab</code> in Java 1.4.2 (refer to bug ID
     * <a href=
     * "http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4788919">4788919</a>).
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.2.html#commandlineoptions">
     * 1.4.2</a>. <br/>
     * Since Java 5.0.
     *
     * @since 2.1
     */
    @Parameter(property = "sourcetab", alias = "linksourcetab")
    private int sourcetab;

    /**
     * Splits the index file into multiple files, alphabetically, one file per
     * letter, plus a file for any index entries that start with
     * non-alphabetical characters. <br/>
     * <b>Note</b>: could be in conflict with &lt;noindex/&gt;. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#splitindex">splitindex</a>.
     * <br/>
     */
    @Parameter(property = "splitindex", defaultValue = "false")
    private boolean splitindex;

    /**
     * Specifies whether the stylesheet to be used is the <code>maven</code>'s
     * javadoc stylesheet or <code>java</code>'s default stylesheet when a
     * <i>stylesheetfile</i> parameter is not specified. <br/>
     * Possible values: <code>maven<code> or <code>java</code>. <br/>
     */
    @Parameter(property = "stylesheet", defaultValue = "java")
    private String stylesheet;

    /**
     * Specifies the path of an alternate HTML stylesheet file. <br/>
     * The <code>stylesheetfile</code> could be an absolute File path. <br/>
     * Since 2.6, it could be also be a path from a resource in the current
     * project source directories (i.e. <code>src/main/java</code>,
     * <code>src/main/resources</code> or <code>src/main/javadoc</code>) or from
     * a resource in the Javadoc plugin dependencies, for instance:
     * 
     * <pre>
     * &lt;stylesheetfile&gt;path/to/your/resource/yourstylesheet.css&lt;/stylesheetfile&gt;
     * </pre>
     * 
     * Where <code>path/to/your/resource/yourstylesheet.css</code> could be in
     * <code>src/main/javadoc</code>.
     * 
     * <pre>
     * &lt;build&gt;
     *   &lt;plugins&gt;
     *     &lt;plugin&gt;
     *       &lt;groupId&gt;org.apache.maven.plugins&lt;/groupId&gt;
     *       &lt;artifactId&gt;maven-javadoc-plugin&lt;/artifactId&gt;
     *       &lt;configuration&gt;
     *         &lt;stylesheetfile&gt;path/to/your/resource/yourstylesheet.css&lt;/stylesheetfile&gt;
     *         ...
     *       &lt;/configuration&gt;
     *       &lt;dependencies&gt;
     *         &lt;dependency&gt;
     *           &lt;groupId&gt;groupId&lt;/groupId&gt;
     *           &lt;artifactId&gt;artifactId&lt;/artifactId&gt;
     *           &lt;version&gt;version&lt;/version&gt;
     *         &lt;/dependency&gt;
     *       &lt;/dependencies&gt;
     *     &lt;/plugin&gt;
     *     ...
     *   &lt;plugins&gt;
     * &lt;/build&gt;
     * </pre>
     * 
     * Where <code>path/to/your/resource/yourstylesheet.css</code> is defined in
     * the <code>groupId:artifactId:version</code> javadoc plugin dependency.
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#stylesheetfile">
     * stylesheetfile</a>.
     */
    @Parameter(property = "stylesheetfile")
    private String stylesheetfile;

    /**
     * Specifies the class file that starts the taglet used in generating the
     * documentation for that tag. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#taglet">taglet</a>.
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#summary">Java
     * 1.4</a>.
     */
    @Parameter(property = "taglet")
    private String taglet;

    /**
     * Specifies the Taglet artifact containing the taglet class files (.class).
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#tagletpath">tagletpath</a>.
     * <br/>
     * Example:
     * 
     * <pre>
     * &lt;taglets&gt;
     *   &lt;taglet&gt;
     *     &lt;tagletClass&gt;com.sun.tools.doclets.ToDoTaglet&lt;/tagletClass&gt;
     *   &lt;/taglet&gt;
     *   &lt;taglet&gt;
     *     &lt;tagletClass&gt;package.to.AnotherTagletClass&lt;/tagletClass&gt;
     *   &lt;/taglet&gt;
     *   ...
     * &lt;/taglets&gt;
     * &lt;tagletArtifact&gt;
     *   &lt;groupId&gt;group-Taglet&lt;/groupId&gt;
     *   &lt;artifactId&gt;artifact-Taglet&lt;/artifactId&gt;
     *   &lt;version&gt;version-Taglet&lt;/version&gt;
     * &lt;/tagletArtifact&gt;
     * </pre>
     * 
     * <br/>
     * See <a href=
     * "./apidocs/org/apache/maven/plugin/javadoc/options/TagletArtifact.html">Javadoc</a>.
     * <br/>
     *
     * @since 2.1
     */
    @Parameter(property = "tagletArtifact")
    private TagletArtifact tagletArtifact;

    /**
     * Specifies several Taglet artifacts containing the taglet class files
     * (.class). These taglets class names will be auto-detect and so no need to
     * specify them. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#taglet">taglet</a>.
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#tagletpath">tagletpath</a>.
     * <br/>
     * Example:
     * 
     * <pre>
     * &lt;tagletArtifacts&gt;
     *   &lt;tagletArtifact&gt;
     *     &lt;groupId&gt;group-Taglet&lt;/groupId&gt;
     *     &lt;artifactId&gt;artifact-Taglet&lt;/artifactId&gt;
     *     &lt;version&gt;version-Taglet&lt;/version&gt;
     *   &lt;/tagletArtifact&gt;
     *   ...
     * &lt;/tagletArtifacts&gt;
     * </pre>
     * 
     * <br/>
     * See <a href=
     * "./apidocs/org/apache/maven/plugin/javadoc/options/TagletArtifact.html">Javadoc</a>.
     * <br/>
     *
     * @since 2.5
     */
    @Parameter(property = "tagletArtifacts")
    private TagletArtifact[] tagletArtifacts;

    /**
     * Specifies the search paths for finding taglet class files (.class). The
     * <code>tagletpath</code> can contain multiple paths by separating them
     * with a colon (<code>:</code>) or a semi-colon (<code>;</code>). <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#tagletpath">tagletpath</a>.
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#summary">Java
     * 1.4</a>.
     */
    @Parameter(property = "tagletpath")
    private String tagletpath;

    /**
     * Enables the Javadoc tool to interpret multiple taglets. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#taglet">taglet</a>.
     * <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#tagletpath">tagletpath</a>.
     * <br/>
     * Example:
     * 
     * <pre>
     * &lt;taglets&gt;
     *   &lt;taglet&gt;
     *     &lt;tagletClass&gt;com.sun.tools.doclets.ToDoTaglet&lt;/tagletClass&gt;
     *     &lt;!--&lt;tagletpath&gt;/home/taglets&lt;/tagletpath&gt;--&gt;
     *     &lt;tagletArtifact&gt;
     *       &lt;groupId&gt;group-Taglet&lt;/groupId&gt;
     *       &lt;artifactId&gt;artifact-Taglet&lt;/artifactId&gt;
     *       &lt;version&gt;version-Taglet&lt;/version&gt;
     *     &lt;/tagletArtifact&gt;
     *   &lt;/taglet&gt;
     * &lt;/taglets&gt;
     * </pre>
     * 
     * <br/>
     * See <a href=
     * "./apidocs/org/apache/maven/plugin/javadoc/options/Taglet.html">Javadoc</a>.
     * <br/>
     *
     * @since 2.1
     */
    @Parameter(property = "taglets")
    private Taglet[] taglets;

    /**
     * Enables the Javadoc tool to interpret a simple, one-argument custom block
     * tag tagname in doc comments. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#tag">tag</a>.
     * <br/>
     * Since <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#summary">Java
     * 1.4</a>. <br/>
     * Example:
     * 
     * <pre>
     * &lt;tags&gt;
     *   &lt;tag&gt;
     *     &lt;name&gt;todo&lt;/name&gt;
     *     &lt;placement&gt;a&lt;/placement&gt;
     *     &lt;head&gt;To Do:&lt;/head&gt;
     *   &lt;/tag&gt;
     * &lt;/tags&gt;
     * </pre>
     * 
     * <b>Note</b>: the placement should be a combinaison of Xaoptcmf letters:
     * <ul>
     * <li><b><code>X</code></b> (disable tag)</li>
     * <li><b><code>a</code></b> (all)</li>
     * <li><b><code>o</code></b> (overview)</li>
     * <li><b><code>p</code></b> (packages)</li>
     * <li><b><code>t</code></b> (types, that is classes and interfaces)</li>
     * <li><b><code>c</code></b> (constructors)</li>
     * <li><b><code>m</code></b> (methods)</li>
     * <li><b><code>f</code></b> (fields)</li>
     * </ul>
     * See <a href=
     * "./apidocs/org/apache/maven/plugin/javadoc/options/Tag.html">Javadoc</a>.
     * <br/>
     */
    @Parameter(property = "tags")
    private Tag[] tags;

    /**
     * Specifies the top text to be placed at the top of each output file. <br/>
     * See <a href=
     * "http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6227616">6227616</a>.
     * <br/>
     * Since Java 6.0
     *
     * @since 2.4
     */
    @Parameter(property = "top")
    private String top;

    /**
     * Includes one "Use" page for each documented class and package. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#use">use</a>.
     * <br/>
     */
    @Parameter(property = "use", defaultValue = "true")
    private boolean use;

    /**
     * Includes the version text in the generated docs. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#version">version</a>.
     * <br/>
     */
    @Parameter(property = "version", defaultValue = "true")
    private boolean version;

    /**
     * Specifies the title to be placed in the HTML title tag. <br/>
     * See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#windowtitle">windowtitle</a>.
     * <br/>
     */
    @Parameter(property = "windowtitle", defaultValue = "${project.name} ${project.version} API")
    private String windowtitle;

    /**
     * Whether dependency -sources jars should be resolved and included as
     * source paths for javadoc generation. This is useful when creating
     * javadocs for a distribution project.
     *
     * @since 2.7
     */
    @Parameter(defaultValue = "false")
    private boolean includeDependencySources;

    /**
     * Directory where unpacked project sources / test-sources should be cached.
     *
     * @see #includeDependencySources
     * @since 2.7
     */
    @Parameter(defaultValue = "${project.build.directory}/distro-javadoc-sources")
    private File sourceDependencyCacheDir;

    /**
     * Whether to include transitive dependencies in the list of dependency
     * -sources jars to include in this javadoc run.
     *
     * @see #includeDependencySources
     * @since 2.7
     * @deprecated if these sources depend on transitive dependencies, those
     *             dependencies should be added to the pom as direct
     *             dependencies
     */
    @Parameter(defaultValue = "false")
    @Deprecated
    private boolean includeTransitiveDependencySources;

    /**
     * List of included dependency-source patterns. Example:
     * <code>org.apache.maven:*</code>
     *
     * @see #includeDependencySources
     * @since 2.7
     */
    @Parameter
    private List<String> dependencySourceIncludes;

    /**
     * List of excluded dependency-source patterns. Example:
     * <code>org.apache.maven.shared:*</code>
     *
     * @see #includeDependencySources
     * @since 2.7
     */
    @Parameter
    private List<String> dependencySourceExcludes;

    /**
     * Directory into which assembled {@link JavadocOptions} instances will be
     * written before they are added to javadoc resources bundles.
     *
     * @since 2.7
     */
    @Parameter(defaultValue = "${project.build.directory}/javadoc-bundle-options", readonly = true)
    private File javadocOptionsDir;

    /**
     * Transient variable to allow lazy-resolution of javadoc bundles from
     * dependencies, so they can be used at various points in the javadoc
     * generation process.
     *
     * @since 2.7
     */
    private transient List<JavadocBundle> dependencyJavadocBundles;

    /**
     * Capability to add additional dependencies to the javadoc classpath.
     * Example:
     * 
     * <pre>
     * &lt;additionalDependencies&gt;
     *   &lt;additionalDependency&gt;
     *     &lt;groupId&gt;geronimo-spec&lt;/groupId&gt;
     *     &lt;artifactId&gt;geronimo-spec-jta&lt;/artifactId&gt;
     *     &lt;version&gt;1.0.1B-rc4&lt;/version&gt;
     *   &lt;/additionalDependency&gt;
     * &lt;/additionalDependencies&gt;
     * </pre>
     *
     * @since 2.8.1
     */
    @Parameter
    private List<AdditionalDependency> additionalDependencies;

    /**
     * Include filters on the source files. Default is **\/\*.java. These are
     * ignored if you specify subpackages or subpackage excludes.
     *
     * @since 2.9
     */
    @Parameter
    private List<String> sourceFileIncludes;

    /**
     * exclude filters on the source files. These are ignored if you specify
     * subpackages or subpackage excludes.
     *
     * @since 2.9
     */
    @Parameter
    private List<String> sourceFileExcludes;

    /**
     * To apply the security fix on generated javadoc see
     * http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2013-1571
     * 
     * @since 2.9.1
     */
    @Parameter(defaultValue = "true", property = "maven.javadoc.applyJavadocSecurityFix")
    private boolean applyJavadocSecurityFix = true;

    /**
     * <p>
     * Specify the requirements for this jdk toolchain. This overrules the
     * toolchain selected by the maven-toolchain-plugin.
     * </p>
     * <strong>note:</strong> requires at least Maven 3.3.1
     *
     * @since 3.0.0
     */
    @Parameter
    private Map<String, String> jdkToolchain;

    /**
     * <p>
     * Location of the file used to store the state of the previous javadoc run.
     * This is used to skip the generation if nothing has changed.
     * </p>
     *
     * @since 3.2.0
     */
    @Parameter(property = "staleDataPath", defaultValue = "${project.build.directory}/maven-javadoc-plugin-stale-data.txt")
    private File staleDataPath;


    // ----------------------------------------------------------------------
    // protected methods
    // ----------------------------------------------------------------------

    /**
     * Indicates whether this goal is flagged with <code>@aggregator</code>.
     *
     * @return <code>true</code> if the goal is designed as an aggregator,
     *         <code>false</code> otherwise.
     * @see AggregatorJavadocReport
     * @see AggregatorTestJavadocReport
     */
    protected boolean isAggregator() {
        return false;
    }

    /**
     * Indicates whether this goal generates documentation for the
     * <code>Java Test code</code>.
     *
     * @return <code>true</code> if the goal generates Test Javadocs,
     *         <code>false</code> otherwise.
     */
    protected boolean isTest() {
        return false;
    }

    /**
     * @return the output directory
     */
    protected String getOutputDirectory() {
        return outputDirectory.getAbsoluteFile().toString();
    }

    protected MavenProject getProject() {
        return project;
    }

    /**
     * @param p not null maven project
     * @return the list of directories where compiled classes are placed for the
     *         given project. These dirs are added in the javadoc classpath.
     */
    protected List<File> getProjectBuildOutputDirs(MavenProject p) {
        if (StringUtils.isEmpty(p.getBuild().getOutputDirectory())) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new File(p.getBuild().getOutputDirectory()));
    }

    protected File getArtifactFile(MavenProject project) {
        if (!isAggregator() && isTest()) {
            return null;
        } else if (project.getArtifact() != null) {
            return project.getArtifact().getFile();
        }
        return null;
    }

    /**
     * @param p not null maven project
     * @return the list of source paths for the given project
     */
    protected List<String> getProjectSourceRoots(MavenProject p) {
        if ("pom".equals(p.getPackaging().toLowerCase())) {
            return Collections.emptyList();
        }

        return p.getCompileSourceRoots() == null ? Collections.<String> emptyList() : new LinkedList<>(p.getCompileSourceRoots());
    }

    /**
     * @param p not null maven project
     * @return the list of source paths for the execution project of the given
     *         project
     */
    protected List<String> getExecutionProjectSourceRoots(MavenProject p) {
        if ("pom".equals(p.getExecutionProject().getPackaging().toLowerCase())) {
            return Collections.emptyList();
        }

        return p.getExecutionProject().getCompileSourceRoots() == null ? Collections.<String> emptyList() : new LinkedList<>(p.getExecutionProject().getCompileSourceRoots());
    }

    /**
     * @return the current javadoc directory
     */
    protected File getJavadocDirectory() {
        return javadocDirectory;
    }

    /**
     * @return the doclint specific checks configuration
     */
    protected String getDoclint() {
        return doclint;
    }

    /**
     * @return the title to be placed near the top of the overview summary file
     */
    protected String getDoctitle() {
        return doctitle;
    }

    /**
     * @return the overview documentation file from the user parameter or from
     *         the <code>javadocdirectory</code>
     */
    protected File getOverview() {
        return overview;
    }

    /**
     * @return the title to be placed in the HTML title tag
     */
    protected String getWindowtitle() {
        return windowtitle;
    }

    /**
     * @return the charset attribute or the value of {@link #getDocencoding()}
     *         if <code>null</code>.
     */
    private String getCharset() {
        return (StringUtils.isEmpty(charset)) ? getDocencoding() : charset;
    }

    /**
     * @return the docencoding attribute or <code>UTF-8</code> if
     *         <code>null</code>.
     */
    private String getDocencoding() {
        return (StringUtils.isEmpty(docencoding)) ? ReaderFactory.UTF_8 : docencoding;
    }

    /**
     * @return the encoding attribute or the value of <code>file.encoding</code>
     *         system property if <code>null</code>.
     */
    private String getEncoding() {
        return (StringUtils.isEmpty(encoding)) ? ReaderFactory.FILE_ENCODING : encoding;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        verifyRemovedParameter("aggregator");
        verifyRemovedParameter("proxyHost");
        verifyRemovedParameter("proxyPort");
        verifyReplacedParameter("additionalparam", "additionalOptions");

        doExecute();
    }

    abstract void doExecute() throws MojoExecutionException, MojoFailureException;

    protected final void verifyRemovedParameter(String paramName) {
        Xpp3Dom configDom = mojo.getConfiguration();
        if (configDom != null) {
            if (configDom.getChild(paramName) != null) {
                throw new IllegalArgumentException("parameter '" + paramName + "' has been removed from the plugin, please verify documentation.");
            }
        }
    }

    private void verifyReplacedParameter(String oldParamName, String newParamNew) {
        Xpp3Dom configDom = mojo.getConfiguration();
        if (configDom != null) {
            if (configDom.getChild(oldParamName) != null) {
                throw new IllegalArgumentException("parameter '" + oldParamName + "' has been replaced with " + newParamNew + ", please verify documentation.");
            }
        }
    }

    /**
     * The <a href="package-summary.html">package documentation</a> details the
     * Javadoc Options used by this Plugin.
     *
     * @param unusedLocale the wanted locale (actually unused).
     * @throws MavenReportException if any
     */
    protected void executeReport(Locale unusedLocale) throws MavenReportException {
        if (skip) {
            getLog().info("Skipping javadoc generation");
            return;
        }

        if (getLog().isDebugEnabled()) {
            this.debug = true;
        }

        // NOTE: Always generate this file, to allow javadocs from modules to be
        // aggregated via
        // useDependencySources in a distro module build.
        try {
            buildJavadocOptions();
        } catch (IOException e) {
            throw new MavenReportException("Failed to generate javadoc options file: " + e.getMessage(), e);
        }

        Map<String, Collection<Path>> sourcePaths = getSourcePaths();

        Collection<Path> collectedSourcePaths = collect(sourcePaths.values());

        Map<Path, Collection<String>> files = getFiles(collectedSourcePaths);
        if (!canGenerateReport(files)) {
            return;
        }

        // ----------------------------------------------------------------------
        // Find the javadoc executable and version
        // ----------------------------------------------------------------------

        String jExecutable;
        try {
            jExecutable = getJavadocExecutable();
        } catch (IOException e) {
            throw new MavenReportException("Unable to find javadoc command: " + e.getMessage(), e);
        }
        setFJavadocVersion(new File(jExecutable));

        List<String> packageNames;
        if (javadocRuntimeVersion.isAtLeast("9")) {
            packageNames = getPackageNamesRespectingJavaModules(sourcePaths);
        } else {
            packageNames = getPackageNames(files);
        }

        // ----------------------------------------------------------------------
        // Javadoc output directory as File
        // ----------------------------------------------------------------------

        File javadocOutputDirectory = new File(getOutputDirectory());
        if (javadocOutputDirectory.exists() && !javadocOutputDirectory.isDirectory()) {
            throw new MavenReportException("IOException: " + getOutputDirectory() + " is not a directory.");
        }
        if (javadocOutputDirectory.exists() && !javadocOutputDirectory.canWrite()) {
            throw new MavenReportException("IOException: " + getOutputDirectory() + " is not writable.");
        }
        javadocOutputDirectory.mkdirs();

        // ----------------------------------------------------------------------
        // Copy all resources
        // ----------------------------------------------------------------------

        copyAllResources(javadocOutputDirectory);

        // ----------------------------------------------------------------------
        // Create command line for Javadoc
        // ----------------------------------------------------------------------

        Commandline cmd = new Commandline();
        cmd.getShell().setQuotedArgumentsEnabled(false); // for Javadoc JVM args
        cmd.setWorkingDirectory(javadocOutputDirectory.getAbsolutePath());
        cmd.setExecutable(jExecutable);

        // ----------------------------------------------------------------------
        // Wrap Javadoc JVM args
        // ----------------------------------------------------------------------

        addMemoryArg(cmd, "-Xmx", this.maxmemory);
        addMemoryArg(cmd, "-Xms", this.minmemory);
        addProxyArg(cmd);

        if (StringUtils.isNotEmpty(additionalJOption)) {
            cmd.createArg().setValue(additionalJOption);
        }

        if (additionalJOptions != null && additionalJOptions.length != 0) {
            for (String jo : additionalJOptions) {
                cmd.createArg().setValue(jo);
            }
        }

        // ----------------------------------------------------------------------
        // Wrap Standard doclet Options
        // ----------------------------------------------------------------------
        List<String> standardDocletArguments = new ArrayList<>();

        Set<OfflineLink> offlineLinks;
        if (StringUtils.isEmpty(doclet) || useStandardDocletOptions) {
            offlineLinks = getLinkofflines();
            addStandardDocletOptions(javadocOutputDirectory, standardDocletArguments, offlineLinks);
        } else {
            offlineLinks = Collections.emptySet();
        }

        // ----------------------------------------------------------------------
        // Wrap Javadoc options
        // ----------------------------------------------------------------------
        List<String> javadocArguments = new ArrayList<>();

        addJavadocOptions(javadocOutputDirectory, javadocArguments, sourcePaths, offlineLinks);

        // ----------------------------------------------------------------------
        // Write options file and include it in the command line
        // ----------------------------------------------------------------------

        List<String> arguments = new ArrayList<>(javadocArguments.size() + standardDocletArguments.size());
        arguments.addAll(javadocArguments);
        arguments.addAll(standardDocletArguments);

        if (arguments.size() > 0) {
            addCommandLineOptions(cmd, arguments, javadocOutputDirectory);
        }

        // ----------------------------------------------------------------------
        // Write packages file and include it in the command line
        // ----------------------------------------------------------------------

        // MJAVADOC-365 if includes/excludes are specified, these take
        // precedence over the default
        // package-based mode and force javadoc into file-based mode unless
        // subpackages are
        // specified. Subpackages take precedence over file-based
        // include/excludes. Why? Because
        // getFiles(...) returns an empty list when subpackages are specified.
        boolean includesExcludesActive = (sourceFileIncludes != null && !sourceFileIncludes.isEmpty()) || (sourceFileExcludes != null && !sourceFileExcludes.isEmpty());
        if (includesExcludesActive && !StringUtils.isEmpty(subpackages)) {
            getLog().warn("sourceFileIncludes and sourceFileExcludes have no effect when subpackages are specified!");
            includesExcludesActive = false;
        }
        if (!packageNames.isEmpty() && !includesExcludesActive) {
            addCommandLinePackages(cmd, javadocOutputDirectory, packageNames);

            // ----------------------------------------------------------------------
            // Write argfile file and include it in the command line
            // ----------------------------------------------------------------------

            List<String> specialFiles = getSpecialFiles(files);

            if (!specialFiles.isEmpty()) {
                addCommandLineArgFile(cmd, javadocOutputDirectory, specialFiles);
            }
        } else {
            // ----------------------------------------------------------------------
            // Write argfile file and include it in the command line
            // ----------------------------------------------------------------------

            List<String> allFiles = new ArrayList<>();
            for (Map.Entry<Path, Collection<String>> filesEntry : files.entrySet()) {
                for (String file : filesEntry.getValue()) {
                    allFiles.add(filesEntry.getKey().resolve(file).toString());
                }
            }

            if (!files.isEmpty()) {
                addCommandLineArgFile(cmd, javadocOutputDirectory, allFiles);
            }
        }

        // ----------------------------------------------------------------------
        // Execute command line
        // ----------------------------------------------------------------------

        executeJavadocCommandLine(cmd, javadocOutputDirectory);

        // delete generated javadoc files only if no error and no debug mode
        // [MJAVADOC-336] Use File.delete() instead of File.deleteOnExit() to
        // prevent these files from making their way into archives.
        if (!debug) {
            for (int i = 0; i < cmd.getArguments().length; i++) {
                String arg = cmd.getArguments()[i].trim();

                if (!arg.startsWith("@")) {
                    continue;
                }

                File argFile = new File(javadocOutputDirectory, arg.substring(1));
                if (argFile.exists()) {
                    argFile.delete();
                }
            }

            File scriptFile = new File(javadocOutputDirectory, DEBUG_JAVADOC_SCRIPT_NAME);
            if (scriptFile.exists()) {
                scriptFile.delete();
            }
        }
        if (applyJavadocSecurityFix) {
            // finally, patch the Javadoc vulnerability in older Javadoc tools
            // (CVE-2013-1571):
            try {
                final int patched = fixFrameInjectionBug(javadocOutputDirectory, getDocencoding());
                if (patched > 0) {
                    getLog().info(String.format("Fixed Javadoc frame injection vulnerability (CVE-2013-1571) in %d files.", patched));
                }
            } catch (IOException e) {
                throw new MavenReportException("Failed to patch javadocs vulnerability: " + e.getMessage(), e);
            }
        } else {
            getLog().info("applying javadoc security fix has been disabled");
        }
    }

    protected final <T> Collection<T> collect(Collection<Collection<T>> sourcePaths) {
        Collection<T> collectedSourcePaths = new LinkedHashSet<>();
        for (Collection<T> sp : sourcePaths) {
            collectedSourcePaths.addAll(sp);
        }
        return collectedSourcePaths;
    }

    /**
     * Method to get the files on the specified source paths
     *
     * @param sourcePaths a Collection that contains the paths to the source
     *            files
     * @return a List that contains the specific path for every source file
     * @throws MavenReportException {@link MavenReportException}
     */
    protected Map<Path, Collection<String>> getFiles(Collection<Path> sourcePaths) throws MavenReportException {
        Map<Path, Collection<String>> mappedFiles = new LinkedHashMap<>(sourcePaths.size());
        if (StringUtils.isEmpty(subpackages)) {
            Collection<String> excludedPackages = getExcludedPackages();

            for (Path sourcePath : sourcePaths) {
                List<String> files = new ArrayList<>();
                File sourceDirectory = sourcePath.toFile();
                files.addAll(JavadocUtil.getFilesFromSource(sourceDirectory, sourceFileIncludes, sourceFileExcludes, excludedPackages));

                if (source != null && JavaVersion.parse(source).isBefore("9") && files.remove("module-info.java")) {
                    getLog().debug("Auto exclude module-info.java due to source value");
                }
                mappedFiles.put(sourcePath, files);
            }
        }

        return mappedFiles;
    }

    /**
     * Method to get the source paths per reactorProject. If no source path is
     * specified in the parameter, the compile source roots of the project will
     * be used.
     *
     * @return a Map of the project absolute source paths per projects key (G:A)
     * @throws MavenReportException {@link MavenReportException}
     * @see JavadocUtil#pruneDirs(MavenProject, Collection)
     */
    protected Map<String, Collection<Path>> getSourcePaths() throws MavenReportException {
        Map<String, Collection<Path>> mappedSourcePaths = new LinkedHashMap<>();

        if (StringUtils.isEmpty(sourcepath)) {
            if (!"pom".equals(project.getPackaging())) {
                Set<Path> sourcePaths = new LinkedHashSet<>(JavadocUtil.pruneDirs(project, getProjectSourceRoots(project)));

                if (project.getExecutionProject() != null) {
                    sourcePaths.addAll(JavadocUtil.pruneDirs(project, getExecutionProjectSourceRoots(project)));
                }

                /*
                 * Should be after the source path (i.e. -sourcepath
                 * '.../src/main/java;.../src/main/javadoc') and *not* the
                 * opposite. If not, the javadoc tool always copies doc files,
                 * even if -docfilessubdirs is not setted.
                 */
                if (getJavadocDirectory() != null) {
                    File javadocDir = getJavadocDirectory();
                    if (javadocDir.exists() && javadocDir.isDirectory()) {
                        Collection<Path> l = JavadocUtil.pruneDirs(project, Collections.singletonList(getJavadocDirectory().getAbsolutePath()));
                        sourcePaths.addAll(l);
                    }
                }
                if (!sourcePaths.isEmpty()) {
                    mappedSourcePaths.put(ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId()), sourcePaths);
                }
            }

            if (includeDependencySources) {
                mappedSourcePaths.putAll(getDependencySourcePaths());
            }

            if (isAggregator()) {
                for (MavenProject subProject : getAggregatedProjects()) {
                    if (subProject != project) {
                        Collection<Path> additionalSourcePaths = new ArrayList<>();

                        List<String> sourceRoots = getProjectSourceRoots(subProject);

                        if (subProject.getExecutionProject() != null) {
                            sourceRoots.addAll(getExecutionProjectSourceRoots(subProject));
                        }

                        ArtifactHandler artifactHandler = subProject.getArtifact().getArtifactHandler();
                        if ("java".equals(artifactHandler.getLanguage())) {
                            additionalSourcePaths.addAll(JavadocUtil.pruneDirs(subProject, sourceRoots));
                        }

                        if (getJavadocDirectory() != null) {
                            String javadocDirRelative = PathUtils.toRelative(project.getBasedir(), getJavadocDirectory().getAbsolutePath());
                            File javadocDir = new File(subProject.getBasedir(), javadocDirRelative);
                            if (javadocDir.exists() && javadocDir.isDirectory()) {
                                Collection<Path> l = JavadocUtil.pruneDirs(subProject, Collections.singletonList(javadocDir.getAbsolutePath()));
                                additionalSourcePaths.addAll(l);
                            }
                        }
                        mappedSourcePaths.put(ArtifactUtils.versionlessKey(subProject.getGroupId(), subProject.getArtifactId()), additionalSourcePaths);
                    }
                }
            }
        } else {
            Collection<Path> sourcePaths = JavadocUtil.pruneDirs(project, new ArrayList<>(Arrays.asList(JavadocUtil.splitPath(sourcepath))));
            if (getJavadocDirectory() != null) {
                Collection<Path> l = JavadocUtil.pruneDirs(project, Collections.singletonList(getJavadocDirectory().getAbsolutePath()));
                sourcePaths.addAll(l);
            }
            mappedSourcePaths.put(ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId()), sourcePaths);
        }

        return mappedSourcePaths;
    }

    private Collection<MavenProject> getAggregatedProjects() {
        Map<Path, MavenProject> reactorProjectsMap = new HashMap<>();
        for (MavenProject reactorProject : this.reactorProjects) {
            reactorProjectsMap.put(reactorProject.getBasedir().toPath(), reactorProject);
        }

        return modulesForAggregatedProject(project, reactorProjectsMap);
    }

    /**
     * Recursively add the modules of the aggregatedProject to the set of
     * aggregatedModules.
     *
     * @param aggregatedProject the project being aggregated
     * @param reactorProjectsMap map of (still) available reactor projects
     * @throws MavenReportException if any
     */
    private Set<MavenProject> modulesForAggregatedProject(MavenProject aggregatedProject, Map<Path, MavenProject> reactorProjectsMap) {
        // Maven does not supply an easy way to get the projects representing
        // the modules of a project. So we will get the paths to the base
        // directories of the modules from the project and compare with the
        // base directories of the projects in the reactor.

        if (aggregatedProject.getModules().isEmpty()) {
            return Collections.singleton(aggregatedProject);
        }

        List<Path> modulePaths = new LinkedList<>();
        for (String module : aggregatedProject.getModules()) {
            modulePaths.add(new File(aggregatedProject.getBasedir(), module).toPath());
        }

        Set<MavenProject> aggregatedModules = new LinkedHashSet<>();

        for (Path modulePath : modulePaths) {
            MavenProject module = reactorProjectsMap.remove(modulePath);
            if (module != null) {
                aggregatedModules.addAll(modulesForAggregatedProject(module, reactorProjectsMap));
            }
        }

        return aggregatedModules;
    }

    /**
     * Override this method to customize the configuration for resolving
     * dependency sources. The default behavior enables the resolution of
     * -sources jar files.
     * 
     * @param config {@link SourceResolverConfig}
     * @return {@link SourceResolverConfig}
     */
    protected SourceResolverConfig configureDependencySourceResolution(final SourceResolverConfig config) {
        return config.withCompileSources();
    }

    /**
     * Resolve dependency sources so they can be included directly in the
     * javadoc process. To customize this, override
     * {@link AbstractJavadocMojo#configureDependencySourceResolution(SourceResolverConfig)}.
     * 
     * @return List of source paths.
     * @throws MavenReportException {@link MavenReportException}
     */
    protected final Map<String, Collection<Path>> getDependencySourcePaths() throws MavenReportException {
        try {
            if (sourceDependencyCacheDir.exists()) {
                FileUtils.forceDelete(sourceDependencyCacheDir);
                sourceDependencyCacheDir.mkdirs();
            }
        } catch (IOException e) {
            throw new MavenReportException("Failed to delete cache directory: " + sourceDependencyCacheDir + "\nReason: " + e.getMessage(), e);
        }

        final SourceResolverConfig config = getDependencySourceResolverConfig();

        try {
            return resourceResolver.resolveDependencySourcePaths(config);
        } catch (final ArtifactResolutionException | ArtifactNotFoundException e) {
            throw new MavenReportException("Failed to resolve one or more javadoc source/resource artifacts:\n\n" + e.getMessage(), e);
        }
    }

    /**
     * Returns a ArtifactFilter that only includes direct dependencies of this
     * project (verified via groupId and artifactId).
     *
     * @return
     */
    private TransformableFilter createDependencyArtifactFilter() {
        Set<Artifact> dependencyArtifacts = project.getDependencyArtifacts();

        List<String> artifactPatterns = new ArrayList<>(dependencyArtifacts.size());
        for (Artifact artifact : dependencyArtifacts) {
            artifactPatterns.add(artifact.getGroupId() + ":" + artifact.getArtifactId());
        }

        return new PatternInclusionsFilter(artifactPatterns);
    }

    /**
     * Construct a SourceResolverConfig for resolving dependency sources and
     * resources in a consistent way, so it can be reused for both source and
     * resource resolution.
     *
     * @since 2.7
     */
    private SourceResolverConfig getDependencySourceResolverConfig() {
        final List<TransformableFilter> andFilters = new ArrayList<>();

        final List<String> dependencyIncludes = dependencySourceIncludes;
        final List<String> dependencyExcludes = dependencySourceExcludes;

        if (!includeTransitiveDependencySources || isNotEmpty(dependencyIncludes) || isNotEmpty(dependencyExcludes)) {
            if (!includeTransitiveDependencySources) {
                andFilters.add(createDependencyArtifactFilter());
            }

            if (isNotEmpty(dependencyIncludes)) {
                andFilters.add(new PatternInclusionsFilter(dependencyIncludes));
            }

            if (isNotEmpty(dependencyExcludes)) {
                andFilters.add(new PatternExclusionsFilter(dependencyExcludes));
            }
        }

        return configureDependencySourceResolution(new SourceResolverConfig(project, getProjectBuildingRequest(project), sourceDependencyCacheDir)
            .withReactorProjects(reactorProjects)).withFilter(new AndFilter(andFilters));

    }

    private ProjectBuildingRequest getProjectBuildingRequest(MavenProject currentProject) {
        return new DefaultProjectBuildingRequest(session.getProjectBuildingRequest()).setRemoteRepositories(currentProject.getRemoteArtifactRepositories());
    }

    /**
     * Method that indicates whether the javadoc can be generated or not. If the
     * project does not contain any source files and no subpackages are
     * specified, the plugin will terminate.
     *
     * @param files the project files
     * @return a boolean that indicates whether javadoc report can be generated
     *         or not
     */
    protected boolean canGenerateReport(Map<Path, Collection<String>> files) {
        for (Collection<String> filesValues : files.values()) {
            if (!filesValues.isEmpty()) {
                return true;
            }
        }

        return !StringUtils.isEmpty(subpackages);
    }

    // ----------------------------------------------------------------------
    // private methods
    // ----------------------------------------------------------------------

    /**
     * Method to get the excluded source files from the javadoc and create the
     * argument string that will be included in the javadoc commandline
     * execution.
     *
     * @param sourcePaths the collection of paths to the source files
     * @return a String that contains the exclude argument that will be used by
     *         javadoc
     * @throws MavenReportException
     */
    private String getExcludedPackages(Collection<Path> sourcePaths) throws MavenReportException {
        List<String> excludedNames = null;

        if (StringUtils.isNotEmpty(sourcepath) && StringUtils.isNotEmpty(subpackages)) {
            Collection<String> excludedPackages = getExcludedPackages();

            excludedNames = JavadocUtil.getExcludedPackages(sourcePaths, excludedPackages);
        }

        String excludeArg = "";
        if (StringUtils.isNotEmpty(subpackages) && excludedNames != null) {
            // add the excludedpackage names
            excludeArg = StringUtils.join(excludedNames.iterator(), ":");
        }

        return excludeArg;
    }

    /**
     * Method to format the specified source paths that will be accepted by the
     * javadoc tool.
     *
     * @param sourcePaths the list of paths to the source files that will be
     *            included in the javadoc.
     * @return a String that contains the formatted source path argument,
     *         separated by the System pathSeparator string (colon
     *         (<code>:</code>) on Solaris or semi-colon (<code>;</code>) on
     *         Windows).
     * @see File#pathSeparator
     */
    private String getSourcePath(Collection<Path> sourcePaths) {
        String sourcePath = null;

        if (StringUtils.isEmpty(subpackages) || StringUtils.isNotEmpty(sourcepath)) {
            sourcePath = StringUtils.join(sourcePaths.iterator(), File.pathSeparator);
        }

        return sourcePath;
    }

    /**
     * Method to get the packages specified in the
     * <code>excludePackageNames</code> parameter. The packages are split with
     * ',', ':', or ';' and then formatted.
     *
     * @return an array of String objects that contain the package names
     * @throws MavenReportException
     */
    private Collection<String> getExcludedPackages() throws MavenReportException {
        Set<String> excluded = new LinkedHashSet<>();

        if (includeDependencySources) {
            try {
                resolveDependencyBundles();
            } catch (IOException e) {
                throw new MavenReportException("Failed to resolve javadoc bundles from dependencies: " + e.getMessage(), e);
            }

            if (isNotEmpty(dependencyJavadocBundles)) {
                for (JavadocBundle bundle : dependencyJavadocBundles) {
                    JavadocOptions options = bundle.getOptions();
                    if (options != null && isNotEmpty(options.getExcludePackageNames())) {
                        excluded.addAll(options.getExcludePackageNames());
                    }
                }
            }
        }

        // for the specified excludePackageNames
        if (StringUtils.isNotEmpty(excludePackageNames)) {
            List<String> packageNames = Arrays.asList(excludePackageNames.split("[,:;]"));
            excluded.addAll(trimValues(packageNames));
        }

        return excluded;
    }

    private static List<String> trimValues(List<String> items) {
        List<String> result = new ArrayList<>(items.size());
        for (String item : items) {
            String trimmed = item.trim();
            if (StringUtils.isEmpty(trimmed)) {
                continue;
            }
            result.add(trimmed);
        }
        return result;
    }

    /**
     * Method that gets the classpath and modulepath elements that will be
     * specified in the javadoc <code>-classpath</code> and
     * <code>--module-path</code> parameter. Since we have all the sources of
     * the current reactor, it is sufficient to consider the dependencies of the
     * reactor modules, excluding the module artifacts which may not yet be
     * available when the reactor project is built for the first time.
     *
     * @return all classpath elements
     * @throws MavenReportException if any.
     */
    private Collection<File> getPathElements() throws MavenReportException {
        Set<File> classpathElements = new LinkedHashSet<>();
        Map<String, Artifact> compileArtifactMap = new LinkedHashMap<>();

        if (isTest()) {
            classpathElements.addAll(getProjectBuildOutputDirs(project));
        }

        populateCompileArtifactMap(compileArtifactMap, project.getArtifacts());

        if (isAggregator()) {
            Collection<MavenProject> aggregatorProjects = getAggregatedProjects();

            List<String> reactorArtifacts = new ArrayList<>();
            for (MavenProject p : aggregatorProjects) {
                reactorArtifacts.add(p.getGroupId() + ':' + p.getArtifactId());
            }

            TransformableFilter dependencyFilter = new AndFilter(Arrays.asList(new PatternExclusionsFilter(reactorArtifacts), getDependencyScopeFilter()));

            for (MavenProject subProject : aggregatorProjects) {
                if (subProject != project) {
                    File projectArtifactFile = getArtifactFile(subProject);
                    if (projectArtifactFile != null) {
                        classpathElements.add(projectArtifactFile);
                    } else {
                        classpathElements.addAll(getProjectBuildOutputDirs(subProject));
                    }

                    try {
                        StringBuilder sb = new StringBuilder();

                        sb.append("Compiled artifacts for ");
                        sb.append(subProject.getGroupId()).append(":");
                        sb.append(subProject.getArtifactId()).append(":");
                        sb.append(subProject.getVersion()).append('\n');

                        ProjectBuildingRequest buildingRequest = getProjectBuildingRequest(subProject);

                        List<Dependency> managedDependencies = null;
                        if (subProject.getDependencyManagement() != null) {
                            managedDependencies = subProject.getDependencyManagement().getDependencies();
                        }

                        for (ArtifactResult artifactResult : dependencyResolver.resolveDependencies(buildingRequest, subProject.getDependencies(), managedDependencies,
                                                                                                    dependencyFilter)) {
                            populateCompileArtifactMap(compileArtifactMap, Collections.singletonList(artifactResult.getArtifact()));

                            sb.append(artifactResult.getArtifact().getFile()).append('\n');
                        }

                        if (getLog().isDebugEnabled()) {
                            getLog().debug(sb.toString());
                        }

                    } catch (DependencyResolverException e) {
                        throw new MavenReportException(e.getMessage(), e);
                    }
                }
            }
        }

        for (Artifact a : compileArtifactMap.values()) {
            classpathElements.add(a.getFile());
        }

        if (additionalDependencies != null) {
            for (Dependency dependency : additionalDependencies) {
                Artifact artifact = resolveDependency(dependency);
                getLog().debug("add additional artifact with path " + artifact.getFile());
                classpathElements.add(artifact.getFile());
            }
        }

        return classpathElements;
    }

    protected ScopeFilter getDependencyScopeFilter() {
        return ScopeFilter.including(Artifact.SCOPE_COMPILE, Artifact.SCOPE_PROVIDED, Artifact.SCOPE_SYSTEM);
    }

    /**
     * @param dependency {@link Dependency}
     * @return {@link Artifact}
     * @throws MavenReportException when artifact could not be resolved
     */
    public Artifact resolveDependency(Dependency dependency) throws MavenReportException {
        DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
        coordinate.setGroupId(dependency.getGroupId());
        coordinate.setArtifactId(dependency.getArtifactId());
        coordinate.setVersion(dependency.getVersion());
        coordinate.setClassifier(dependency.getClassifier());
        coordinate.setExtension(artifactHandlerManager.getArtifactHandler(dependency.getType()).getExtension());

        try {
            return artifactResolver.resolveArtifact(getProjectBuildingRequest(project), coordinate).getArtifact();
        } catch (ArtifactResolverException e) {
            throw new MavenReportException("artifact resolver problem - " + e.getMessage(), e);
        }
    }

    // TODO remove the part with ToolchainManager lookup once we depend on
    // 3.0.9 (have it as prerequisite). Define as regular component field then.
    protected final Toolchain getToolchain() {
        Toolchain tc = null;

        if (jdkToolchain != null) {
            // Maven 3.3.1 has plugin execution scoped Toolchain Support
            try {
                Method getToolchainsMethod = toolchainManager.getClass().getMethod("getToolchains", MavenSession.class, String.class, Map.class);

                @SuppressWarnings("unchecked")
                List<Toolchain> tcs = (List<Toolchain>)getToolchainsMethod.invoke(toolchainManager, session, "jdk", jdkToolchain);

                if (tcs != null && tcs.size() > 0) {
                    tc = tcs.get(0);
                }
            } catch (SecurityException | ReflectiveOperationException e) {
                // ignore
            }
        }

        if (tc == null) {
            tc = toolchainManager.getToolchainFromBuildContext("jdk", session);
        }

        return tc;
    }

    /**
     * Method to put the artifacts in the hashmap.
     *
     * @param compileArtifactMap the hashmap that will contain the artifacts
     * @param artifactList the list of artifacts that will be put in the map
     * @throws MavenReportException if any
     */
    private void populateCompileArtifactMap(Map<String, Artifact> compileArtifactMap, Collection<Artifact> artifactList) throws MavenReportException {
        if (artifactList == null) {
            return;
        }

        for (Artifact newArtifact : artifactList) {
            File file = newArtifact.getFile();

            if (file == null) {
                throw new MavenReportException("Error in plugin descriptor - " + "dependency was not resolved for artifact: " + newArtifact.getGroupId() + ":"
                                               + newArtifact.getArtifactId() + ":" + newArtifact.getVersion());
            }

            if (compileArtifactMap.get(newArtifact.getDependencyConflictId()) != null) {
                Artifact oldArtifact = compileArtifactMap.get(newArtifact.getDependencyConflictId());

                ArtifactVersion oldVersion = new DefaultArtifactVersion(oldArtifact.getVersion());
                ArtifactVersion newVersion = new DefaultArtifactVersion(newArtifact.getVersion());
                if (newVersion.compareTo(oldVersion) > 0) {
                    compileArtifactMap.put(newArtifact.getDependencyConflictId(), newArtifact);
                }
            } else {
                compileArtifactMap.put(newArtifact.getDependencyConflictId(), newArtifact);
            }
        }
    }

    /**
     * Method that sets the bottom text that will be displayed on the bottom of
     * the javadocs.
     *
     * @return a String that contains the text that will be displayed at the
     *         bottom of the javadoc
     */
    private String getBottomText() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        String year = String.valueOf(currentYear);

        String inceptionYear = project.getInceptionYear();

        String theBottom = StringUtils.replace(this.bottom, "{currentYear}", year);

        if (inceptionYear != null) {
            if (inceptionYear.equals(year)) {
                theBottom = StringUtils.replace(theBottom, "{inceptionYear}&#x2013;", "");
            } else {
                theBottom = StringUtils.replace(theBottom, "{inceptionYear}", inceptionYear);
            }
        } else {
            theBottom = StringUtils.replace(theBottom, "{inceptionYear}&#x2013;", "");
        }

        if (project.getOrganization() == null) {
            theBottom = StringUtils.replace(theBottom, " {organizationName}", "");
        } else {
            if (StringUtils.isNotEmpty(project.getOrganization().getName())) {
                if (StringUtils.isNotEmpty(project.getOrganization().getUrl())) {
                    theBottom = StringUtils.replace(theBottom, "{organizationName}",
                                                    "<a href=\"" + project.getOrganization().getUrl() + "\">" + project.getOrganization().getName() + "</a>");
                } else {
                    theBottom = StringUtils.replace(theBottom, "{organizationName}", project.getOrganization().getName());
                }
            } else {
                theBottom = StringUtils.replace(theBottom, " {organizationName}", "");
            }
        }

        return theBottom;
    }

    /**
     * Method to get the stylesheet path file to be used by the Javadoc Tool.
     * <br/>
     * If the {@link #stylesheetfile} is empty, return the file as String
     * definded by {@link #stylesheet} value. <br/>
     * If the {@link #stylesheetfile} is defined, return the file as String.
     * <br/>
     * Note: since 2.6, the {@link #stylesheetfile} could be a path from a
     * resource in the project source directories (i.e.
     * <code>src/main/java</code>, <code>src/main/resources</code> or
     * <code>src/main/javadoc</code>) or from a resource in the Javadoc plugin
     * dependencies.
     *
     * @param javadocOutputDirectory the output directory
     * @return the stylesheet file absolute path as String.
     * @see #getResource(List, String)
     */
    private String getStylesheetFile(final File javadocOutputDirectory) {
        if (StringUtils.isEmpty(stylesheetfile)) {
            if ("java".equalsIgnoreCase(stylesheet)) {
                // use the default Javadoc tool stylesheet
                return null;
            }

            // maven, see #copyDefaultStylesheet(File)
            return new File(javadocOutputDirectory, DEFAULT_CSS_NAME).getAbsolutePath();
        }

        if (new File(stylesheetfile).exists()) {
            return new File(stylesheetfile).getAbsolutePath();
        }

        return getResource(new File(javadocOutputDirectory, DEFAULT_CSS_NAME), stylesheetfile);
    }

    /**
     * Method to get the help file to be used by the Javadoc Tool. <br/>
     * Since 2.6, the {@link #helpfile} could be a path from a resource in the
     * project source directories (i.e. <code>src/main/java</code>,
     * <code>src/main/resources</code> or <code>src/main/javadoc</code>) or from
     * a resource in the Javadoc plugin dependencies.
     *
     * @param javadocOutputDirectory the output directory.
     * @return the help file absolute path as String.
     * @see #getResource(File, String)
     * @since 2.6
     */
    private String getHelpFile(final File javadocOutputDirectory) {
        if (StringUtils.isEmpty(helpfile)) {
            return null;
        }

        if (new File(helpfile).exists()) {
            return new File(helpfile).getAbsolutePath();
        }

        return getResource(new File(javadocOutputDirectory, "help-doc.html"), helpfile);
    }

    /**
     * Method to get the access level for the classes and members to be shown in
     * the generated javadoc. If the specified access level is not public,
     * protected, package or private, the access level is set to protected.
     *
     * @return the access level
     */
    private String getAccessLevel() {
        String accessLevel;
        if ("public".equalsIgnoreCase(show) || "protected".equalsIgnoreCase(show) || "package".equalsIgnoreCase(show) || "private".equalsIgnoreCase(show)) {
            accessLevel = "-" + show;
        } else {
            if (getLog().isErrorEnabled()) {
                getLog().error("Unrecognized access level to show '" + show + "'. Defaulting to protected.");
            }
            accessLevel = "-protected";
        }

        return accessLevel;
    }

    /**
     * Method to get the path of the bootclass artifacts used in the
     * <code>-bootclasspath</code> option.
     *
     * @return a string that contains bootclass path, separated by the System
     *         pathSeparator string (colon (<code>:</code>) on Solaris or
     *         semi-colon (<code>;</code>) on Windows).
     * @throws MavenReportException if any
     * @see File#pathSeparator
     */
    private String getBootclassPath() throws MavenReportException {
        Set<BootclasspathArtifact> bootclasspathArtifacts = collectBootClasspathArtifacts();

        List<String> bootclassPath = new ArrayList<>();
        for (BootclasspathArtifact aBootclasspathArtifact : bootclasspathArtifacts) {
            if ((StringUtils.isNotEmpty(aBootclasspathArtifact.getGroupId())) && (StringUtils.isNotEmpty(aBootclasspathArtifact.getArtifactId()))
                && (StringUtils.isNotEmpty(aBootclasspathArtifact.getVersion()))) {
                bootclassPath.addAll(getArtifactsAbsolutePath(aBootclasspathArtifact));
            }
        }

        bootclassPath = JavadocUtil.pruneFiles(bootclassPath);

        StringBuilder path = new StringBuilder();
        path.append(StringUtils.join(bootclassPath.iterator(), File.pathSeparator));

        if (StringUtils.isNotEmpty(bootclasspath)) {
            path.append(JavadocUtil.unifyPathSeparator(bootclasspath));
        }

        return path.toString();
    }

    /**
     * Method to get the path of the doclet artifacts used in the
     * <code>-docletpath</code> option.
     * <p/>
     * Either docletArtifact or doclectArtifacts can be defined and used, not
     * both, docletArtifact takes precedence over doclectArtifacts. docletPath
     * is always appended to any result path definition.
     *
     * @return a string that contains doclet path, separated by the System
     *         pathSeparator string (colon (<code>:</code>) on Solaris or
     *         semi-colon (<code>;</code>) on Windows).
     * @throws MavenReportException if any
     * @see File#pathSeparator
     */
    private String getDocletPath() throws MavenReportException {
        Set<DocletArtifact> docletArtifacts = collectDocletArtifacts();
        List<String> pathParts = new ArrayList<>();

        for (DocletArtifact docletArtifact : docletArtifacts) {
            if (!isDocletArtifactEmpty(docletArtifact)) {
                pathParts.addAll(getArtifactsAbsolutePath(docletArtifact));
            }
        }

        if (!StringUtils.isEmpty(docletPath)) {
            pathParts.add(JavadocUtil.unifyPathSeparator(docletPath));
        }

        String path = StringUtils.join(pathParts.iterator(), File.pathSeparator);

        if (StringUtils.isEmpty(path) && getLog().isWarnEnabled()) {
            getLog().warn("No docletpath option was found. Please review <docletpath/> or <docletArtifact/>" + " or <doclets/>.");
        }

        return path;
    }

    /**
     * Verify if a doclet artifact is empty or not
     *
     * @param aDocletArtifact could be null
     * @return <code>true</code> if aDocletArtifact or the
     *         groupId/artifactId/version of the doclet artifact is null,
     *         <code>false</code> otherwise.
     */
    private boolean isDocletArtifactEmpty(DocletArtifact aDocletArtifact) {
        if (aDocletArtifact == null) {
            return true;
        }

        return StringUtils.isEmpty(aDocletArtifact.getGroupId()) && StringUtils.isEmpty(aDocletArtifact.getArtifactId()) && StringUtils.isEmpty(aDocletArtifact.getVersion());
    }

    /**
     * Method to get the path of the taglet artifacts used in the
     * <code>-tagletpath</code> option.
     *
     * @return a string that contains taglet path, separated by the System
     *         pathSeparator string (colon (<code>:</code>) on Solaris or
     *         semi-colon (<code>;</code>) on Windows).
     * @throws MavenReportException if any
     * @see File#pathSeparator
     */
    private String getTagletPath() throws MavenReportException {
        Set<TagletArtifact> tArtifacts = collectTagletArtifacts();
        Collection<String> pathParts = new ArrayList<>();

        for (TagletArtifact tagletArtifact : tArtifacts) {
            if ((tagletArtifact != null) && (StringUtils.isNotEmpty(tagletArtifact.getGroupId())) && (StringUtils.isNotEmpty(tagletArtifact.getArtifactId()))
                && (StringUtils.isNotEmpty(tagletArtifact.getVersion()))) {
                pathParts.addAll(getArtifactsAbsolutePath(tagletArtifact));
            }
        }

        Set<Taglet> taglets = collectTaglets();
        for (Taglet taglet : taglets) {
            if (taglet == null) {
                continue;
            }

            if ((taglet.getTagletArtifact() != null) && (StringUtils.isNotEmpty(taglet.getTagletArtifact().getGroupId()))
                && (StringUtils.isNotEmpty(taglet.getTagletArtifact().getArtifactId())) && (StringUtils.isNotEmpty(taglet.getTagletArtifact().getVersion()))) {
                pathParts.addAll(JavadocUtil.pruneFiles(getArtifactsAbsolutePath(taglet.getTagletArtifact())));
            } else if (StringUtils.isNotEmpty(taglet.getTagletpath())) {
                for (Path dir : JavadocUtil.pruneDirs(project, Collections.singletonList(taglet.getTagletpath()))) {
                    pathParts.add(dir.toString());
                }
            }
        }

        StringBuilder path = new StringBuilder();
        path.append(StringUtils.join(pathParts.iterator(), File.pathSeparator));

        if (StringUtils.isNotEmpty(tagletpath)) {
            path.append(JavadocUtil.unifyPathSeparator(tagletpath));
        }

        return path.toString();
    }

    private Set<String> collectLinks() throws MavenReportException {
        Set<String> links = new LinkedHashSet<>();

        if (includeDependencySources) {
            try {
                resolveDependencyBundles();
            } catch (IOException e) {
                throw new MavenReportException("Failed to resolve javadoc bundles from dependencies: " + e.getMessage(), e);
            }

            if (isNotEmpty(dependencyJavadocBundles)) {
                for (JavadocBundle bundle : dependencyJavadocBundles) {
                    JavadocOptions options = bundle.getOptions();
                    if (options != null && isNotEmpty(options.getLinks())) {
                        links.addAll(options.getLinks());
                    }
                }
            }
        }

        if (isNotEmpty(this.links)) {
            links.addAll(this.links);
        }

        links.addAll(getDependenciesLinks());

        return followLinks(links);
    }

    private Set<Group> collectGroups() throws MavenReportException {
        Set<Group> groups = new LinkedHashSet<>();

        if (includeDependencySources) {
            try {
                resolveDependencyBundles();
            } catch (IOException e) {
                throw new MavenReportException("Failed to resolve javadoc bundles from dependencies: " + e.getMessage(), e);
            }

            if (isNotEmpty(dependencyJavadocBundles)) {
                for (JavadocBundle bundle : dependencyJavadocBundles) {
                    JavadocOptions options = bundle.getOptions();
                    if (options != null && isNotEmpty(options.getGroups())) {
                        groups.addAll(options.getGroups());
                    }
                }
            }
        }

        if (this.groups != null && this.groups.length > 0) {
            groups.addAll(Arrays.asList(this.groups));
        }

        return groups;
    }

    private Set<ResourcesArtifact> collectResourcesArtifacts() throws MavenReportException {
        Set<ResourcesArtifact> result = new LinkedHashSet<>();

        if (includeDependencySources) {
            try {
                resolveDependencyBundles();
            } catch (IOException e) {
                throw new MavenReportException("Failed to resolve javadoc bundles from dependencies: " + e.getMessage(), e);
            }

            if (isNotEmpty(dependencyJavadocBundles)) {
                for (JavadocBundle bundle : dependencyJavadocBundles) {
                    JavadocOptions options = bundle.getOptions();
                    if (options != null && isNotEmpty(options.getResourcesArtifacts())) {
                        result.addAll(options.getResourcesArtifacts());
                    }
                }
            }
        }

        if (this.resourcesArtifacts != null && this.resourcesArtifacts.length > 0) {
            result.addAll(Arrays.asList(this.resourcesArtifacts));
        }

        return result;
    }

    private Set<BootclasspathArtifact> collectBootClasspathArtifacts() throws MavenReportException {
        Set<BootclasspathArtifact> result = new LinkedHashSet<>();

        if (includeDependencySources) {
            try {
                resolveDependencyBundles();
            } catch (IOException e) {
                throw new MavenReportException("Failed to resolve javadoc bundles from dependencies: " + e.getMessage(), e);
            }

            if (isNotEmpty(dependencyJavadocBundles)) {
                for (JavadocBundle bundle : dependencyJavadocBundles) {
                    JavadocOptions options = bundle.getOptions();
                    if (options != null && isNotEmpty(options.getBootclasspathArtifacts())) {
                        result.addAll(options.getBootclasspathArtifacts());
                    }
                }
            }
        }

        if (this.bootclasspathArtifacts != null && this.bootclasspathArtifacts.length > 0) {
            result.addAll(Arrays.asList(this.bootclasspathArtifacts));
        }

        return result;
    }

    private Set<OfflineLink> collectOfflineLinks() throws MavenReportException {
        Set<OfflineLink> result = new LinkedHashSet<>();

        OfflineLink javaApiLink = getDefaultJavadocApiLink();
        if (javaApiLink != null) {
            result.add(javaApiLink);
        }

        if (includeDependencySources) {
            try {
                resolveDependencyBundles();
            } catch (IOException e) {
                throw new MavenReportException("Failed to resolve javadoc bundles from dependencies: " + e.getMessage(), e);
            }

            if (isNotEmpty(dependencyJavadocBundles)) {
                for (JavadocBundle bundle : dependencyJavadocBundles) {
                    JavadocOptions options = bundle.getOptions();
                    if (options != null && isNotEmpty(options.getOfflineLinks())) {
                        result.addAll(options.getOfflineLinks());
                    }
                }
            }
        }

        if (this.offlineLinks != null && this.offlineLinks.length > 0) {
            result.addAll(Arrays.asList(this.offlineLinks));
        }

        return result;
    }

    private Set<Tag> collectTags() throws MavenReportException {
        Set<Tag> tags = new LinkedHashSet<>();

        if (includeDependencySources) {
            try {
                resolveDependencyBundles();
            } catch (IOException e) {
                throw new MavenReportException("Failed to resolve javadoc bundles from dependencies: " + e.getMessage(), e);
            }

            if (isNotEmpty(dependencyJavadocBundles)) {
                for (JavadocBundle bundle : dependencyJavadocBundles) {
                    JavadocOptions options = bundle.getOptions();
                    if (options != null && isNotEmpty(options.getTags())) {
                        tags.addAll(options.getTags());
                    }
                }
            }
        }

        if (this.tags != null && this.tags.length > 0) {
            tags.addAll(Arrays.asList(this.tags));
        }

        return tags;
    }

    private Set<TagletArtifact> collectTagletArtifacts() throws MavenReportException {
        Set<TagletArtifact> tArtifacts = new LinkedHashSet<>();

        if (includeDependencySources) {
            try {
                resolveDependencyBundles();
            } catch (IOException e) {
                throw new MavenReportException("Failed to resolve javadoc bundles from dependencies: " + e.getMessage(), e);
            }

            if (isNotEmpty(dependencyJavadocBundles)) {
                for (JavadocBundle bundle : dependencyJavadocBundles) {
                    JavadocOptions options = bundle.getOptions();
                    if (options != null && isNotEmpty(options.getTagletArtifacts())) {
                        tArtifacts.addAll(options.getTagletArtifacts());
                    }
                }
            }
        }

        if (tagletArtifact != null) {
            tArtifacts.add(tagletArtifact);
        }

        if (tagletArtifacts != null && tagletArtifacts.length > 0) {
            tArtifacts.addAll(Arrays.asList(tagletArtifacts));
        }

        return tArtifacts;
    }

    private Set<DocletArtifact> collectDocletArtifacts() throws MavenReportException {
        Set<DocletArtifact> dArtifacts = new LinkedHashSet<>();

        if (includeDependencySources) {
            try {
                resolveDependencyBundles();
            } catch (IOException e) {
                throw new MavenReportException("Failed to resolve javadoc bundles from dependencies: " + e.getMessage(), e);
            }

            if (isNotEmpty(dependencyJavadocBundles)) {
                for (JavadocBundle bundle : dependencyJavadocBundles) {
                    JavadocOptions options = bundle.getOptions();
                    if (options != null && isNotEmpty(options.getDocletArtifacts())) {
                        dArtifacts.addAll(options.getDocletArtifacts());
                    }
                }
            }
        }

        if (docletArtifact != null) {
            dArtifacts.add(docletArtifact);
        }

        if (docletArtifacts != null && docletArtifacts.length > 0) {
            dArtifacts.addAll(Arrays.asList(docletArtifacts));
        }

        return dArtifacts;
    }

    private Set<Taglet> collectTaglets() throws MavenReportException {
        Set<Taglet> result = new LinkedHashSet<>();

        if (includeDependencySources) {
            try {
                resolveDependencyBundles();
            } catch (IOException e) {
                throw new MavenReportException("Failed to resolve javadoc bundles from dependencies: " + e.getMessage(), e);
            }

            if (isNotEmpty(dependencyJavadocBundles)) {
                for (JavadocBundle bundle : dependencyJavadocBundles) {
                    JavadocOptions options = bundle.getOptions();
                    if (options != null && isNotEmpty(options.getTaglets())) {
                        result.addAll(options.getTaglets());
                    }
                }
            }
        }

        if (taglets != null && taglets.length > 0) {
            result.addAll(Arrays.asList(taglets));
        }

        return result;
    }

    /**
     * Return the Javadoc artifact path and its transitive dependencies path
     * from the local repository
     *
     * @param javadocArtifact not null
     * @return a list of locale artifacts absolute path
     * @throws MavenReportException if any
     */
    private List<String> getArtifactsAbsolutePath(JavadocPathArtifact javadocArtifact) throws MavenReportException {
        if ((StringUtils.isEmpty(javadocArtifact.getGroupId())) && (StringUtils.isEmpty(javadocArtifact.getArtifactId())) && (StringUtils.isEmpty(javadocArtifact.getVersion()))) {
            return Collections.emptyList();
        }

        List<String> path = new ArrayList<>();

        try {
            Artifact artifact = createAndResolveArtifact(javadocArtifact);
            path.add(artifact.getFile().getAbsolutePath());

            DefaultDependableCoordinate coordinate = new DefaultDependableCoordinate();
            coordinate.setGroupId(javadocArtifact.getGroupId());
            coordinate.setArtifactId(javadocArtifact.getArtifactId());
            coordinate.setVersion(javadocArtifact.getVersion());

            Iterable<ArtifactResult> deps = dependencyResolver.resolveDependencies(getProjectBuildingRequest(project), coordinate, ScopeFilter.including("compile", "provided"));
            for (ArtifactResult a : deps) {
                path.add(a.getArtifact().getFile().getAbsolutePath());
            }

            return path;
        } catch (ArtifactResolverException e) {
            throw new MavenReportException("Unable to resolve artifact:" + javadocArtifact, e);
        } catch (DependencyResolverException e) {
            throw new MavenReportException("Unable to resolve dependencies for:" + javadocArtifact, e);
        }
    }

    /**
     * creates an {@link Artifact} representing the configured
     * {@link JavadocPathArtifact} and resolves it.
     *
     * @param javadocArtifact the {@link JavadocPathArtifact} to resolve
     * @return a resolved {@link Artifact}
     * @throws ArtifactResolverException
     */
    private Artifact createAndResolveArtifact(JavadocPathArtifact javadocArtifact) throws ArtifactResolverException {
        DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
        coordinate.setGroupId(javadocArtifact.getGroupId());
        coordinate.setArtifactId(javadocArtifact.getArtifactId());
        coordinate.setVersion(javadocArtifact.getVersion());

        return artifactResolver.resolveArtifact(getProjectBuildingRequest(project), coordinate).getArtifact();
    }

    /**
     * Method that adds/sets the java memory parameters in the command line
     * execution.
     *
     * @param cmd the command line execution object where the argument will be
     *            added
     * @param arg the argument parameter name
     * @param memory the JVM memory value to be set
     * @see JavadocUtil#parseJavadocMemory(String)
     */
    private void addMemoryArg(Commandline cmd, String arg, String memory) {
        if (StringUtils.isNotEmpty(memory)) {
            try {
                cmd.createArg().setValue("-J" + arg + JavadocUtil.parseJavadocMemory(memory));
            } catch (IllegalArgumentException e) {
                if (getLog().isErrorEnabled()) {
                    getLog().error("Malformed memory pattern for '" + arg + memory + "'. Ignore this option.");
                }
            }
        }
    }

    /**
     * Method that adds/sets the javadoc proxy parameters in the command line
     * execution.
     *
     * @param cmd the command line execution object where the argument will be
     *            added
     */
    private void addProxyArg(Commandline cmd) {
        if (settings == null || settings.getProxies().isEmpty()) {
            return;
        }

        Map<String, Proxy> activeProxies = new HashMap<>();

        for (Proxy proxy : settings.getProxies()) {
            if (proxy.isActive()) {
                String protocol = proxy.getProtocol();

                if (!activeProxies.containsKey(protocol)) {
                    activeProxies.put(protocol, proxy);
                }
            }
        }

        if (activeProxies.containsKey("https")) {
            Proxy httpsProxy = activeProxies.get("https");
            if (StringUtils.isNotEmpty(httpsProxy.getHost())) {
                cmd.createArg().setValue("-J-Dhttps.proxyHost=" + httpsProxy.getHost());
                cmd.createArg().setValue("-J-Dhttps.proxyPort=" + httpsProxy.getPort());

                if (StringUtils.isNotEmpty(httpsProxy.getNonProxyHosts())
                    && (!activeProxies.containsKey("http") || StringUtils.isEmpty(activeProxies.get("http").getNonProxyHosts()))) {
                    cmd.createArg().setValue("-J-Dhttp.nonProxyHosts=\"" + httpsProxy.getNonProxyHosts().replace("|", "^|") + "\"");
                }
            }
        }

        if (activeProxies.containsKey("http")) {
            Proxy httpProxy = activeProxies.get("http");
            if (StringUtils.isNotEmpty(httpProxy.getHost())) {
                cmd.createArg().setValue("-J-Dhttp.proxyHost=" + httpProxy.getHost());
                cmd.createArg().setValue("-J-Dhttp.proxyPort=" + httpProxy.getPort());

                if (!activeProxies.containsKey("https")) {
                    cmd.createArg().setValue("-J-Dhttps.proxyHost=" + httpProxy.getHost());
                    cmd.createArg().setValue("-J-Dhttps.proxyPort=" + httpProxy.getPort());
                }

                if (StringUtils.isNotEmpty(httpProxy.getNonProxyHosts())) {
                    cmd.createArg().setValue("-J-Dhttp.nonProxyHosts=\"" + httpProxy.getNonProxyHosts().replace("|", "^|") + "\"");
                }
            }
        }

        // We bravely ignore FTP because no one (probably) uses FTP for Javadoc
    }

    /**
     * Get the path of the Javadoc tool executable depending the user entry or
     * try to find it depending the OS or the <code>java.home</code> system
     * property or the <code>JAVA_HOME</code> environment variable.
     *
     * @return the path of the Javadoc tool
     * @throws IOException if not found
     */
    private String getJavadocExecutable() throws IOException {
        Toolchain tc = getToolchain();

        if (tc != null) {
            getLog().info("Toolchain in maven-javadoc-plugin: " + tc);
            if (javadocExecutable != null) {
                getLog().warn("Toolchains are ignored, 'javadocExecutable' parameter is set to " + javadocExecutable);
            } else {
                javadocExecutable = tc.findTool("javadoc");
            }
        }

        String javadocCommand = "javadoc" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "");

        File javadocExe;

        // ----------------------------------------------------------------------
        // The javadoc executable is defined by the user
        // ----------------------------------------------------------------------
        if (StringUtils.isNotEmpty(javadocExecutable)) {
            javadocExe = new File(javadocExecutable);

            if (javadocExe.isDirectory()) {
                javadocExe = new File(javadocExe, javadocCommand);
            }

            if (SystemUtils.IS_OS_WINDOWS && javadocExe.getName().indexOf('.') < 0) {
                javadocExe = new File(javadocExe.getPath() + ".exe");
            }

            if (!javadocExe.isFile()) {
                throw new IOException("The javadoc executable '" + javadocExe + "' doesn't exist or is not a file. Verify the <javadocExecutable/> parameter.");
            }

            return javadocExe.getAbsolutePath();
        }

        // ----------------------------------------------------------------------
        // Try to find javadocExe from System.getProperty( "java.home" )
        // By default, System.getProperty( "java.home" ) = JRE_HOME and JRE_HOME
        // should be in the JDK_HOME
        // ----------------------------------------------------------------------
        // For IBM's JDK 1.2
        // CHECKSTYLE:OFF
        if (SystemUtils.IS_OS_AIX) {
            javadocExe = new File(SystemUtils.getJavaHome() + File.separator + ".." + File.separator + "sh", javadocCommand);
        }
        // For Apple's JDK 1.6.x (and older?) on Mac OSX
        else if (SystemUtils.IS_OS_MAC_OSX && !JavaVersion.JAVA_SPECIFICATION_VERSION.isAtLeast("1.7"))
        {
            javadocExe = new File(SystemUtils.getJavaHome() + File.separator + "bin", javadocCommand);
        } else {
            javadocExe = new File(SystemUtils.getJavaHome() + File.separator + ".." + File.separator + "bin", javadocCommand);
        }
        // CHECKSTYLE:ON

        // ----------------------------------------------------------------------
        // Try to find javadocExe from JAVA_HOME environment variable
        // ----------------------------------------------------------------------
        if (!javadocExe.exists() || !javadocExe.isFile()) {
            Properties env = CommandLineUtils.getSystemEnvVars();
            String javaHome = env.getProperty("JAVA_HOME");
            if (StringUtils.isEmpty(javaHome)) {
                throw new IOException("The environment variable JAVA_HOME is not correctly set.");
            }
            if ((!new File(javaHome).getCanonicalFile().exists()) || (new File(javaHome).getCanonicalFile().isFile())) {
                throw new IOException("The environment variable JAVA_HOME=" + javaHome + " doesn't exist or is not a valid directory.");
            }

            javadocExe = new File(javaHome + File.separator + "bin", javadocCommand);
        }

        if (!javadocExe.getCanonicalFile().exists() || !javadocExe.getCanonicalFile().isFile()) {
            throw new IOException("The javadoc executable '" + javadocExe + "' doesn't exist or is not a file. Verify the JAVA_HOME environment variable.");
        }

        return javadocExe.getAbsolutePath();
    }

    /**
     * Set a new value for <code>javadocRuntimeVersion</code>
     *
     * @param jExecutable not null
     * @throws MavenReportException if not found
     * @see JavadocUtil#getJavadocVersion(File)
     */
    private void setFJavadocVersion(File jExecutable) throws MavenReportException {
        JavaVersion jVersion;
        try {
            jVersion = JavadocUtil.getJavadocVersion(jExecutable);
        } catch (IOException | CommandLineException | IllegalArgumentException e) {
            if (getLog().isWarnEnabled()) {
                getLog().warn("Unable to find the javadoc version: " + e.getMessage());
                getLog().warn("Using the Java version instead of, i.e. " + JAVA_VERSION);
            }
            jVersion = JAVA_VERSION;
        }

        if (StringUtils.isNotEmpty(javadocVersion)) {
            try {
                javadocRuntimeVersion = JavaVersion.parse(javadocVersion);
            } catch (NumberFormatException e) {
                throw new MavenReportException("Unable to parse javadoc version: " + e.getMessage(), e);
            }

            if (javadocRuntimeVersion.compareTo(jVersion) != 0 && getLog().isWarnEnabled()) {
                getLog().warn("Are you sure about the <javadocVersion/> parameter? It seems to be " + jVersion);
            }
        } else {
            javadocRuntimeVersion = jVersion;
        }
    }

    /**
     * Is the Javadoc version at least the requested version.
     *
     * @param requiredVersion the required version, for example 1.5f
     * @return <code>true</code> if the javadoc version is equal or greater than
     *         the required version
     */
    private boolean isJavaDocVersionAtLeast(JavaVersion requiredVersion) {
        return JAVA_VERSION.compareTo(requiredVersion) >= 0;
    }

    /**
     * Convenience method to add an argument to the <code>command line</code>
     * conditionally based on the given flag.
     *
     * @param arguments a list of arguments, not null
     * @param b the flag which controls if the argument is added or not.
     * @param value the argument value to be added.
     */
    private void addArgIf(List<String> arguments, boolean b, String value) {
        if (b) {
            arguments.add(value);
        }
    }

    /**
     * Convenience method to add an argument to the <code>command line</code>
     * regarding the requested Java version.
     *
     * @param arguments a list of arguments, not null
     * @param b the flag which controls if the argument is added or not.
     * @param value the argument value to be added.
     * @param requiredJavaVersion the required Java version, for example 1.31f
     *            or 1.4f
     * @see #addArgIf(List, boolean, String)
     * @see #isJavaDocVersionAtLeast(JavaVersion)
     */
    private void addArgIf(List<String> arguments, boolean b, String value, JavaVersion requiredJavaVersion) {
        if (b) {
            if (isJavaDocVersionAtLeast(requiredJavaVersion)) {
                addArgIf(arguments, true, value);
            } else {
                if (getLog().isWarnEnabled()) {
                    getLog().warn(value + " option is not supported on Java version < " + requiredJavaVersion + ". Ignore this option.");
                }
            }
        }
    }

    /**
     * Convenience method to add an argument to the <code>command line</code> if
     * the the value is not null or empty.
     * <p/>
     * Moreover, the value could be comma separated.
     *
     * @param arguments a list of arguments, not null
     * @param key the argument name.
     * @param value the argument value to be added.
     * @see #addArgIfNotEmpty(List, String, String, boolean)
     */
    private void addArgIfNotEmpty(List<String> arguments, String key, String value) {
        addArgIfNotEmpty(arguments, key, value, false);
    }

    /**
     * Convenience method to add an argument to the <code>command line</code> if
     * the the value is not null or empty.
     * <p/>
     * Moreover, the value could be comma separated.
     *
     * @param arguments a list of arguments, not null
     * @param key the argument name.
     * @param value the argument value to be added.
     * @param repeatKey repeat or not the key in the command line
     * @param splitValue if <code>true</code> given value will be tokenized by
     *            comma
     * @param requiredJavaVersion the required Java version, for example 1.31f
     *            or 1.4f
     * @see #addArgIfNotEmpty(List, String, String, boolean, boolean)
     * @see #isJavaDocVersionAtLeast(JavaVersion)
     */
    private void addArgIfNotEmpty(List<String> arguments, String key, String value, boolean repeatKey, boolean splitValue, JavaVersion requiredJavaVersion) {
        if (StringUtils.isNotEmpty(value)) {
            if (isJavaDocVersionAtLeast(requiredJavaVersion)) {
                addArgIfNotEmpty(arguments, key, value, repeatKey, splitValue);
            } else {
                if (getLog().isWarnEnabled()) {
                    getLog().warn(key + " option is not supported on Java version < " + requiredJavaVersion + ". Ignore this option.");
                }
            }
        }
    }

    /**
     * Convenience method to add an argument to the <code>command line</code> if
     * the the value is not null or empty.
     * <p/>
     * Moreover, the value could be comma separated.
     *
     * @param arguments a list of arguments, not null
     * @param key the argument name.
     * @param value the argument value to be added.
     * @param repeatKey repeat or not the key in the command line
     * @param splitValue if <code>true</code> given value will be tokenized by
     *            comma
     */
    private void addArgIfNotEmpty(List<String> arguments, String key, String value, boolean repeatKey, boolean splitValue) {
        if (StringUtils.isNotEmpty(value)) {
            if (StringUtils.isNotEmpty(key)) {
                arguments.add(key);
            }

            if (splitValue) {
                StringTokenizer token = new StringTokenizer(value, ",");
                while (token.hasMoreTokens()) {
                    String current = token.nextToken().trim();

                    if (StringUtils.isNotEmpty(current)) {
                        arguments.add(current);

                        if (token.hasMoreTokens() && repeatKey) {
                            arguments.add(key);
                        }
                    }
                }
            } else {
                arguments.add(value);
            }
        }
    }

    /**
     * Convenience method to add an argument to the <code>command line</code> if
     * the the value is not null or empty.
     * <p/>
     * Moreover, the value could be comma separated.
     *
     * @param arguments a list of arguments, not null
     * @param key the argument name.
     * @param value the argument value to be added.
     * @param repeatKey repeat or not the key in the command line
     */
    private void addArgIfNotEmpty(List<String> arguments, String key, String value, boolean repeatKey) {
        addArgIfNotEmpty(arguments, key, value, repeatKey, true);
    }

    /**
     * Convenience method to add an argument to the <code>command line</code>
     * regarding the requested Java version.
     *
     * @param arguments a list of arguments, not null
     * @param key the argument name.
     * @param value the argument value to be added.
     * @param requiredJavaVersion the required Java version, for example 1.31f
     *            or 1.4f
     * @see #addArgIfNotEmpty(List, String, String, JavaVersion, boolean)
     */
    private void addArgIfNotEmpty(List<String> arguments, String key, String value, JavaVersion requiredJavaVersion) {
        addArgIfNotEmpty(arguments, key, value, requiredJavaVersion, false);
    }

    /**
     * Convenience method to add an argument to the <code>command line</code>
     * regarding the requested Java version.
     *
     * @param arguments a list of arguments, not null
     * @param key the argument name.
     * @param value the argument value to be added.
     * @param requiredJavaVersion the required Java version, for example 1.31f
     *            or 1.4f
     * @param repeatKey repeat or not the key in the command line
     * @see #addArgIfNotEmpty(List, String, String)
     * @see #isJavaDocVersionAtLeast
     */
    private void addArgIfNotEmpty(List<String> arguments, String key, String value, JavaVersion requiredJavaVersion, boolean repeatKey) {
        if (StringUtils.isNotEmpty(value)) {
            if (isJavaDocVersionAtLeast(requiredJavaVersion)) {
                addArgIfNotEmpty(arguments, key, value, repeatKey);
            } else {
                if (getLog().isWarnEnabled()) {
                    getLog().warn(key + " option is not supported on Java version < " + requiredJavaVersion);
                }
            }
        }
    }

    /**
     * Convenience method to process {@link #offlineLinks} values as individual
     * <code>-linkoffline</code> javadoc options. <br/>
     * If {@link #detectOfflineLinks}, try to add javadoc apidocs according
     * Maven conventions for all modules given in the project.
     *
     * @param arguments a list of arguments, not null
     * @throws MavenReportException if any
     * @see #offlineLinks
     * @see #getModulesLinks()
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#package-list">package-list
     *      spec</a>
     */
    private void addLinkofflineArguments(List<String> arguments, Set<OfflineLink> offlineLinksList) throws MavenReportException {
        for (OfflineLink offlineLink : offlineLinksList) {
            String url = offlineLink.getUrl();
            if (StringUtils.isEmpty(url)) {
                continue;
            }
            url = cleanUrl(url);

            String location = offlineLink.getLocation();
            if (StringUtils.isEmpty(location)) {
                continue;
            }
            if (isValidJavadocLink(location, false)) {
                addArgIfNotEmpty(arguments, "-linkoffline", JavadocUtil.quotedPathArgument(url) + " " + JavadocUtil.quotedPathArgument(location), true);
            }
        }
    }

    private Set<OfflineLink> getLinkofflines() throws MavenReportException {
        Set<OfflineLink> offlineLinksList = collectOfflineLinks();

        offlineLinksList.addAll(getModulesLinks());

        return offlineLinksList;
    }

    /**
     * Convenience method to process {@link #links} values as individual
     * <code>-link</code> javadoc options. If {@link #detectLinks}, try to add
     * javadoc apidocs according Maven conventions for all dependencies given in
     * the project. <br/>
     * According the Javadoc documentation, all defined link should have
     * <code>${link}/package-list</code> fetchable. <br/>
     * <b>Note</b>: when a link is not fetchable:
     * <ul>
     * <li>Javadoc 1.4 and less throw an exception</li>
     * <li>Javadoc 1.5 and more display a warning</li>
     * </ul>
     *
     * @param arguments a list of arguments, not null
     * @throws MavenReportException
     * @see #detectLinks
     * @see #getDependenciesLinks()
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#package-list">package-list
     *      spec</a>
     */
    private void addLinkArguments(List<String> arguments) throws MavenReportException {
        Set<String> links = collectLinks();

        for (String link : links) {
            if (StringUtils.isEmpty(link)) {
                continue;
            }

            if (isOffline && !link.startsWith("file:")) {
                continue;
            }

            while (link.endsWith("/")) {
                link = link.substring(0, link.lastIndexOf("/"));
            }

            addArgIfNotEmpty(arguments, "-link", JavadocUtil.quotedPathArgument(link), true, false);
        }
    }

    /**
     * Coppy all resources to the output directory
     *
     * @param javadocOutputDirectory not null
     * @throws MavenReportException if any
     * @see #copyDefaultStylesheet(File)
     * @see #copyJavadocResources(File)
     * @see #copyAdditionalJavadocResources(File)
     */
    private void copyAllResources(File javadocOutputDirectory) throws MavenReportException {
        // ----------------------------------------------------------------------
        // Copy default resources
        // ----------------------------------------------------------------------

        try {
            copyDefaultStylesheet(javadocOutputDirectory);
        } catch (IOException e) {
            throw new MavenReportException("Unable to copy default stylesheet: " + e.getMessage(), e);
        }

        // ----------------------------------------------------------------------
        // Copy javadoc resources
        // ----------------------------------------------------------------------

        if (docfilessubdirs) {
            /*
             * Workaround since -docfilessubdirs doesn't seem to be used
             * correctly by the javadoc tool (see other note about -sourcepath).
             * Take care of the -excludedocfilessubdir option.
             */
            try {
                copyJavadocResources(javadocOutputDirectory);
            } catch (IOException e) {
                throw new MavenReportException("Unable to copy javadoc resources: " + e.getMessage(), e);
            }
        }

        // ----------------------------------------------------------------------
        // Copy additional javadoc resources in artifacts
        // ----------------------------------------------------------------------

        copyAdditionalJavadocResources(javadocOutputDirectory);
    }

    /**
     * Copies the {@link #DEFAULT_CSS_NAME} css file from the current class
     * loader to the <code>outputDirectory</code> only if
     * {@link #stylesheetfile} is empty and {@link #stylesheet} is equals to
     * <code>maven</code>.
     *
     * @param anOutputDirectory the output directory
     * @throws java.io.IOException if any
     * @see #DEFAULT_CSS_NAME
     * @see JavadocUtil#copyResource(java.net.URL, java.io.File)
     */
    private void copyDefaultStylesheet(File anOutputDirectory) throws IOException {
        if (StringUtils.isNotEmpty(stylesheetfile)) {
            return;
        }

        if (!stylesheet.equalsIgnoreCase("maven")) {
            return;
        }

        URL url = getClass().getClassLoader().getResource(RESOURCE_CSS_DIR + "/" + DEFAULT_CSS_NAME);
        File outFile = new File(anOutputDirectory, DEFAULT_CSS_NAME);
        JavadocUtil.copyResource(url, outFile);
    }

    /**
     * Method that copy all <code>doc-files</code> directories from
     * <code>javadocDirectory</code> of the current project or of the projects
     * in the reactor to the <code>outputDirectory</code>.
     *
     * @param anOutputDirectory the output directory
     * @throws java.io.IOException if any
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.2.html#docfiles">Reference
     *      Guide, Copies new "doc-files" directory for holding images and
     *      examples</a>
     * @see #docfilessubdirs
     */
    private void copyJavadocResources(File anOutputDirectory) throws IOException {
        if (anOutputDirectory == null || !anOutputDirectory.exists()) {
            throw new IOException("The outputDirectory " + anOutputDirectory + " doesn't exists.");
        }

        if (includeDependencySources) {
            resolveDependencyBundles();
            if (isNotEmpty(dependencyJavadocBundles)) {
                for (JavadocBundle bundle : dependencyJavadocBundles) {
                    File dir = bundle.getResourcesDirectory();
                    JavadocOptions options = bundle.getOptions();
                    if (dir != null && dir.isDirectory()) {
                        JavadocUtil.copyJavadocResources(anOutputDirectory, dir, options == null ? null : options.getExcludedDocfilesSubdirs());
                    }
                }
            }
        }

        if (getJavadocDirectory() != null) {
            JavadocUtil.copyJavadocResources(anOutputDirectory, getJavadocDirectory(), excludedocfilessubdir);
        }

        if (isAggregator()) {
            for (MavenProject subProject : getAggregatedProjects()) {
                if (subProject != project && getJavadocDirectory() != null) {
                    String javadocDirRelative = PathUtils.toRelative(project.getBasedir(), getJavadocDirectory().getAbsolutePath());
                    File javadocDir = new File(subProject.getBasedir(), javadocDirRelative);
                    JavadocUtil.copyJavadocResources(anOutputDirectory, javadocDir, excludedocfilessubdir);
                }
            }
        }
    }

    private synchronized void resolveDependencyBundles() throws IOException {
        if (dependencyJavadocBundles == null) {
            dependencyJavadocBundles = resourceResolver.resolveDependencyJavadocBundles(getDependencySourceResolverConfig());
            if (dependencyJavadocBundles == null) {
                dependencyJavadocBundles = new ArrayList<>();
            }
        }
    }

    /**
     * Method that copy additional Javadoc resources from given artifacts.
     *
     * @param anOutputDirectory the output directory
     * @throws MavenReportException if any
     * @see #resourcesArtifacts
     */
    private void copyAdditionalJavadocResources(File anOutputDirectory) throws MavenReportException {
        Set<ResourcesArtifact> resourcesArtifacts = collectResourcesArtifacts();
        if (isEmpty(resourcesArtifacts)) {
            return;
        }

        UnArchiver unArchiver;
        try {
            unArchiver = archiverManager.getUnArchiver("jar");
        } catch (NoSuchArchiverException e) {
            throw new MavenReportException("Unable to extract resources artifact. " + "No archiver for 'jar' available.", e);
        }

        for (ResourcesArtifact item : resourcesArtifacts) {
            Artifact artifact;
            try {
                artifact = createAndResolveArtifact(item);
            } catch (ArtifactResolverException e) {
                throw new MavenReportException("Unable to resolve artifact:" + item, e);
            }

            unArchiver.setSourceFile(artifact.getFile());
            unArchiver.setDestDirectory(anOutputDirectory);
            // remove the META-INF directory from resource artifact
            IncludeExcludeFileSelector[] selectors = new IncludeExcludeFileSelector[] {new IncludeExcludeFileSelector()};
            selectors[0].setExcludes(new String[] {"META-INF/**"});
            unArchiver.setFileSelectors(selectors);

            getLog().info("Extracting contents of resources artifact: " + artifact.getArtifactId());
            try {
                unArchiver.extract();
            } catch (ArchiverException e) {
                throw new MavenReportException("Extraction of resources failed. Artifact that failed was: " + artifact.getArtifactId(), e);
            }
        }
    }

    /**
     * @param sourcePaths could be null
     * @return the list of package names for files in the sourcePaths
     */
    private List<String> getPackageNames(Map<Path, Collection<String>> sourcePaths) {
        List<String> returnList = new ArrayList<>();

        if (!StringUtils.isEmpty(sourcepath)) {
            return returnList;
        }

        for (Entry<Path, Collection<String>> currentPathEntry : sourcePaths.entrySet()) {
            for (String currentFile : currentPathEntry.getValue()) {
                /*
                 * Remove the miscellaneous files
                 * http://docs.oracle.com/javase/1.4.2/docs/tooldocs/solaris/
                 * javadoc.html#unprocessed
                 */
                if (currentFile.contains("doc-files")) {
                    continue;
                }

                int lastIndexOfSeparator = currentFile.lastIndexOf("/");
                if (lastIndexOfSeparator != -1) {
                    String packagename = currentFile.substring(0, lastIndexOfSeparator).replace('/', '.');

                    if (!returnList.contains(packagename)) {
                        returnList.add(packagename);
                    }
                }
            }
        }

        return returnList;
    }

    /**
     * @param allSourcePaths not null, containing absolute and relative paths
     * @return a list of exported package names for files in allSourcePaths
     * @throws MavenReportException if any
     * @see #getFiles
     * @see #getSourcePaths()
     */
    private List<String> getPackageNamesRespectingJavaModules(Map<String, Collection<Path>> allSourcePaths) throws MavenReportException {
        List<String> returnList = new ArrayList<>();

        if (!StringUtils.isEmpty(sourcepath)) {
            return returnList;
        }

        for (Collection<Path> artifactSourcePaths : allSourcePaths.values()) {
            Set<String> exportedPackages = new HashSet<>();
            boolean exportAllPackages;
            File mainDescriptor = findMainDescriptor(artifactSourcePaths);
            if (mainDescriptor != null && !isTest()) {
                ResolvePathsRequest<File> request = ResolvePathsRequest.ofFiles(Collections.<File> emptyList()).setMainModuleDescriptor(mainDescriptor);

                try {
                    Set<JavaModuleDescriptor.JavaExports> exports = locationManager.resolvePaths(request).getMainModuleDescriptor().exports();
                    if (exports.isEmpty()) {
                        continue;
                    }
                    for (JavaModuleDescriptor.JavaExports export : exports) {
                        exportedPackages.add(export.source());
                    }
                } catch (IOException e) {
                    throw new MavenReportException(e.getMessage(), e);
                }
                exportAllPackages = false;
            } else {
                exportAllPackages = true;
            }

            for (Map.Entry<Path, Collection<String>> currentPathEntry : getFiles(artifactSourcePaths).entrySet()) {
                for (String currentFile : currentPathEntry.getValue()) {
                    /*
                     * Remove the miscellaneous files
                     * http://docs.oracle.com/javase/1.4.2/docs/tooldocs/solaris
                     * /javadoc.html#unprocessed
                     */
                    if (currentFile.contains("doc-files")) {
                        continue;
                    }

                    int lastIndexOfSeparator = currentFile.lastIndexOf(File.separatorChar);
                    if (lastIndexOfSeparator != -1) {
                        String packagename = currentFile.substring(0, lastIndexOfSeparator).replace(File.separatorChar, '.');

                        if (exportAllPackages || exportedPackages.contains(packagename)) {
                            returnList.add(packagename);
                        }
                    }
                }
            }
        }

        return returnList;
    }

    /**
     * @param sourcePaths could be null
     * @return a list files with unnamed package names for files in the
     *         sourcePaths
     */
    private List<String> getFilesWithUnnamedPackages(Map<Path, Collection<String>> sourcePaths) {
        List<String> returnList = new ArrayList<>();

        if (!StringUtils.isEmpty(sourcepath)) {
            return returnList;
        }

        for (Entry<Path, Collection<String>> currentPathEntry : sourcePaths.entrySet()) {
            Path currentSourcePath = currentPathEntry.getKey();

            for (String currentFile : currentPathEntry.getValue()) {
                /*
                 * Remove the miscellaneous files
                 * http://docs.oracle.com/javase/1.4.2/docs/tooldocs/solaris/
                 * javadoc.html#unprocessed
                 */
                if (currentFile.contains("doc-files")) {
                    continue;
                }

                if (currentFile.indexOf(File.separatorChar) == -1) {
                    returnList.add(currentSourcePath.resolve(currentFile).toAbsolutePath().toString());
                }
            }
        }

        return returnList;
    }

    /**
     * Either return only the module descriptor or all sourcefiles per
     * sourcepath
     * 
     * @param sourcePaths could be null
     * @return a list of files
     */
    private List<String> getSpecialFiles(Map<Path, Collection<String>> sourcePaths) {
        if (!StringUtils.isEmpty(sourcepath)) {
            return new ArrayList<>();
        }

        boolean containsModuleDescriptor = false;
        for (Collection<String> sourcepathFiles : sourcePaths.values()) {
            containsModuleDescriptor = sourcepathFiles.contains("module-info.java");
            if (containsModuleDescriptor) {
                break;
            }
        }

        if (containsModuleDescriptor) {
            return getModuleSourcePathFiles(sourcePaths);
        } else {
            return getFilesWithUnnamedPackages(sourcePaths);
        }
    }

    private List<String> getModuleSourcePathFiles(Map<Path, Collection<String>> sourcePaths) {
        List<String> returnList = new ArrayList<>();

        for (Entry<Path, Collection<String>> currentPathEntry : sourcePaths.entrySet()) {
            Path currentSourcePath = currentPathEntry.getKey();
            if (currentPathEntry.getValue().contains("module-info.java")) {
                returnList.add(currentSourcePath.resolve("module-info.java").toAbsolutePath().toString());
            } else {
                for (String currentFile : currentPathEntry.getValue()) {
                    /*
                     * Remove the miscellaneous files
                     * http://docs.oracle.com/javase/1.4.2/docs/tooldocs/solaris
                     * /javadoc.html#unprocessed
                     */
                    if (currentFile.contains("doc-files")) {
                        continue;
                    }

                    returnList.add(currentSourcePath.resolve(currentFile).toAbsolutePath().toString());
                }
            }
        }
        return returnList;
    }

    /**
     * Generate an <code>options</code> file for all options and arguments and
     * add the <code>@options</code> in the command line.
     *
     * @param cmd not null
     * @param arguments not null
     * @param javadocOutputDirectory not null
     * @throws MavenReportException if any
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#argumentfiles">
     *      Reference Guide, Command line argument files</a>
     * @see #OPTIONS_FILE_NAME
     */
    private void addCommandLineOptions(Commandline cmd, List<String> arguments, File javadocOutputDirectory) throws MavenReportException {
        File optionsFile = new File(javadocOutputDirectory, OPTIONS_FILE_NAME);

        StringBuilder options = new StringBuilder();
        options.append(StringUtils.join(arguments.iterator(), SystemUtils.LINE_SEPARATOR));

        /* default to platform encoding */
        String outputFileEncoding = null;
        if (JAVA_VERSION.isAtLeast("9")) {
            outputFileEncoding = StandardCharsets.UTF_8.name();
        }
        try {
            FileUtils.fileWrite(optionsFile.getAbsolutePath(), outputFileEncoding, options.toString());
        } catch (IOException e) {
            throw new MavenReportException("Unable to write '" + optionsFile.getName() + "' temporary file for command execution", e);
        }

        cmd.createArg().setValue("@" + OPTIONS_FILE_NAME);
    }

    /**
     * Generate a file called <code>argfile</code> (or <code>files</code>,
     * depending the JDK) to hold files and add the <code>@argfile</code> (or
     * <code>@file</code>, depending the JDK) in the command line.
     *
     * @param cmd not null
     * @param javadocOutputDirectory not null
     * @param files not null
     * @throws MavenReportException if any
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#argumentfiles">
     *      Reference Guide, Command line argument files </a>
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/whatsnew-1.4.html#runningjavadoc">
     *      What s New in Javadoc 1.4 </a>
     * @see #isJavaDocVersionAtLeast(JavaVersion)
     * @see #ARGFILE_FILE_NAME
     * @see #FILES_FILE_NAME
     */
    private void addCommandLineArgFile(Commandline cmd, File javadocOutputDirectory, List<String> files) throws MavenReportException {
        File argfileFile;
        if (JAVA_VERSION.compareTo(SINCE_JAVADOC_1_4) >= 0) {
            argfileFile = new File(javadocOutputDirectory, ARGFILE_FILE_NAME);
            cmd.createArg().setValue("@" + ARGFILE_FILE_NAME);
        } else {
            argfileFile = new File(javadocOutputDirectory, FILES_FILE_NAME);
            cmd.createArg().setValue("@" + FILES_FILE_NAME);
        }

        List<String> quotedFiles = new ArrayList<>(files.size());
        for (String file : files) {
            quotedFiles.add(JavadocUtil.quotedPathArgument(file));
        }

        try {
            FileUtils.fileWrite(argfileFile.getAbsolutePath(),
                                null /* platform encoding */, StringUtils.join(quotedFiles.iterator(), SystemUtils.LINE_SEPARATOR));
        } catch (IOException e) {
            throw new MavenReportException("Unable to write '" + argfileFile.getName() + "' temporary file for command execution", e);
        }
    }

    /**
     * Generate a file called <code>packages</code> to hold all package names
     * and add the <code>@packages</code> in the command line.
     *
     * @param cmd not null
     * @param javadocOutputDirectory not null
     * @param packageNames not null
     * @throws MavenReportException if any
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#argumentfiles">
     *      Reference Guide, Command line argument files</a>
     * @see #PACKAGES_FILE_NAME
     */
    private void addCommandLinePackages(Commandline cmd, File javadocOutputDirectory, List<String> packageNames) throws MavenReportException {
        File packagesFile = new File(javadocOutputDirectory, PACKAGES_FILE_NAME);

        try {
            FileUtils.fileWrite(packagesFile.getAbsolutePath(),
                                null /* platform encoding */, StringUtils.join(packageNames.iterator(), SystemUtils.LINE_SEPARATOR));
        } catch (IOException e) {
            throw new MavenReportException("Unable to write '" + packagesFile.getName() + "' temporary file for command execution", e);
        }

        cmd.createArg().setValue("@" + PACKAGES_FILE_NAME);
    }

    /**
     * Checks for the validity of the Javadoc options used by the user.
     *
     * @throws MavenReportException if error
     */
    private void validateJavadocOptions() throws MavenReportException {
        // encoding
        if (StringUtils.isNotEmpty(getEncoding()) && !JavadocUtil.validateEncoding(getEncoding())) {
            throw new MavenReportException("Unsupported option <encoding/> '" + getEncoding() + "'");
        }

        // locale
        if (StringUtils.isNotEmpty(this.locale)) {
            StringTokenizer tokenizer = new StringTokenizer(this.locale, "_");
            final int maxTokens = 3;
            if (tokenizer.countTokens() > maxTokens) {
                throw new MavenReportException("Unsupported option <locale/> '" + this.locale + "', should be language_country_variant.");
            }

            Locale localeObject = null;
            if (tokenizer.hasMoreTokens()) {
                String language = tokenizer.nextToken().toLowerCase(Locale.ENGLISH);
                if (!Arrays.asList(Locale.getISOLanguages()).contains(language)) {
                    throw new MavenReportException("Unsupported language '" + language + "' in option <locale/> '" + this.locale + "'");
                }
                localeObject = new Locale(language);

                if (tokenizer.hasMoreTokens()) {
                    String country = tokenizer.nextToken().toUpperCase(Locale.ENGLISH);
                    if (!Arrays.asList(Locale.getISOCountries()).contains(country)) {
                        throw new MavenReportException("Unsupported country '" + country + "' in option <locale/> '" + this.locale + "'");
                    }
                    localeObject = new Locale(language, country);

                    if (tokenizer.hasMoreTokens()) {
                        String variant = tokenizer.nextToken();
                        localeObject = new Locale(language, country, variant);
                    }
                }
            }

            if (localeObject == null) {
                throw new MavenReportException("Unsupported option <locale/> '" + this.locale + "', should be language_country_variant.");
            }

            this.locale = localeObject.toString();
            final List<Locale> availableLocalesList = Arrays.asList(Locale.getAvailableLocales());
            if (StringUtils.isNotEmpty(localeObject.getVariant()) && !availableLocalesList.contains(localeObject)) {
                StringBuilder sb = new StringBuilder();
                sb.append("Unsupported option <locale/> with variant '").append(this.locale);
                sb.append("'");

                localeObject = new Locale(localeObject.getLanguage(), localeObject.getCountry());
                this.locale = localeObject.toString();

                sb.append(", trying to use <locale/> without variant, i.e. '").append(this.locale).append("'");
                if (getLog().isWarnEnabled()) {
                    getLog().warn(sb.toString());
                }
            }

            if (!availableLocalesList.contains(localeObject)) {
                throw new MavenReportException("Unsupported option <locale/> '" + this.locale + "'");
            }
        }
    }

    /**
     * Checks for the validity of the Standard Doclet options. <br/>
     * For example, throw an exception if &lt;nohelp/&gt; and &lt;helpfile/&gt;
     * options are used together.
     *
     * @throws MavenReportException if error or conflict found
     */
    private void validateStandardDocletOptions() throws MavenReportException {
        // docencoding
        if (StringUtils.isNotEmpty(getDocencoding()) && !JavadocUtil.validateEncoding(getDocencoding())) {
            throw new MavenReportException("Unsupported option <docencoding/> '" + getDocencoding() + "'");
        }

        // charset
        if (StringUtils.isNotEmpty(getCharset()) && !JavadocUtil.validateEncoding(getCharset())) {
            throw new MavenReportException("Unsupported option <charset/> '" + getCharset() + "'");
        }

        // helpfile
        if (StringUtils.isNotEmpty(helpfile) && nohelp) {
            throw new MavenReportException("Option <nohelp/> conflicts with <helpfile/>");
        }

        // overview
        if ((getOverview() != null) && nooverview) {
            throw new MavenReportException("Option <nooverview/> conflicts with <overview/>");
        }

        // index
        if (splitindex && noindex) {
            throw new MavenReportException("Option <noindex/> conflicts with <splitindex/>");
        }

        // stylesheet
        if (StringUtils.isNotEmpty(stylesheet) && !(stylesheet.equalsIgnoreCase("maven") || stylesheet.equalsIgnoreCase("java"))) {
            throw new MavenReportException("Option <stylesheet/> supports only \"maven\" or \"java\" value.");
        }
    }

    /**
     * Add Standard Javadoc Options. <br/>
     * The <a href="package-summary.html#Standard_Javadoc_Options">package
     * documentation</a> details the Standard Javadoc Options wrapped by this
     * Plugin.
     *
     * @param javadocOutputDirectory not null
     * @param arguments not null
     * @param allSourcePaths not null
     * @throws MavenReportException if any
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#javadocoptions">http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#javadocoptions</a>
     */
    private void addJavadocOptions(File javadocOutputDirectory, List<String> arguments, Map<String, Collection<Path>> allSourcePaths, Set<OfflineLink> offlineLinks)
        throws MavenReportException {
        Collection<Path> sourcePaths = collect(allSourcePaths.values());

        validateJavadocOptions();

        // see com.sun.tools.javadoc.Start#parseAndExecute(String argv[])
        addArgIfNotEmpty(arguments, "-locale", JavadocUtil.quotedArgument(this.locale));

        // all options in alphabetical order

        if (old && isJavaDocVersionAtLeast(SINCE_JAVADOC_1_4)) {
            if (getLog().isWarnEnabled()) {
                getLog().warn("Javadoc 1.4+ doesn't support the -1.1 switch anymore. Ignore this option.");
            }
        } else {
            addArgIf(arguments, old, "-1.1");
        }

        addArgIfNotEmpty(arguments, "-bootclasspath", JavadocUtil.quotedPathArgument(getBootclassPath()));

        if (isJavaDocVersionAtLeast(SINCE_JAVADOC_1_5)) {
            addArgIf(arguments, breakiterator, "-breakiterator", SINCE_JAVADOC_1_5);
        }

        Map<String, MavenProject> reactorKeys = new HashMap<>(reactorProjects.size());
        for (MavenProject reactorProject : reactorProjects) {
            reactorKeys.put(ArtifactUtils.versionlessKey(reactorProject.getGroupId(), reactorProject.getArtifactId()), reactorProject);
        }

        Map<String, JavaModuleDescriptor> allModuleDescriptors = new HashMap<>();

        boolean supportModulePath = javadocRuntimeVersion.isAtLeast("9") && (source == null || JavaVersion.parse(source).isAtLeast("9"))
                                    && (release == null || JavaVersion.parse(release).isAtLeast("9"));

        if (supportModulePath) {
            for (Map.Entry<String, Collection<Path>> entry : allSourcePaths.entrySet()) {
                MavenProject entryProject = reactorKeys.get(entry.getKey());

                File artifactFile;
                if (entryProject != null) {
                    artifactFile = getArtifactFile(entryProject);
                } else {
                    artifactFile = project.getArtifactMap().get(entry.getKey()).getFile();
                }
                ResolvePathResult resolvePathResult = getResolvePathResult(artifactFile);

                if (resolvePathResult == null || resolvePathResult.getModuleNameSource() == ModuleNameSource.FILENAME) {
                    File moduleDescriptor = findMainDescriptor(entry.getValue());

                    if (moduleDescriptor != null) {
                        try {
                            allModuleDescriptors.put(entry.getKey(), locationManager.parseModuleDescriptor(moduleDescriptor).getModuleDescriptor());
                        } catch (IOException e) {
                            throw new MavenReportException(e.getMessage(), e);
                        }
                    }
                } else {
                    allModuleDescriptors.put(entry.getKey(), resolvePathResult.getModuleDescriptor());
                }
            }
        }

        Collection<String> additionalModules = new ArrayList<>();

        ResolvePathResult mainResolvePathResult = null;

        Map<String, Collection<Path>> patchModules = new HashMap<>();

        Path moduleSourceDir = null;
        if (supportModulePath && !allModuleDescriptors.isEmpty()) {
            Collection<String> unnamedProjects = new ArrayList<>();
            for (Map.Entry<String, Collection<Path>> projectSourcepaths : allSourcePaths.entrySet()) {
                MavenProject aggregatedProject = reactorKeys.get(projectSourcepaths.getKey());
                if (aggregatedProject != null && !"pom".equals(aggregatedProject.getPackaging())) {
                    ResolvePathResult result = null;

                    // Prefer jar over outputDirectory, since it may may contain
                    // an automatic module name
                    File artifactFile = getArtifactFile(aggregatedProject);
                    if (artifactFile != null) {
                        ResolvePathRequest<File> request = ResolvePathRequest.ofFile(artifactFile);
                        try {
                            result = locationManager.resolvePath(request);
                        } catch (RuntimeException e) {
                            // most likely an invalid module name based on
                            // filename
                            if (!"java.lang.module.FindException".equals(e.getClass().getName())) {
                                throw e;
                            }
                        } catch (IOException e) {
                            throw new MavenReportException(e.getMessage(), e);
                        }
                    } else {
                        File moduleDescriptor = findMainDescriptor(projectSourcepaths.getValue());

                        if (moduleDescriptor != null) {
                            try {
                                result = locationManager.parseModuleDescriptor(moduleDescriptor);
                            } catch (IOException e) {
                                throw new MavenReportException(e.getMessage(), e);
                            }
                        }
                    }

                    if (result != null && result.getModuleDescriptor() != null) {
                        moduleSourceDir = javadocOutputDirectory.toPath().resolve("src");
                        try {
                            moduleSourceDir = Files.createDirectories(moduleSourceDir);

                            additionalModules.add(result.getModuleDescriptor().name());

                            patchModules.put(result.getModuleDescriptor().name(), projectSourcepaths.getValue());

                            Path modulePath = moduleSourceDir.resolve(result.getModuleDescriptor().name());
                            if (!Files.isDirectory(modulePath)) {
                                Files.createDirectory(modulePath);
                            }
                        } catch (IOException e) {
                            throw new MavenReportException(e.getMessage(), e);
                        }
                    } else {
                        unnamedProjects.add(projectSourcepaths.getKey());
                    }

                    if (aggregatedProject.equals(getProject())) {
                        mainResolvePathResult = result;
                    }
                } else {
                    // todo
                    getLog().error("no reactor project: " + projectSourcepaths.getKey());
                }
            }

            if (!unnamedProjects.isEmpty()) {
                getLog().error("Creating an aggregated report for both named and unnamed modules is not possible.");
                getLog().error("Ensure that every module has a module descriptor or is a jar with a MANIFEST.MF " + "containing an Automatic-Module-Name.");
                getLog().error("Fix the following projects:");
                for (String unnamedProject : unnamedProjects) {
                    getLog().error(" - " + unnamedProject);
                }
                throw new MavenReportException("Aggregator report contains named and unnamed modules");
            }

            if (mainResolvePathResult != null && ModuleNameSource.MANIFEST.equals(mainResolvePathResult.getModuleNameSource())) {
                arguments.add("--add-modules");
                arguments.add("ALL-MODULE-PATH");
            }
        }

        // MJAVADOC-506
        boolean moduleDescriptorSource = false;
        for (Path sourcepath : sourcePaths) {
            if (Files.isRegularFile(sourcepath.resolve("module-info.java"))) {
                moduleDescriptorSource = true;
                break;
            }
        }

        final ModuleNameSource mainModuleNameSource;
        if (mainResolvePathResult != null) {
            mainModuleNameSource = mainResolvePathResult.getModuleNameSource();
        } else {
            mainModuleNameSource = null;
        }

        if (supportModulePath && !isTest()
            && (isAggregator() || ModuleNameSource.MODULEDESCRIPTOR.equals(mainModuleNameSource) || ModuleNameSource.MANIFEST.equals(mainModuleNameSource))) {
            List<File> pathElements = new ArrayList<>(getPathElements());
            File artifactFile = getArtifactFile(project);
            if (artifactFile != null) {
                pathElements.add(0, artifactFile);
            }

            ResolvePathsRequest<File> request = ResolvePathsRequest.ofFiles(pathElements);

            String mainModuleName = null;
            if (mainResolvePathResult != null) {
                request.setModuleDescriptor(mainResolvePathResult.getModuleDescriptor());
                mainModuleName = mainResolvePathResult.getModuleDescriptor().name();
            }

            request.setAdditionalModules(additionalModules);

            try {
                ResolvePathsResult<File> result = locationManager.resolvePaths(request);

                Set<File> modulePathElements = new HashSet<>(result.getModulepathElements().keySet());

                Collection<File> classPathElements = new ArrayList<>(result.getClasspathElements().size());

                for (File file : result.getClasspathElements()) {
                    if (file.isDirectory() && new File(file, "module-info.class").exists()) {
                        modulePathElements.add(file);
                    } else if (ModuleNameSource.MANIFEST.equals(mainModuleNameSource)) {
                        ModuleNameSource depModuleNameSource = locationManager.resolvePath(ResolvePathRequest.ofFile(file)).getModuleNameSource();
                        if (ModuleNameSource.MODULEDESCRIPTOR.equals(depModuleNameSource) || ModuleNameSource.MANIFEST.equals(depModuleNameSource)) {
                            modulePathElements.add(file);
                        } else {
                            patchModules.get(mainModuleName).add(file.toPath());
                        }
                    } else {
                        classPathElements.add(file);
                    }
                }

                /*
                 * MJAVADOC-620: also add all JARs where module-name-guessing
                 * leads to a FindException:
                 */
                for (Entry<File, Exception> pathExceptionEntry : result.getPathExceptions().entrySet()) {
                    Exception exception = pathExceptionEntry.getValue();
                    // For Java < 9 compatibility, reference FindException by
                    // name:
                    if ("java.lang.module.FindException".equals(exception.getClass().getName())) {
                        File jarPath = pathExceptionEntry.getKey();
                        classPathElements.add(jarPath);
                    }
                }

                String classpath = StringUtils.join(classPathElements.iterator(), File.pathSeparator);
                addArgIfNotEmpty(arguments, "--class-path", JavadocUtil.quotedPathArgument(classpath), false, false);

                String modulepath = StringUtils.join(modulePathElements.iterator(), File.pathSeparator);
                addArgIfNotEmpty(arguments, "--module-path", JavadocUtil.quotedPathArgument(modulepath), false, false);
            } catch (IOException e) {
                throw new MavenReportException(e.getMessage(), e);
            }
        } else if (supportModulePath && moduleDescriptorSource && !isTest()) {
            String modulepath = StringUtils.join(getPathElements().iterator(), File.pathSeparator);
            addArgIfNotEmpty(arguments, "--module-path", JavadocUtil.quotedPathArgument(modulepath), false, false);
        } else {
            String classpath = StringUtils.join(getPathElements().iterator(), File.pathSeparator);
            addArgIfNotEmpty(arguments, "-classpath", JavadocUtil.quotedPathArgument(classpath), false, false);
        }

        for (Entry<String, Collection<Path>> entry : patchModules.entrySet()) {
            addArgIfNotEmpty(arguments, "--patch-module", entry.getKey() + '=' + JavadocUtil.quotedPathArgument(getSourcePath(entry.getValue())), false, false);
        }

        if (StringUtils.isNotEmpty(doclet)) {
            addArgIfNotEmpty(arguments, "-doclet", JavadocUtil.quotedArgument(doclet));
            addArgIfNotEmpty(arguments, "-docletpath", JavadocUtil.quotedPathArgument(getDocletPath()));
        }

        if (StringUtils.isEmpty(encoding)) {
            getLog().warn("Source files encoding has not been set, using platform encoding " + ReaderFactory.FILE_ENCODING + ", i.e. build is platform dependent!");
        }
        addArgIfNotEmpty(arguments, "-encoding", JavadocUtil.quotedArgument(getEncoding()));

        addArgIfNotEmpty(arguments, "-extdirs", JavadocUtil.quotedPathArgument(JavadocUtil.unifyPathSeparator(extdirs)));

        if ((getOverview() != null) && (getOverview().exists())) {
            addArgIfNotEmpty(arguments, "-overview", JavadocUtil.quotedPathArgument(getOverview().getAbsolutePath()));
        }

        arguments.add(getAccessLevel());

        if (isJavaDocVersionAtLeast(SINCE_JAVADOC_1_5)) {
            addArgIf(arguments, quiet, "-quiet", SINCE_JAVADOC_1_5);
        }

        if (release != null) {
            arguments.add("--release");
            arguments.add(release);
        } else {
            addArgIfNotEmpty(arguments, "-source", JavadocUtil.quotedArgument(source), SINCE_JAVADOC_1_4);
        }

        if ((StringUtils.isEmpty(sourcepath)) && (StringUtils.isNotEmpty(subpackages))) {
            sourcepath = StringUtils.join(sourcePaths.iterator(), File.pathSeparator);
        }

        if (moduleSourceDir == null) {
            addArgIfNotEmpty(arguments, "-sourcepath", JavadocUtil.quotedPathArgument(getSourcePath(sourcePaths)), false, false);
        } else if (mainResolvePathResult == null || ModuleNameSource.MODULEDESCRIPTOR.equals(mainResolvePathResult.getModuleNameSource())) {
            addArgIfNotEmpty(arguments, "--module-source-path", JavadocUtil.quotedPathArgument(moduleSourceDir.toString()));
        }

        if (StringUtils.isNotEmpty(sourcepath) && isJavaDocVersionAtLeast(SINCE_JAVADOC_1_5)) {
            addArgIfNotEmpty(arguments, "-subpackages", subpackages, SINCE_JAVADOC_1_5);
        }

        // [MJAVADOC-497] must be after sourcepath is recalculated, since
        // getExcludedPackages() depends on it
        addArgIfNotEmpty(arguments, "-exclude", getExcludedPackages(sourcePaths), SINCE_JAVADOC_1_4);

        addArgIf(arguments, verbose, "-verbose");

        if (additionalOptions != null && additionalOptions.length > 0) {
            for (String additionalOption : additionalOptions) {
                arguments.add(additionalOption.replaceAll("(?<!\\\\)\\\\(?!\\\\|:)", "\\\\"));
            }
        }
    }

    private ResolvePathResult getResolvePathResult(File artifactFile) {
        if (artifactFile == null) {
            return null;
        }

        ResolvePathResult resolvePathResult = null;
        ResolvePathRequest<File> resolvePathRequest = ResolvePathRequest.ofFile(artifactFile);
        try {
            resolvePathResult = locationManager.resolvePath(resolvePathRequest);

            // happens when artifactFile is a directory without module
            // descriptor
            if (resolvePathResult.getModuleDescriptor() == null) {
                return null;
            }
        } catch (IOException
            | RuntimeException /* e.g java.lang.module.FindException */ e) {
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            getLog().warn(e.getMessage());
        }
        return resolvePathResult;
    }

    private File findMainDescriptor(Collection<Path> roots) throws MavenReportException {
        for (Map.Entry<Path, Collection<String>> entry : getFiles(roots).entrySet()) {
            if (entry.getValue().contains("module-info.java")) {
                return entry.getKey().resolve("module-info.java").toFile();
            }
        }
        return null;
    }

    /**
     * Add Standard Doclet Options. <br/>
     * The <a href="package-summary.html#Standard_Doclet_Options">package
     * documentation</a> details the Standard Doclet Options wrapped by this
     * Plugin.
     *
     * @param javadocOutputDirectory not null
     * @param arguments not null
     * @throws MavenReportException if any
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#standard">
     *      http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#standard</a>
     */
    private void addStandardDocletOptions(File javadocOutputDirectory, List<String> arguments, Set<OfflineLink> offlineLinks) throws MavenReportException {
        validateStandardDocletOptions();

        // all options in alphabetical order

        addArgIf(arguments, author, "-author");

        addArgIfNotEmpty(arguments, "-bottom", JavadocUtil.quotedArgument(getBottomText()), false, false);

        if (!isJavaDocVersionAtLeast(SINCE_JAVADOC_1_5)) {
            addArgIf(arguments, breakiterator, "-breakiterator", SINCE_JAVADOC_1_4);
        }

        addArgIfNotEmpty(arguments, "-charset", JavadocUtil.quotedArgument(getCharset()));

        addArgIfNotEmpty(arguments, "-d", JavadocUtil.quotedPathArgument(javadocOutputDirectory.toString()));

        addArgIfNotEmpty(arguments, "-docencoding", JavadocUtil.quotedArgument(getDocencoding()));

        addArgIf(arguments, docfilessubdirs, "-docfilessubdirs", SINCE_JAVADOC_1_4);

        addArgIf(arguments, StringUtils.isNotEmpty(doclint), "-Xdoclint:" + getDoclint(), SINCE_JAVADOC_1_8);

        addArgIfNotEmpty(arguments, "-doctitle", JavadocUtil.quotedArgument(getDoctitle()), false, false);

        if (docfilessubdirs) {
            addArgIfNotEmpty(arguments, "-excludedocfilessubdir", JavadocUtil.quotedPathArgument(excludedocfilessubdir), SINCE_JAVADOC_1_4);
        }

        addArgIfNotEmpty(arguments, "-footer", JavadocUtil.quotedArgument(footer), false, false);

        addGroups(arguments);

        addArgIfNotEmpty(arguments, "-header", JavadocUtil.quotedArgument(header), false, false);

        addArgIfNotEmpty(arguments, "-helpfile", JavadocUtil.quotedPathArgument(getHelpFile(javadocOutputDirectory)));

        addArgIf(arguments, keywords, "-keywords", SINCE_JAVADOC_1_4_2);

        addLinkArguments(arguments);

        addLinkofflineArguments(arguments, offlineLinks);

        addArgIf(arguments, linksource, "-linksource", SINCE_JAVADOC_1_4);

        if (sourcetab > 0) {
            if (javadocRuntimeVersion == SINCE_JAVADOC_1_4_2) {
                addArgIfNotEmpty(arguments, "-linksourcetab", String.valueOf(sourcetab));
            }
            addArgIfNotEmpty(arguments, "-sourcetab", String.valueOf(sourcetab), SINCE_JAVADOC_1_5);
        }

        addArgIf(arguments, nocomment, "-nocomment", SINCE_JAVADOC_1_4);

        addArgIf(arguments, nodeprecated, "-nodeprecated");

        addArgIf(arguments, nodeprecatedlist, "-nodeprecatedlist");

        addArgIf(arguments, nohelp, "-nohelp");

        addArgIf(arguments, noindex, "-noindex");

        addArgIf(arguments, nonavbar, "-nonavbar");

        addArgIf(arguments, nooverview, "-nooverview");

        addArgIfNotEmpty(arguments, "-noqualifier", JavadocUtil.quotedArgument(noqualifier), SINCE_JAVADOC_1_4);

        addArgIf(arguments, nosince, "-nosince");

        addArgIf(arguments, notimestamp, "-notimestamp", SINCE_JAVADOC_1_5);

        addArgIf(arguments, notree, "-notree");

        addArgIfNotEmpty(arguments, "-packagesheader", JavadocUtil.quotedArgument(packagesheader), SINCE_JAVADOC_1_4_2);

        if (!isJavaDocVersionAtLeast(SINCE_JAVADOC_1_5)) { // Sun bug: 4714350
            addArgIf(arguments, quiet, "-quiet", SINCE_JAVADOC_1_4);
        }

        addArgIf(arguments, serialwarn, "-serialwarn");

        addArgIf(arguments, splitindex, "-splitindex");

        addArgIfNotEmpty(arguments, "-stylesheetfile", JavadocUtil.quotedPathArgument(getStylesheetFile(javadocOutputDirectory)));

        if (StringUtils.isNotEmpty(sourcepath) && !isJavaDocVersionAtLeast(SINCE_JAVADOC_1_5)) {
            addArgIfNotEmpty(arguments, "-subpackages", subpackages, SINCE_JAVADOC_1_4);
        }

        addArgIfNotEmpty(arguments, "-taglet", JavadocUtil.quotedArgument(taglet), SINCE_JAVADOC_1_4);
        addTaglets(arguments);
        addTagletsFromTagletArtifacts(arguments);
        addArgIfNotEmpty(arguments, "-tagletpath", JavadocUtil.quotedPathArgument(getTagletPath()), SINCE_JAVADOC_1_4);

        addTags(arguments);

        addArgIfNotEmpty(arguments, "-top", JavadocUtil.quotedArgument(top), false, false, SINCE_JAVADOC_1_6);

        addArgIf(arguments, use, "-use");

        addArgIf(arguments, version, "-version");

        addArgIfNotEmpty(arguments, "-windowtitle", JavadocUtil.quotedArgument(getWindowtitle()), false, false);
    }

    /**
     * Add <code>groups</code> parameter to arguments.
     *
     * @param arguments not null
     * @throws MavenReportException
     */
    private void addGroups(List<String> arguments) throws MavenReportException {
        Set<Group> groups = collectGroups();
        if (isEmpty(groups)) {
            return;
        }

        for (Group group : groups) {
            if (group == null || StringUtils.isEmpty(group.getTitle()) || StringUtils.isEmpty(group.getPackages())) {
                if (getLog().isWarnEnabled()) {
                    getLog().warn("A group option is empty. Ignore this option.");
                }
            } else {
                String groupTitle = StringUtils.replace(group.getTitle(), ",", "&#44;");
                addArgIfNotEmpty(arguments, "-group", JavadocUtil.quotedArgument(groupTitle) + " " + JavadocUtil.quotedArgument(group.getPackages()), true);
            }
        }
    }

    /**
     * Add <code>tags</code> parameter to arguments.
     *
     * @param arguments not null
     * @throws MavenReportException
     */
    private void addTags(List<String> arguments) throws MavenReportException {
        Set<Tag> tags = collectTags();

        if (isEmpty(tags)) {
            return;
        }

        for (Tag tag : tags) {
            if (StringUtils.isEmpty(tag.getName())) {
                if (getLog().isWarnEnabled()) {
                    getLog().warn("A tag name is empty. Ignore this option.");
                }
            } else {
                String value = "\"" + tag.getName();
                if (StringUtils.isNotEmpty(tag.getPlacement())) {
                    value += ":" + tag.getPlacement();
                    if (StringUtils.isNotEmpty(tag.getHead())) {
                        value += ":" + tag.getHead();
                    }
                }
                value += "\"";
                addArgIfNotEmpty(arguments, "-tag", value, SINCE_JAVADOC_1_4);
            }
        }
    }

    /**
     * Add <code>taglets</code> parameter to arguments.
     *
     * @param arguments not null
     */
    private void addTaglets(List<String> arguments) {
        if (taglets == null) {
            return;
        }

        for (Taglet taglet1 : taglets) {
            if ((taglet1 == null) || (StringUtils.isEmpty(taglet1.getTagletClass()))) {
                if (getLog().isWarnEnabled()) {
                    getLog().warn("A taglet option is empty. Ignore this option.");
                }
            } else {
                addArgIfNotEmpty(arguments, "-taglet", JavadocUtil.quotedArgument(taglet1.getTagletClass()), SINCE_JAVADOC_1_4);
            }
        }
    }

    /**
     * Auto-detect taglets class name from <code>tagletArtifacts</code> and add
     * them to arguments.
     *
     * @param arguments not null
     * @throws MavenReportException if any
     * @see JavadocUtil#getTagletClassNames(File)
     */
    private void addTagletsFromTagletArtifacts(List<String> arguments) throws MavenReportException {
        Set<TagletArtifact> tArtifacts = new LinkedHashSet<>();
        if (tagletArtifacts != null && tagletArtifacts.length > 0) {
            tArtifacts.addAll(Arrays.asList(tagletArtifacts));
        }

        if (includeDependencySources) {
            try {
                resolveDependencyBundles();
            } catch (IOException e) {
                throw new MavenReportException("Failed to resolve javadoc bundles from dependencies: " + e.getMessage(), e);
            }

            if (isNotEmpty(dependencyJavadocBundles)) {
                for (JavadocBundle bundle : dependencyJavadocBundles) {
                    JavadocOptions options = bundle.getOptions();
                    if (options != null && isNotEmpty(options.getTagletArtifacts())) {
                        tArtifacts.addAll(options.getTagletArtifacts());
                    }
                }
            }
        }

        if (isEmpty(tArtifacts)) {
            return;
        }

        List<String> tagletsPath = new ArrayList<>();

        for (TagletArtifact aTagletArtifact : tArtifacts) {
            if ((StringUtils.isNotEmpty(aTagletArtifact.getGroupId())) && (StringUtils.isNotEmpty(aTagletArtifact.getArtifactId()))
                && (StringUtils.isNotEmpty(aTagletArtifact.getVersion()))) {
                Artifact artifact;
                try {
                    artifact = createAndResolveArtifact(aTagletArtifact);
                } catch (ArtifactResolverException e) {
                    throw new MavenReportException("Unable to resolve artifact:" + aTagletArtifact, e);
                }

                tagletsPath.add(artifact.getFile().getAbsolutePath());
            }
        }

        tagletsPath = JavadocUtil.pruneFiles(tagletsPath);

        for (String tagletJar : tagletsPath) {
            if (!tagletJar.toLowerCase(Locale.ENGLISH).endsWith(".jar")) {
                continue;
            }

            List<String> tagletClasses;
            try {
                tagletClasses = JavadocUtil.getTagletClassNames(new File(tagletJar));
            } catch (IOException e) {
                if (getLog().isWarnEnabled()) {
                    getLog().warn("Unable to auto-detect Taglet class names from '" + tagletJar + "'. Try to specify them with <taglets/>.");
                }
                if (getLog().isDebugEnabled()) {
                    getLog().debug("IOException: " + e.getMessage(), e);
                }
                continue;
            } catch (ClassNotFoundException e) {
                if (getLog().isWarnEnabled()) {
                    getLog().warn("Unable to auto-detect Taglet class names from '" + tagletJar + "'. Try to specify them with <taglets/>.");
                }
                if (getLog().isDebugEnabled()) {
                    getLog().debug("ClassNotFoundException: " + e.getMessage(), e);
                }
                continue;
            } catch (NoClassDefFoundError e) {
                if (getLog().isWarnEnabled()) {
                    getLog().warn("Unable to auto-detect Taglet class names from '" + tagletJar + "'. Try to specify them with <taglets/>.");
                }
                if (getLog().isDebugEnabled()) {
                    getLog().debug("NoClassDefFoundError: " + e.getMessage(), e);
                }
                continue;
            }

            if (tagletClasses != null && !tagletClasses.isEmpty()) {
                for (String tagletClass : tagletClasses) {
                    addArgIfNotEmpty(arguments, "-taglet", JavadocUtil.quotedArgument(tagletClass), SINCE_JAVADOC_1_4);
                }
            }
        }
    }

    /**
     * Execute the Javadoc command line
     *
     * @param cmd not null
     * @param javadocOutputDirectory not null
     * @throws MavenReportException if any errors occur
     */
    private void executeJavadocCommandLine(Commandline cmd, File javadocOutputDirectory) throws MavenReportException {
        if (staleDataPath != null) {
            if (!isUpToDate(cmd)) {
                doExecuteJavadocCommandLine(cmd, javadocOutputDirectory);
                StaleHelper.writeStaleData(cmd, staleDataPath.toPath());
            }
        } else {
            doExecuteJavadocCommandLine(cmd, javadocOutputDirectory);
        }
    }

    /**
     * Check if the javadoc is uptodate or not
     *
     * @param cmd not null
     * @return <code>true</code> is the javadoc is uptodate, <code>false</code>
     *         otherwise
     * @throws MavenReportException if any error occur
     */
    private boolean isUpToDate(Commandline cmd) throws MavenReportException {
        try {
            String curdata = StaleHelper.getStaleData(cmd);
            Path cacheData = staleDataPath.toPath();
            String prvdata;
            if (Files.isRegularFile(cacheData)) {
                prvdata = new String(Files.readAllBytes(cacheData), StandardCharsets.UTF_8);
            } else {
                prvdata = null;
            }
            if (curdata.equals(prvdata)) {
                getLog().info("Skipping javadoc generation, everything is up to date.");
                return true;
            } else {
                if (prvdata == null) {
                    getLog().info("No previous run data found, generating javadoc.");
                } else {
                    getLog().info("Configuration changed, re-generating javadoc.");
                }
            }
        } catch (IOException e) {
            throw new MavenReportException("Error checking uptodate status", e);
        }
        return false;
    }

    /**
     * Execute the Javadoc command line
     *
     * @param cmd not null
     * @param javadocOutputDirectory not null
     * @throws MavenReportException if any errors occur
     */
    private void doExecuteJavadocCommandLine(Commandline cmd, File javadocOutputDirectory) throws MavenReportException {
        if (getLog().isDebugEnabled()) {
            // no quoted arguments
            getLog().debug(CommandLineUtils.toString(cmd.getCommandline()).replaceAll("'", ""));
        }

        String cmdLine = null;
        if (debug) {
            cmdLine = CommandLineUtils.toString(cmd.getCommandline()).replaceAll("'", "");

            writeDebugJavadocScript(cmdLine, javadocOutputDirectory);
        }

        CommandLineUtils.StringStreamConsumer err = new JavadocUtil.JavadocOutputStreamConsumer();
        CommandLineUtils.StringStreamConsumer out = new JavadocUtil.JavadocOutputStreamConsumer();
        try {
            int exitCode = CommandLineUtils.executeCommandLine(cmd, out, err);

            String output = StringUtils.isEmpty(out.getOutput()) ? null : '\n' + out.getOutput().trim();

            if (exitCode != 0) {
                if (cmdLine == null) {
                    cmdLine = CommandLineUtils.toString(cmd.getCommandline()).replaceAll("'", "");
                }
                writeDebugJavadocScript(cmdLine, javadocOutputDirectory);

                if (StringUtils.isNotEmpty(output) && StringUtils.isEmpty(err.getOutput()) && isJavadocVMInitError(output)) {
                    throw new MavenReportException(output + '\n' + '\n' + JavadocUtil.ERROR_INIT_VM + '\n' + "Or, try to reduce the Java heap size for the Javadoc goal using "
                                                   + "-Dminmemory=<size> and -Dmaxmemory=<size>." + '\n' + '\n' + "Command line was: " + cmdLine + '\n' + '\n'
                                                   + "Refer to the generated Javadoc files in '" + javadocOutputDirectory + "' dir.\n");
                }

                if (StringUtils.isNotEmpty(output)) {
                    getLog().info(output);
                }

                StringBuilder msg = new StringBuilder("\nExit code: ");
                msg.append(exitCode);
                if (StringUtils.isNotEmpty(err.getOutput())) {
                    msg.append(" - ").append(err.getOutput());
                }
                msg.append('\n');
                msg.append("Command line was: ").append(cmdLine).append('\n').append('\n');

                msg.append("Refer to the generated Javadoc files in '").append(javadocOutputDirectory).append("' dir.\n");

                throw new MavenReportException(msg.toString());
            }

            if (StringUtils.isNotEmpty(output)) {
                getLog().info(output);
            }
        } catch (CommandLineException e) {
            throw new MavenReportException("Unable to execute javadoc command: " + e.getMessage(), e);
        }

        // ----------------------------------------------------------------------
        // Handle Javadoc warnings
        // ----------------------------------------------------------------------

        if (StringUtils.isNotEmpty(err.getOutput()) && getLog().isWarnEnabled()) {
            getLog().warn("Javadoc Warnings");

            StringTokenizer token = new StringTokenizer(err.getOutput(), "\n");
            while (token.hasMoreTokens()) {
                String current = token.nextToken().trim();

                getLog().warn(current);
            }
        }

        if (StringUtils.isNotEmpty(err.getOutput()) && failOnWarnings) {
            throw new MavenReportException("Project contains Javadoc Warnings");
        }
    }

    /**
     * Patches the given Javadoc output directory to work around CVE-2013-1571
     * (see http://www.kb.cert.org/vuls/id/225657).
     *
     * @param javadocOutputDirectory directory to scan for vulnerabilities
     * @param outputEncoding encoding used by the javadoc tool (-docencoding
     *            parameter). If {@code null}, the platform's default encoding
     *            is used (like javadoc does).
     * @return the number of patched files
     */
    private int fixFrameInjectionBug(File javadocOutputDirectory, String outputEncoding) throws IOException {
        final String fixData;
        InputStream in = null;
        try {
            in = this.getClass().getResourceAsStream("frame-injection-fix.txt");
            if (in == null) {
                throw new FileNotFoundException("Missing resource 'frame-injection-fix.txt' in classpath.");
            }
            fixData = StringUtils.unifyLineSeparators(IOUtil.toString(in, "US-ASCII")).trim();
            in.close();
            in = null;
        } finally {
            IOUtil.close(in);
        }

        final DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(javadocOutputDirectory);
        ds.setCaseSensitive(false);
        ds.setIncludes(new String[] {"**/index.html", "**/index.htm", "**/toc.html", "**/toc.htm"});
        ds.addDefaultExcludes();
        ds.scan();
        int patched = 0;
        for (String f : ds.getIncludedFiles()) {
            final File file = new File(javadocOutputDirectory, f);
            // we load the whole file as one String (toc/index files are
            // generally small, because they only contain frameset declaration):
            final String fileContents = FileUtils.fileRead(file, outputEncoding);
            // check if file may be vulnerable because it was not patched with
            // "validURL(url)":
            if (!StringUtils.contains(fileContents, "function validURL(url) {")) {
                // we need to patch the file!
                final String patchedFileContents = StringUtils.replaceOnce(fileContents, "function loadFrames() {", fixData);
                if (!patchedFileContents.equals(fileContents)) {
                    FileUtils.fileWrite(file, outputEncoding, patchedFileContents);
                    patched++;
                }
            }
        }
        return patched;
    }

    /**
     * @param outputFile not nul
     * @param inputResourceName a not null resource in
     *            <code>src/main/java</code>, <code>src/main/resources</code> or
     *            <code>src/main/javadoc</code> or in the Javadoc plugin
     *            dependencies.
     * @return the resource file absolute path as String
     * @since 2.6
     */
    private String getResource(File outputFile, String inputResourceName) {
        if (inputResourceName.startsWith("/")) {
            inputResourceName = inputResourceName.replaceFirst("//*", "");
        }

        List<String> classPath = new ArrayList<>();
        classPath.add(project.getBuild().getSourceDirectory());

        URL resourceURL = getResource(classPath, inputResourceName);
        if (resourceURL != null) {
            getLog().debug(inputResourceName + " found in the main src directory of the project.");
            return FileUtils.toFile(resourceURL).getAbsolutePath();
        }

        classPath.clear();
        List<Resource> resources = project.getBuild().getResources();
        for (Resource resource : resources) {
            classPath.add(resource.getDirectory());
        }
        resourceURL = getResource(classPath, inputResourceName);
        if (resourceURL != null) {
            getLog().debug(inputResourceName + " found in the main resources directories of the project.");
            return FileUtils.toFile(resourceURL).getAbsolutePath();
        }

        if (javadocDirectory.exists()) {
            classPath.clear();
            classPath.add(javadocDirectory.getAbsolutePath());
            resourceURL = getResource(classPath, inputResourceName);
            if (resourceURL != null) {
                getLog().debug(inputResourceName + " found in the main javadoc directory of the project.");
                return FileUtils.toFile(resourceURL).getAbsolutePath();
            }
        }

        classPath.clear();
        final String pluginId = "org.apache.maven.plugins:maven-javadoc-plugin";
        Plugin javadocPlugin = getPlugin(project, pluginId);
        if (javadocPlugin != null && javadocPlugin.getDependencies() != null) {
            List<Dependency> dependencies = javadocPlugin.getDependencies();
            for (Dependency dependency : dependencies) {
                JavadocPathArtifact javadocPathArtifact = new JavadocPathArtifact();
                javadocPathArtifact.setGroupId(dependency.getGroupId());
                javadocPathArtifact.setArtifactId(dependency.getArtifactId());
                javadocPathArtifact.setVersion(dependency.getVersion());
                Artifact artifact = null;
                try {
                    artifact = createAndResolveArtifact(javadocPathArtifact);
                } catch (Exception e) {
                    logError("Unable to retrieve the dependency: " + dependency + ". Ignored.", e);
                }

                if (artifact != null && artifact.getFile().exists()) {
                    classPath.add(artifact.getFile().getAbsolutePath());
                }
            }
            resourceURL = getResource(classPath, inputResourceName);
            if (resourceURL != null) {
                getLog().debug(inputResourceName + " found in javadoc plugin dependencies.");
                try {
                    JavadocUtil.copyResource(resourceURL, outputFile);

                    return outputFile.getAbsolutePath();
                } catch (IOException e) {
                    logError("IOException: " + e.getMessage(), e);
                }
            }
        }

        getLog().warn("Unable to find the resource '" + inputResourceName + "'. Using default Javadoc resources.");

        return null;
    }

    /**
     * @param classPath a not null String list of files where resource will be
     *            look up.
     * @param resource a not null ressource to find in the class path.
     * @return the resource from the given classpath or null if not found
     * @see ClassLoader#getResource(String)
     * @since 2.6
     */
    private URL getResource(final List<String> classPath, final String resource) {
        List<URL> urls = new ArrayList<>(classPath.size());
        for (String filename : classPath) {
            try {
                urls.add(new File(filename).toURL());
            } catch (MalformedURLException e) {
                getLog().error("MalformedURLException: " + e.getMessage());
            }
        }

        ClassLoader javadocClassLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]), null);

        return javadocClassLoader.getResource(resource);
    }

    /**
     * Get the full javadoc goal. Loads the plugin's pom.properties to get the
     * current plugin version.
     *
     * @return <code>org.apache.maven.plugins:maven-javadoc-plugin:CURRENT_VERSION:[test-]javadoc</code>
     */
    private String getFullJavadocGoal() {
        String javadocPluginVersion = null;
        InputStream resourceAsStream = null;
        try {
            String resource = "META-INF/maven/org.apache.maven.plugins/maven-javadoc-plugin/pom.properties";
            resourceAsStream = AbstractJavadocMojo.class.getClassLoader().getResourceAsStream(resource);

            if (resourceAsStream != null) {
                Properties properties = new Properties();
                properties.load(resourceAsStream);
                resourceAsStream.close();
                resourceAsStream = null;
                if (StringUtils.isNotEmpty(properties.getProperty("version"))) {
                    javadocPluginVersion = properties.getProperty("version");
                }
            }
        } catch (IOException e) {
            // nop
        } finally {
            IOUtil.close(resourceAsStream);
        }

        StringBuilder sb = new StringBuilder();

        sb.append("org.apache.maven.plugins:maven-javadoc-plugin:");
        if (StringUtils.isNotEmpty(javadocPluginVersion)) {
            sb.append(javadocPluginVersion).append(":");
        }

        if (this instanceof TestJavadocReport) {
            sb.append("test-javadoc");
        } else {
            sb.append("javadoc");
        }

        return sb.toString();
    }

    /**
     * Using Maven, a Javadoc link is given by
     * <code>${project.url}/apidocs</code>.
     *
     * @return the detected Javadoc links using the Maven conventions for all
     *         modules defined in the current project or an empty list.
     * @throws MavenReportException if any
     * @see #detectOfflineLinks
     * @see #reactorProjects
     * @since 2.6
     */
    private List<OfflineLink> getModulesLinks() throws MavenReportException {
        if (!detectOfflineLinks || isAggregator() || reactorProjects == null) {
            return Collections.emptyList();
        }

        getLog().debug("Trying to add links for modules...");

        Set<String> dependencyArtifactIds = new HashSet<>();
        final Set<Artifact> dependencyArtifacts = project.getDependencyArtifacts();
        for (Artifact artifact : dependencyArtifacts) {
            dependencyArtifactIds.add(artifact.getId());
        }

        List<OfflineLink> modulesLinks = new ArrayList<>();
        String javadocDirRelative = PathUtils.toRelative(project.getBasedir(), getOutputDirectory());
        for (MavenProject p : reactorProjects) {
            if (!dependencyArtifactIds.contains(p.getArtifact().getId()) || (p.getUrl() == null)) {
                continue;
            }

            File location = new File(p.getBasedir(), javadocDirRelative);

            if (!location.exists()) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Javadoc directory not found: " + location);
                }

                String javadocGoal = getFullJavadocGoal();
                getLog().info("The goal '" + javadocGoal + "' has not been previously called for the module: '" + p.getId() + "'. Trying to invoke it...");

                File invokerDir = new File(project.getBuild().getDirectory(), "invoker");
                invokerDir.mkdirs();
                File invokerLogFile = FileUtils.createTempFile("maven-javadoc-plugin", ".txt", invokerDir);
                try {
                    JavadocUtil.invokeMaven(getLog(), new File(localRepository.getBasedir()), p.getFile(), Collections.singletonList(javadocGoal), null, invokerLogFile);
                } catch (MavenInvocationException e) {
                    logError("MavenInvocationException: " + e.getMessage(), e);

                    String invokerLogContent = JavadocUtil
                        .readFile(invokerLogFile, null);

                    // TODO: Why are we only interested in cases where the JVM
                    // won't start?
                    // [MJAVADOC-275][jdcasey] I changed the logic here to only
                    // throw an error WHEN
                    // the JVM won't start (opposite of what it was).
                    if (invokerLogContent != null && invokerLogContent.contains(JavadocUtil.ERROR_INIT_VM)) {
                        throw new MavenReportException(e.getMessage(), e);
                    }
                } finally {
                    // just create the directory to prevent repeated
                    // invocations..
                    if (!location.exists()) {
                        getLog().warn("Creating fake javadoc directory to prevent repeated invocations: " + location);
                        location.mkdirs();
                    }
                }
            }

            if (location.exists()) {
                String url = getJavadocLink(p);

                OfflineLink ol = new OfflineLink();
                ol.setUrl(url);
                ol.setLocation(location.getAbsolutePath());

                if (getLog().isDebugEnabled()) {
                    getLog().debug("Added Javadoc offline link: " + url + " for the module: " + p.getId());
                }

                modulesLinks.add(ol);
            }
        }

        return modulesLinks;
    }

    /**
     * Using Maven, a Javadoc link is given by
     * <code>${project.url}/apidocs</code>.
     *
     * @return the detected Javadoc links using the Maven conventions for all
     *         dependencies defined in the current project or an empty list.
     * @see #detectLinks
     * @see #isValidJavadocLink
     * @since 2.6
     */
    private List<String> getDependenciesLinks() {
        if (!detectLinks) {
            return Collections.emptyList();
        }

        getLog().debug("Trying to add links for dependencies...");

        List<String> dependenciesLinks = new ArrayList<>();

        final Set<Artifact> dependencies = project.getDependencyArtifacts();
        for (Artifact artifact : dependencies) {
            if (artifact.getFile() == null || !artifact.getFile().exists()) {
                continue;
            }

            try {
                MavenProject artifactProject = mavenProjectBuilder.build(artifact, getProjectBuildingRequest(project)).getProject();

                if (StringUtils.isNotEmpty(artifactProject.getUrl())) {
                    String url = getJavadocLink(artifactProject);

                    if (isValidJavadocLink(url, true)) {
                        getLog().debug("Added Javadoc link: " + url + " for " + artifactProject.getId());

                        dependenciesLinks.add(url);
                    }
                }
            } catch (ProjectBuildingException e) {
                logError("ProjectBuildingException for " + artifact.toString() + ": " + e.getMessage(), e);
            }
        }

        return dependenciesLinks;
    }

    /**
     * @return if {@link #detectJavaApiLink}, the Java API link based on the
     *         {@link #javaApiLinks} properties and the value of the
     *         <code>source</code> parameter in the
     *         <code>org.apache.maven.plugins:maven-compiler-plugin</code>
     *         defined in <code>${project.build.plugins}</code> or in
     *         <code>${project.build.pluginManagement}</code>, or the
     *         {@link #javadocRuntimeVersion}, or <code>null</code> if not
     *         defined.
     * @see #detectJavaApiLink
     * @see #javaApiLinks
     * @see <a href=
     *      "http://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html#source">source
     *      parameter</a>
     * @since 2.6
     */
    protected final OfflineLink getDefaultJavadocApiLink() {
        if (!detectJavaApiLink) {
            return null;
        }

        final JavaVersion javaApiversion;
        if (release != null) {
            javaApiversion = JavaVersion.parse(release);
        } else {
            final String pluginId = "org.apache.maven.plugins:maven-compiler-plugin";
            String sourceConfigured = getPluginParameter(project, pluginId, "source");
            if (sourceConfigured != null) {
                javaApiversion = JavaVersion.parse(sourceConfigured);
            } else {
                getLog().debug("No maven-compiler-plugin defined in ${build.plugins} or in " + "${project.build.pluginManagement} for the " + project.getId()
                               + ". Added Javadoc API link according the javadoc executable version i.e.: " + javadocRuntimeVersion);

                javaApiversion = javadocRuntimeVersion;
            }
        }

        final String javaApiKey;
        if (javaApiversion.asMajor().isAtLeast("9")) {
            javaApiKey = "api_" + javaApiversion.asMajor();
        } else {
            javaApiKey = "api_1." + javaApiversion.asMajor().toString().charAt(0);
        }

        final String javaApiLink;
        if (javaApiLinks != null && javaApiLinks.containsKey(javaApiKey)) {
            javaApiLink = javaApiLinks.getProperty(javaApiKey);
        } else if (javaApiversion.isAtLeast("11")) {
            javaApiLink = String.format("https://docs.oracle.com/en/java/javase/%s/docs/api/", javaApiversion.getValue(1));
        } else if (javaApiversion.asMajor().isAtLeast("6")) {
            javaApiLink = String.format("https://docs.oracle.com/javase/%s/docs/api/", javaApiversion.asMajor().getValue(1));
        } else if (javaApiversion.isAtLeast("1.5")) {
            javaApiLink = "https://docs.oracle.com/javase/1.5.0/docs/api/";
        } else {
            javaApiLink = null;
        }

        if (getLog().isDebugEnabled()) {
            if (javaApiLink != null) {
                getLog().debug("Found Java API link: " + javaApiLink);
            } else {
                getLog().debug("No Java API link found.");
            }
        }

        if (javaApiLink == null) {
            return null;
        }

        final Path javaApiListFile;
        final String resourceName;
        if (javaApiversion.isAtLeast("10")) {
            javaApiListFile = getJavadocOptionsFile().getParentFile().toPath().resolve("element-list");
            resourceName = "java-api-element-list-" + javaApiversion.toString().substring(0, 2);
        } else if (javaApiversion.asMajor().isAtLeast("9")) {
            javaApiListFile = getJavadocOptionsFile().getParentFile().toPath().resolve("package-list");
            resourceName = "java-api-package-list-9";
        } else {
            javaApiListFile = getJavadocOptionsFile().getParentFile().toPath().resolve("package-list");
            resourceName = "java-api-package-list-1." + javaApiversion.asMajor().toString().charAt(0);
        }

        OfflineLink link = new OfflineLink();
        link.setLocation(javaApiListFile.getParent().toAbsolutePath().toString());
        link.setUrl(javaApiLink);

        InputStream in = this.getClass().getResourceAsStream(resourceName);
        if (in != null) {
            try (InputStream closableIS = in) {
                // TODO only copy when changed
                Files.copy(closableIS, javaApiListFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ioe) {
                logError("Can't get " + resourceName + ": " + ioe.getMessage(), ioe);
                return null;
            }
        }

        return link;
    }

    /**
     * Follows all of the given links, and returns their last redirect
     * locations. Ordering is kept. This is necessary because javadoc tool
     * doesn't follow links, see JDK-8190312 (MJAVADOC-427, MJAVADOC-487)
     *
     * @param links Links to follow.
     * @return Last redirect location of all the links.
     */
    private Set<String> followLinks(Set<String> links) {
        Set<String> redirectLinks = new LinkedHashSet<>(links.size());
        for (String link : links) {
            try {
                redirectLinks.add(JavadocUtil.getRedirectUrl(new URI(link).toURL(), settings).toString());
            } catch (Exception e) {
                // only print in debug, it should have been logged already in
                // warn/error because link isn't valid
                getLog().debug("Could not follow " + link + ". Reason: " + e.getMessage());

                // Even when link produces error it should be kept in the set
                // because the error might be caused by
                // incomplete redirect configuration on the server side.
                // This partially restores the previous behaviour before fix for
                // MJAVADOC-427
                redirectLinks.add(link);
            }
        }
        return redirectLinks;
    }

    /**
     * @param link not null
     * @param detecting <code>true</code> if the link is generated by
     *            <code>detectLinks</code>, or <code>false</code> otherwise
     * @return <code>true</code> if the link has a <code>/package-list</code>,
     *         <code>false</code> otherwise.
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/tools/solaris/javadoc.html#package-list">
     *      package-list spec</a>
     * @since 2.6
     */
    protected boolean isValidJavadocLink(String link, boolean detecting) {
        try {
            final URI packageListUri;
            final URI elementListUri;

            if (link.trim().toLowerCase(Locale.ENGLISH).startsWith("http:") || link.trim().toLowerCase(Locale.ENGLISH).startsWith("https:")
                || link.trim().toLowerCase(Locale.ENGLISH).startsWith("ftp:") || link.trim().toLowerCase(Locale.ENGLISH).startsWith("file:")) {
                packageListUri = new URI(link + '/' + PACKAGE_LIST);
                elementListUri = new URI(link + '/' + ELEMENT_LIST);
            } else {
                // links can be relative paths or files
                File dir = new File(link);
                if (!dir.isAbsolute()) {
                    dir = new File(getOutputDirectory(), link);
                }
                if (!dir.isDirectory()) {
                    if (detecting) {
                        getLog().warn("The given File link: " + dir + " is not a dir.");
                    } else {
                        getLog().error("The given File link: " + dir + " is not a dir.");
                    }
                }
                packageListUri = new File(dir, PACKAGE_LIST).toURI();
                elementListUri = new File(dir, ELEMENT_LIST).toURI();
            }

            IOException elementListIOException = null;
            try {
                if (JavadocUtil.isValidElementList(elementListUri.toURL(), settings, validateLinks)) {
                    return true;
                }
            } catch (IOException e) {
                elementListIOException = e;
            }

            if (JavadocUtil.isValidPackageList(packageListUri.toURL(), settings, validateLinks)) {
                return true;
            }

            if (getLog().isErrorEnabled()) {
                if (detecting) {
                    getLog().warn("Invalid links: " + link + " with /" + PACKAGE_LIST + " or / " + ELEMENT_LIST + ". Ignored it.");
                } else {
                    getLog().error("Invalid links: " + link + " with /" + PACKAGE_LIST + " or / " + ELEMENT_LIST + ". Ignored it.");
                }
            }

            return false;
        } catch (URISyntaxException e) {
            if (getLog().isErrorEnabled()) {
                if (detecting) {
                    getLog().warn("Malformed link: " + e.getInput() + ". Ignored it.");
                } else {
                    getLog().error("Malformed link: " + e.getInput() + ". Ignored it.");
                }
            }
            return false;
        } catch (IOException e) {
            if (getLog().isErrorEnabled()) {
                if (detecting) {
                    getLog().warn("Error fetching link: " + link + ". Ignored it.");
                } else {
                    getLog().error("Error fetching link: " + link + ". Ignored it.");
                }
            }
            return false;
        }
    }

    /**
     * Write a debug javadoc script in case of command line error or in debug
     * mode.
     *
     * @param cmdLine the current command line as string, not null.
     * @param javadocOutputDirectory the output dir, not null.
     * @see #executeJavadocCommandLine(Commandline, File)
     * @since 2.6
     */
    private void writeDebugJavadocScript(String cmdLine, File javadocOutputDirectory) {
        File commandLineFile = new File(javadocOutputDirectory, DEBUG_JAVADOC_SCRIPT_NAME);
        commandLineFile.getParentFile().mkdirs();

        try {
            FileUtils.fileWrite(commandLineFile.getAbsolutePath(),
                                null /* platform encoding */, cmdLine);

            if (!SystemUtils.IS_OS_WINDOWS) {
                Runtime.getRuntime().exec(new String[] {"chmod", "a+x", commandLineFile.getAbsolutePath()});
            }
        } catch (IOException e) {
            logError("Unable to write '" + commandLineFile.getName() + "' debug script file", e);
        }
    }

    /**
     * Check if the Javadoc JVM is correctly started or not.
     *
     * @param output the command line output, not null.
     * @return <code>true</code> if Javadoc output command line contains Javadoc
     *         word, <code>false</code> otherwise.
     * @see #executeJavadocCommandLine(Commandline, File)
     * @since 2.6.1
     */
    private boolean isJavadocVMInitError(String output) {
        /*
         * see main.usage and main.Building_tree keys from
         * com.sun.tools.javadoc.resources.javadoc bundle in tools.jar
         */
        return !(output.contains("Javadoc") || output.contains("javadoc"));
    }

    // ----------------------------------------------------------------------
    // Static methods
    // ----------------------------------------------------------------------

    /**
     * @param p not null
     * @return the javadoc link based on the project url i.e.
     *         <code>${project.url}/${destDir}</code> where <code>destDir</code>
     *         is configued in the Javadoc plugin configuration
     *         (<code>apidocs</code> by default).
     * @since 2.6
     */
    private static String getJavadocLink(MavenProject p) {
        if (p.getUrl() == null) {
            return null;
        }

        String url = cleanUrl(p.getUrl());
        String destDir = "apidocs"; // see JavadocReport#destDir

        final String pluginId = "org.apache.maven.plugins:maven-javadoc-plugin";
        String destDirConfigured = getPluginParameter(p, pluginId, "destDir");
        if (destDirConfigured != null) {
            destDir = destDirConfigured;
        }

        return url + "/" + destDir;
    }

    /**
     * @param url could be null.
     * @return the url cleaned or empty if url was null.
     * @since 2.6
     */
    private static String cleanUrl(String url) {
        if (url == null) {
            return "";
        }

        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.lastIndexOf("/"));
        }

        return url;
    }

    /**
     * @param p not null
     * @param pluginId not null key of the plugin defined in
     *            {@link org.apache.maven.model.Build#getPluginsAsMap()} or in
     *            {@link org.apache.maven.model.PluginManagement#getPluginsAsMap()}
     * @return the Maven plugin defined in <code>${project.build.plugins}</code>
     *         or in <code>${project.build.pluginManagement}</code>, or
     *         <code>null</code> if not defined.
     * @since 2.6
     */
    private static Plugin getPlugin(MavenProject p, String pluginId) {
        if ((p.getBuild() == null) || (p.getBuild().getPluginsAsMap() == null)) {
            return null;
        }

        Plugin plugin = p.getBuild().getPluginsAsMap().get(pluginId);

        if ((plugin == null) && (p.getBuild().getPluginManagement() != null) && (p.getBuild().getPluginManagement().getPluginsAsMap() != null)) {
            plugin = p.getBuild().getPluginManagement().getPluginsAsMap().get(pluginId);
        }

        return plugin;
    }

    /**
     * @param p not null
     * @param pluginId not null
     * @param param not null
     * @return the simple parameter as String defined in the plugin
     *         configuration by <code>param</code> key or <code>null</code> if
     *         not found.
     * @since 2.6
     */
    private static String getPluginParameter(MavenProject p, String pluginId, String param) {
//        p.getGoalConfiguration( pluginGroupId, pluginArtifactId, executionId, goalId );
        Plugin plugin = getPlugin(p, pluginId);
        if (plugin != null) {
            Xpp3Dom xpp3Dom = (Xpp3Dom)plugin.getConfiguration();
            if (xpp3Dom != null && xpp3Dom.getChild(param) != null && StringUtils.isNotEmpty(xpp3Dom.getChild(param).getValue())) {
                return xpp3Dom.getChild(param).getValue();
            }
        }

        return null;
    }

    /**
     * Construct the output file for the generated javadoc-options XML file,
     * after creating the javadocOptionsDir if necessary. This method does NOT
     * write to the file in question.
     *
     * @return The options {@link File} file.
     * @since 2.7
     */
    protected final File getJavadocOptionsFile() {
        if (javadocOptionsDir != null && !javadocOptionsDir.exists()) {
            javadocOptionsDir.mkdirs();
        }

        return new File(javadocOptionsDir, "javadoc-options-" + getAttachmentClassifier() + ".xml");
    }

    /**
     * Generate a javadoc-options XML file, for either bundling with a
     * javadoc-resources artifact OR supplying to a distro module in a
     * includeDependencySources configuration, so the javadoc options from this
     * execution can be reconstructed and merged in the distro build.
     *
     * @return {@link JavadocOptions}
     * @throws IOException {@link IOException}
     * @since 2.7
     */
    protected final JavadocOptions buildJavadocOptions() throws IOException {
        JavadocOptions options = new JavadocOptions();

        options.setBootclasspathArtifacts(toList(bootclasspathArtifacts));
        options.setDocfilesSubdirsUsed(docfilessubdirs);
        options.setDocletArtifacts(toList(docletArtifact, docletArtifacts));
        options.setExcludedDocfilesSubdirs(excludedocfilessubdir);
        options.setExcludePackageNames(toList(excludePackageNames));
        options.setGroups(toList(groups));
        options.setLinks(links);
        options.setOfflineLinks(toList(offlineLinks));
        options.setResourcesArtifacts(toList(resourcesArtifacts));
        options.setTagletArtifacts(toList(tagletArtifact, tagletArtifacts));
        options.setTaglets(toList(taglets));
        options.setTags(toList(tags));

        if (getProject() != null && getJavadocDirectory() != null) {
            options.setJavadocResourcesDirectory(toRelative(getProject().getBasedir(), getJavadocDirectory().getAbsolutePath()));
        }

        File optionsFile = getJavadocOptionsFile();

        try (Writer writer = WriterFactory.newXmlWriter(optionsFile)) {
            new JavadocOptionsXpp3Writer().write(writer, options);
        }

        return options;
    }

    /**
     * Override this if you need to provide a bundle attachment classifier, as
     * in the case of test javadocs.
     * 
     * @return The attachment classifier.
     */
    protected String getAttachmentClassifier() {
        return JAVADOC_RESOURCES_ATTACHMENT_CLASSIFIER;
    }

    /**
     * Logs an error with throwable content only if in debug.
     *
     * @param message The message which should be announced.
     * @param t The throwable part of the message.
     */
    protected void logError(String message, Throwable t) {
        if (getLog().isDebugEnabled()) {
            getLog().error(message, t);
        } else {
            getLog().error(message);
        }
    }

    /**
     * @param prefix The prefix of the exception.
     * @param e The exception.
     * @throws MojoExecutionException {@link MojoExecutionException}
     */
    protected void failOnError(String prefix, Exception e) throws MojoExecutionException {
        if (failOnError) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            }
            throw new MojoExecutionException(prefix + ": " + e.getMessage(), e);
        }

        getLog().error(prefix + ": " + e.getMessage(), e);
    }
}
