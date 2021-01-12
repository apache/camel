/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.revelc.code.formatter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.xml.sax.SAXException;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import net.revelc.code.formatter.css.CssFormatter;
import net.revelc.code.formatter.html.HTMLFormatter;
import net.revelc.code.formatter.java.JavaFormatter;
import net.revelc.code.formatter.javascript.JavascriptFormatter;
import net.revelc.code.formatter.json.JsonFormatter;
import net.revelc.code.formatter.model.ConfigReadException;
import net.revelc.code.formatter.model.ConfigReader;
import net.revelc.code.formatter.xml.XMLFormatter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.resource.ResourceManager;
import org.codehaus.plexus.resource.loader.FileResourceLoader;
import org.codehaus.plexus.resource.loader.ResourceNotFoundException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.text.edits.MalformedTreeException;

/**
 * A Maven plugin mojo to format Java source code using the Eclipse code formatter.
 * 
 * Mojo parameters allow customizing formatting by specifying the config XML file, line endings, compiler version, and
 * source code locations. Reformatting source files is avoided using an sha512 hash of the content, comparing to the
 * original hash to the hash after formatting and a cached hash.
 * 
 * @author jecki
 * @author Matt Blanchette
 * @author marvin.froeder
 */
@Mojo(name = "format", defaultPhase = LifecyclePhase.PROCESS_SOURCES, requiresProject = true, threadSafe = true)
public class FormatterMojo extends AbstractMojo implements ConfigurationSource {

    private static final String FILE_S = " file(s)";

    /** The Constant CACHE_PROPERTIES_FILENAME. */
    private static final String CACHE_PROPERTIES_FILENAME = "formatter-maven-cache.properties";

    /** The Constant DEFAULT_INCLUDES. */
    private static final String[] DEFAULT_INCLUDES = new String[] { "**/*.css", "**/*.json", "**/*.html", "**/*.java",
            "**/*.js", "**/*.xml" };

    /**
     * ResourceManager for retrieving the configFile resource.
     */
    @Component(role = ResourceManager.class)
    private ResourceManager resourceManager;

    /**
     * Project's source directory as specified in the POM.
     */
    @Parameter(defaultValue = "${project.build.sourceDirectory}", property = "sourceDirectory", required = true)
    private File sourceDirectory;

    /**
     * Project's test source directory as specified in the POM.
     */
    @Parameter(defaultValue = "${project.build.testSourceDirectory}", property = "testSourceDirectory", required = true)
    private File testSourceDirectory;

    /**
     * Project's target directory as specified in the POM.
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    private File targetDirectory;

    /**
     * Project's base directory.
     */
    @Parameter(defaultValue = ".", property = "project.basedir", readonly = true, required = true)
    private File basedir;

    /**
     * Projects cache directory.
     *
     * <p>
     * This file is a hash cache of the files in the project source. It can be preserved in source code such that it
     * ensures builds are always fast by not unnecessarily writing files constantly. It can also be added to gitignore
     * in case startup is not necessary. It further can be redirected to another location.
     *
     * <p>
     * When stored in the repository, the cache if run on cross platforms will display the files multiple times due to
     * line ending differences on the platform.
     *
     * <p>
     * The cache itself has been part of formatter plugin for a long time but was hidden in target directory and did not
     * survive clean phase when it should. This is not intended to be clean in that way as one would want as close to a
     * no-op as possible when files are already all formatted and/or have not been otherwise touched. This is used based
     * off the files in the project so it is as much part of the source as any other file is.
     * 
     * @since 2.12.1
     */
    @Parameter(defaultValue = "${project.build.directory}", property = "formatter.cachedir")
    private File cachedir;

    /**
     * Location of the Java source files to format. Defaults to source main and test directories if not set. Deprecated
     * in version 0.3. Reintroduced in 0.4.
     * 
     * @since 0.4
     */
    @Parameter
    private File[] directories;

    /**
     * List of fileset patterns for Java source locations to include in formatting. Patterns are relative to the project
     * source and test source directories. When not specified, the default include is <code>**&#47;*.java</code>
     * 
     * @since 0.3
     */
    @Parameter(property = "formatter.includes")
    private String[] includes;

    /**
     * List of fileset patterns for Java source locations to exclude from formatting. Patterns are relative to the
     * project source and test source directories. When not specified, there is no default exclude.
     * 
     * @since 0.3
     */
    @Parameter(property = "formatter.excludes")
    private String[] excludes;

    /**
     * Java compiler source version.
     */
    @Parameter(defaultValue = "1.8", property = "maven.compiler.source", required = true)
    private String compilerSource;

    /**
     * Java compiler compliance version.
     */
    @Parameter(defaultValue = "1.8", property = "maven.compiler.source", required = true)
    private String compilerCompliance;

    /**
     * Java compiler target version.
     */
    @Parameter(defaultValue = "1.8", property = "maven.compiler.target", required = true)
    private String compilerTargetPlatform;

    /**
     * The file encoding used to read and write source files. When not specified and sourceEncoding also not set,
     * default is platform file encoding.
     * 
     * @since 0.3
     */
    @Parameter(property = "project.build.sourceEncoding", required = true)
    private String encoding;

    /**
     * Sets the line-ending of files after formatting. Valid values are:
     * <ul>
     * <li><b>"AUTO"</b> - Use line endings of current system</li>
     * <li><b>"KEEP"</b> - Preserve line endings of files, default to AUTO if ambiguous</li>
     * <li><b>"LF"</b> - Use Unix and Mac style line endings</li>
     * <li><b>"CRLF"</b> - Use DOS and Windows style line endings</li>
     * <li><b>"CR"</b> - Use early Mac style line endings</li>
     * </ul>
     * 
     * @since 0.2.0
     */
    @Parameter(defaultValue = "AUTO", property = "lineending", required = true)
    private LineEnding lineEnding;

    /**
     * File or classpath location of an Eclipse code formatter configuration xml file to use in formatting.
     */
    @Parameter(defaultValue = "formatter-maven-plugin/eclipse/java.xml", property = "configfile", required = true)
    private String configFile;

    /**
     * File or classpath location of an Eclipse code formatter configuration xml file to use in formatting.
     */
    @Parameter(defaultValue = "formatter-maven-plugin/eclipse/javascript.xml", property = "configjsfile", required = true)
    private String configJsFile;

    /**
     * File or classpath location of a properties file to use in html formatting.
     */
    @Parameter(defaultValue = "formatter-maven-plugin/jsoup/html.properties", property = "confightmlfile", required = true)
    private String configHtmlFile;

    /**
     * File or classpath location of a properties file to use in xml formatting.
     */
    @Parameter(defaultValue = "formatter-maven-plugin/eclipse/xml.properties", property = "configxmlfile", required = true)
    private String configXmlFile;

    /**
     * File or classpath location of a properties file to use in json formatting.
     */
    @Parameter(defaultValue = "formatter-maven-plugin/jackson/json.properties", property = "configjsonfile", required = true)
    private String configJsonFile;

    /**
     * File or classpath location of a properties file to use in css formatting.
     */
    @Parameter(defaultValue = "formatter-maven-plugin/ph-css/css.properties", property = "configcssfile", required = true)
    private String configCssFile;

    /**
     * Whether the java formatting is skipped.
     */
    @Parameter(defaultValue = "false", property = "formatter.java.skip")
    private boolean skipJavaFormatting;

    /**
     * Whether the javascript formatting is skipped.
     */
    @Parameter(defaultValue = "false", property = "formatter.js.skip")
    private boolean skipJsFormatting;

    /**
     * Whether the html formatting is skipped.
     */
    @Parameter(defaultValue = "false", property = "formatter.html.skip")
    private boolean skipHtmlFormatting;

    /**
     * Whether the xml formatting is skipped.
     */
    @Parameter(defaultValue = "false", property = "formatter.xml.skip")
    private boolean skipXmlFormatting;

    /**
     * Whether the json formatting is skipped.
     */
    @Parameter(defaultValue = "false", property = "formatter.json.skip")
    private boolean skipJsonFormatting;

    /**
     * Whether the css formatting is skipped.
     */
    @Parameter(defaultValue = "false", property = "formatter.css.skip")
    private boolean skipCssFormatting;

    /**
     * Whether the formatting is skipped.
     *
     * @since 0.5
     */
    @Parameter(defaultValue = "false", alias = "skip", property = "formatter.skip")
    private boolean skipFormatting;

    /**
     * Use eclipse defaults when set to true for java and javascript.
     */
    @Parameter(defaultValue = "false", property = "formatter.useEclipseDefaults")
    private boolean useEclipseDefaults;

    private JavaFormatter javaFormatter = new JavaFormatter();

    private JavascriptFormatter jsFormatter = new JavascriptFormatter();

    private HTMLFormatter htmlFormatter = new HTMLFormatter();

    private XMLFormatter xmlFormatter = new XMLFormatter();

    private JsonFormatter jsonFormatter = new JsonFormatter();

    private CssFormatter cssFormatter = new CssFormatter();

    private boolean hashCacheWritten;

    /**
     * Execute.
     *
     * @throws MojoExecutionException
     *             the mojo execution exception
     * @throws MojoFailureException
     *             the mojo failure exception
     * 
     * @see AbstractMojo#execute()
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skipFormatting) {
            getLog().info("Formatting is skipped");
            return;
        }

        long startClock = System.currentTimeMillis();

        if (StringUtils.isEmpty(this.encoding)) {
            this.encoding = ReaderFactory.FILE_ENCODING;
            getLog().warn("File encoding has not been set, using platform encoding (" + this.encoding
                    + ") to format source files, i.e. build is platform dependent!");
        } else {
            if (!Charset.isSupported(this.encoding)) {
                throw new MojoExecutionException("Encoding '" + this.encoding + "' is not supported");
            }
            getLog().info("Using '" + this.encoding + "' encoding to format source files.");
        }

        List<File> files = new ArrayList<>();
        if (this.directories != null) {
            for (File directory : this.directories) {
                if (directory.exists() && directory.isDirectory()) {
                    files.addAll(addCollectionFiles(directory));
                }
            }
        } else {
            // Using defaults of source main and test dirs
            if (this.sourceDirectory != null && this.sourceDirectory.exists() && this.sourceDirectory.isDirectory()) {
                files.addAll(addCollectionFiles(this.sourceDirectory));
            }
            if (this.testSourceDirectory != null && this.testSourceDirectory.exists()
                    && this.testSourceDirectory.isDirectory()) {
                files.addAll(addCollectionFiles(this.testSourceDirectory));
            }
        }

        int numberOfFiles = files.size();
        Log log = getLog();
        log.info("Number of files to be formatted: " + numberOfFiles);

        if (numberOfFiles > 0) {
            createCodeFormatter();
            ResultCollector rc = new ResultCollector();
            Properties hashCache = readFileHashCacheFile();

            String basedirPath = getBasedirPath();
            for (int i = 0, n = files.size(); i < n; i++) {
                File file = files.get(i);
                if (file.exists()) {
                    if (file.canWrite()) {
                        formatFile(file, rc, hashCache, basedirPath);
                    } else {
                        rc.readOnlyCount++;
                    }
                } else {
                    rc.failCount++;
                }
            }

            // Only store the cache if it changed during processing to avoid java properties timestamp writting for
            // those that want to save the cache
            if (hashCacheWritten) {
                storeFileHashCache(hashCache);
            }

            long endClock = System.currentTimeMillis();

            log.info("Successfully formatted:          " + rc.successCount + FILE_S);
            log.info("Fail to format:                  " + rc.failCount + FILE_S);
            log.info("Skipped:                         " + rc.skippedCount + FILE_S);
            log.info("Read only skipped:               " + rc.readOnlyCount + FILE_S);
            log.info("Approximate time taken:          " + ((endClock - startClock) / 1000) + "s");
        }
    }

    /**
     * Add source files to the files list.
     *
     */
    List<File> addCollectionFiles(File newBasedir) {
        final DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(newBasedir);
        if (this.includes != null && this.includes.length > 0) {
            ds.setIncludes(this.includes);
        } else {
            ds.setIncludes(DEFAULT_INCLUDES);
        }

        ds.setExcludes(this.excludes);
        ds.addDefaultExcludes();
        ds.setCaseSensitive(false);
        ds.setFollowSymlinks(false);
        ds.scan();

        List<File> foundFiles = new ArrayList<>();
        for (String filename : ds.getIncludedFiles()) {
            foundFiles.add(new File(newBasedir, filename));
        }
        return foundFiles;
    }

    /**
     * Gets the basedir path.
     * 
     * @return the basedir path
     */
    private String getBasedirPath() {
        try {
            return this.basedir.getCanonicalPath();
        } catch (IOException e) {
            getLog().debug("", e);
            return "";
        }
    }

    /**
     * Store file hash cache.
     *
     * @param props
     *            the props
     */
    private void storeFileHashCache(Properties props) {
        File cacheFile = new File(this.cachedir, CACHE_PROPERTIES_FILENAME);
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(cacheFile))) {
            props.store(out, null);
        } catch (IOException e) {
            getLog().warn("Cannot store file hash cache properties file", e);
        }
    }

    /**
     * Read file hash cache file.
     *
     * @return the properties
     */
    private Properties readFileHashCacheFile() {
        Properties props = new Properties();
        Log log = getLog();
        if (!this.cachedir.exists()) {
            this.cachedir.mkdirs();
        } else if (!this.cachedir.isDirectory()) {
            log.warn("Something strange here as the '" + this.cachedir.getPath()
                    + "' supposedly cache directory is not a directory.");
            return props;
        }

        File cacheFile = new File(this.cachedir, CACHE_PROPERTIES_FILENAME);
        if (!cacheFile.exists()) {
            return props;
        }

        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(cacheFile))) {
            props.load(stream);
        } catch (IOException e) {
            log.warn("Cannot load file hash cache properties file", e);
        }
        return props;
    }

    /**
     * Format file.
     *
     * @param file
     *            the file
     * @param rc
     *            the rc
     * @param hashCache
     *            the hash cache
     * @param basedirPath
     *            the basedir path
     * 
     * @throws MojoFailureException
     *             the mojo failure exception
     * @throws MojoExecutionException
     *             the mojo execution exception
     */
    private void formatFile(File file, ResultCollector rc, Properties hashCache, String basedirPath)
            throws MojoFailureException, MojoExecutionException {
        try {
            doFormatFile(file, rc, hashCache, basedirPath, false);
        } catch (IOException | MalformedTreeException | BadLocationException e) {
            rc.failCount++;
            getLog().warn(e);
        }
    }

    /**
     * Format individual file.
     *
     * @param file
     *            the file
     * @param rc
     *            the rc
     * @param hashCache
     *            the hash cache
     * @param basedirPath
     *            the basedir path
     * @param dryRun
     *            the dry run
     * 
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     * @throws BadLocationException
     *             the bad location exception
     * @throws MojoFailureException
     *             the mojo failure exception
     * @throws MojoExecutionException
     *             the mojo execution exception
     */
    protected void doFormatFile(File file, ResultCollector rc, Properties hashCache, String basedirPath, boolean dryRun)
            throws IOException, BadLocationException, MojoFailureException, MojoExecutionException {
        Log log = getLog();
        log.debug("Processing file: " + file);
        String originalCode = readFileAsString(file);
        String originalHash = sha512hash(originalCode);

        String canonicalPath = file.getCanonicalPath();
        String path = canonicalPath.substring(basedirPath.length());
        String cachedHash = hashCache.getProperty(path);
        if (cachedHash != null && cachedHash.equals(originalHash)) {
            rc.skippedCount++;
            log.debug("File is already formatted.");
            return;
        }

        Result result = null;
        String formattedCode = null;
        if (file.getName().endsWith(".java") && javaFormatter.isInitialized()) {
            if (skipJavaFormatting) {
                getLog().info("Java formatting is skipped");
                result = Result.SKIPPED;
            } else {
                formattedCode = this.javaFormatter.formatFile(file, originalCode, this.lineEnding);
            }
        } else if (file.getName().endsWith(".js") && jsFormatter.isInitialized()) {
            if (skipJsFormatting) {
                getLog().info("Javascript formatting is skipped");
                result = Result.SKIPPED;
            } else {
                formattedCode = this.jsFormatter.formatFile(file, originalCode, this.lineEnding);
            }
        } else if (file.getName().endsWith(".html") && htmlFormatter.isInitialized()) {
            if (skipHtmlFormatting) {
                getLog().info("Html formatting is skipped");
                result = Result.SKIPPED;
            } else {
                formattedCode = this.htmlFormatter.formatFile(file, originalCode, this.lineEnding);
            }
        } else if (file.getName().endsWith(".xml") && xmlFormatter.isInitialized()) {
            if (skipXmlFormatting) {
                getLog().info("Xml formatting is skipped");
                result = Result.SKIPPED;
            } else {
                formattedCode = this.xmlFormatter.formatFile(file, originalCode, this.lineEnding);
            }
        } else if (file.getName().endsWith(".json") && jsonFormatter.isInitialized()) {
            if (skipJsonFormatting) {
                getLog().info("json formatting is skipped");
                result = Result.SKIPPED;
            } else {
                formattedCode = this.jsonFormatter.formatFile(file, originalCode, this.lineEnding);
            }
        } else if (file.getName().endsWith(".css") && cssFormatter.isInitialized()) {
            if (skipCssFormatting) {
                getLog().info("css formatting is skipped");
                result = Result.SKIPPED;
            } else {
                formattedCode = this.cssFormatter.formatFile(file, originalCode, this.lineEnding);
            }
        } else {
            log.debug("No formatter found or initialization failed for file " + file.getName());
            result = Result.SKIPPED;
        }

        // If not skipped, check formatting result
        if (!Result.SKIPPED.equals(result)) {
            if (formattedCode == null) {
                result = Result.FAIL;
            } else if (originalCode.equals(formattedCode)) {
                result = Result.SKIPPED;
            } else {
                result = Result.SUCCESS;
            }
        }

        // Process the result type
        if (Result.SKIPPED.equals(result)) {
            rc.skippedCount++;
        } else if (Result.SUCCESS.equals(result)) {
            rc.successCount++;
            rc.diff.add(DiffUtils.diff(originalCode, formattedCode, null));
        } else if (Result.FAIL.equals(result)) {
            rc.failCount++;
            return;
        }

        // Write the cache
        String formattedHash;
        if (Result.SKIPPED.equals(result)) {
            formattedHash = originalHash;
        } else {
            formattedHash = sha512hash(Strings.nullToEmpty(formattedCode));
        }
        hashCache.setProperty(path, formattedHash);
        hashCacheWritten = true;

        // If we had determined to skip write, do so now after cache was written
        if (Result.SKIPPED.equals(result)) {
            log.debug("File is already formatted.  Writing to cache only.");
            return;
        }

        // As a safety check, if our hash matches, skip the write of file (should never occur)
        if (originalHash.equals(formattedHash)) {
            rc.skippedCount++;
            log.debug("Equal hash code. Not writing result to file.");
            return;
        }

        // Now write the file
        if (!dryRun) {
            writeStringToFile(formattedCode, file);
        }
    }

    /**
     * sha512hash.
     *
     * @param str
     *            the str
     * 
     * @return the string
     */
    private String sha512hash(String str) {
        return Hashing.sha512().hashBytes(str.getBytes(getEncoding())).toString();
    }

    /**
     * Read the given file and return the content as a string.
     *
     * @param file
     *            the file
     * 
     * @return the string
     * 
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    private String readFileAsString(File file) throws IOException {
        StringBuilder fileData = new StringBuilder(1000);
        try (BufferedReader reader = new BufferedReader(ReaderFactory.newReader(file, this.encoding))) {
            char[] buf = new char[1024];
            int numRead = 0;
            while ((numRead = reader.read(buf)) != -1) {
                String readData = String.valueOf(buf, 0, numRead);
                fileData.append(readData);
                buf = new char[1024];
            }
        }
        return fileData.toString();
    }

    /**
     * Write the given string to a file.
     *
     * @param str
     *            the str
     * @param file
     *            the file
     * 
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    private void writeStringToFile(String str, File file) throws IOException {
        if (!file.exists() && file.isDirectory()) {
            return;
        }

        try (BufferedWriter bw = new BufferedWriter(WriterFactory.newWriter(file, this.encoding))) {
            bw.write(str);
        }
    }

    /**
     * Create a {@link CodeFormatter} instance to be used by this mojo.
     *
     * @throws MojoExecutionException
     *             the mojo execution exception
     */
    private void createCodeFormatter() throws MojoExecutionException {
        // Java Setup
        Map<String, String> javaFormattingOptions = getFormattingOptions(this.configFile);
        if (javaFormattingOptions != null) {
            this.javaFormatter.init(javaFormattingOptions, this);
        }
        // Javascript Setup
        Map<String, String> jsFormattingOptions = getFormattingOptions(this.configJsFile);
        if (jsFormattingOptions != null) {
            this.jsFormatter.init(jsFormattingOptions, this);
        }
        // Html Setup
        if (configHtmlFile != null) {
            this.htmlFormatter.init(getOptionsFromPropertiesFile(this.configHtmlFile), this);
        }
        // Xml Setup
        if (configXmlFile != null) {
            Map<String, String> xmlFormattingOptions = getOptionsFromPropertiesFile(this.configXmlFile);
            xmlFormattingOptions.put("lineending", this.lineEnding.getChars());
            this.xmlFormatter.init(xmlFormattingOptions, this);
        }
        // Json Setup
        if (configJsonFile != null) {
            Map<String, String> jsonFormattingOptions = getOptionsFromPropertiesFile(this.configJsonFile);
            jsonFormattingOptions.put("lineending", this.lineEnding.getChars());
            this.jsonFormatter.init(jsonFormattingOptions, this);
        }
        // Css Setup
        if (configCssFile != null) {
            this.cssFormatter.init(getOptionsFromPropertiesFile(configCssFile), this);
        }
        // stop the process if not config files where found
        if (javaFormattingOptions == null && jsFormattingOptions == null && configHtmlFile == null
                && configXmlFile == null && configCssFile == null) {
            throw new MojoExecutionException(
                    "You must provide a Java, Javascript, HTML, XML, JSON, or CSS configuration file.");
        }
    }

    /**
     * Return the options to be passed when creating {@link CodeFormatter} instance.
     *
     * @return the formatting options or null if not config file found
     * 
     * @throws MojoExecutionException
     *             the mojo execution exception
     */
    private Map<String, String> getFormattingOptions(String newConfigFile) throws MojoExecutionException {
        if (this.useEclipseDefaults) {
            getLog().info("Using Ecipse Defaults");
            // Use defaults only for formatting
            Map<String, String> options = new HashMap<>();
            options.put(JavaCore.COMPILER_SOURCE, this.compilerSource);
            options.put(JavaCore.COMPILER_COMPLIANCE, this.compilerCompliance);
            options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, this.compilerTargetPlatform);
            return options;
        }

        return getOptionsFromConfigFile(newConfigFile);
    }

    /**
     * Read config file and return the config as {@link Map}.
     *
     * @return the options from config file
     * 
     * @throws MojoExecutionException
     *             the mojo execution exception
     */
    private Map<String, String> getOptionsFromConfigFile(String newConfigFile) throws MojoExecutionException {

        this.getLog().debug("Using search path at: " + this.basedir.getAbsolutePath());
        this.resourceManager.addSearchPath(FileResourceLoader.ID, this.basedir.getAbsolutePath());

        try (InputStream configInput = this.resourceManager.getResourceAsInputStream(newConfigFile)) {
            return new ConfigReader().read(configInput);
        } catch (ResourceNotFoundException e) {
            throw new MojoExecutionException("Cannot find config file [" + newConfigFile + "]");
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read config file [" + newConfigFile + "]", e);
        } catch (SAXException e) {
            throw new MojoExecutionException("Cannot parse config file [" + newConfigFile + "]", e);
        } catch (ConfigReadException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Read properties file and return the properties as {@link Map}.
     *
     * @return the options from properties file or null if not properties file found
     * 
     * @throws MojoExecutionException
     *             the mojo execution exception
     */
    private Map<String, String> getOptionsFromPropertiesFile(String newPropertiesFile) throws MojoExecutionException {

        this.getLog().debug("Using search path at: " + this.basedir.getAbsolutePath());
        this.resourceManager.addSearchPath(FileResourceLoader.ID, this.basedir.getAbsolutePath());

        Properties properties = new Properties();
        try {
            properties.load(this.resourceManager.getResourceAsInputStream(newPropertiesFile));
        } catch (ResourceNotFoundException e) {
            getLog().debug("Property file [" + newPropertiesFile + "] cannot be found", e);
            return new HashMap<>();
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read config file [" + newPropertiesFile + "]", e);
        }

        final Map<String, String> map = new HashMap<>();
        for (final String name : properties.stringPropertyNames()) {
            map.put(name, properties.getProperty(name));
        }
        return map;
    }

    static class ResultCollector {

        int successCount;

        int failCount;

        int skippedCount;

        int readOnlyCount;

        List<Patch<String>> diff = new ArrayList<>();
    }

    @Override
    public String getCompilerSources() {
        return this.compilerSource;
    }

    @Override
    public String getCompilerCompliance() {
        return this.compilerCompliance;
    }

    @Override
    public String getCompilerCodegenTargetPlatform() {
        return this.compilerTargetPlatform;
    }

    @Override
    public File getTargetDirectory() {
        return this.targetDirectory;
    }

    @Override
    public Charset getEncoding() {
        return Charset.forName(this.encoding);
    }
}
