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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.camel.impl.ReportingTypeConverterLoader;
import org.apache.camel.impl.ReportingTypeConverterLoader.TypeMapping;
import org.apache.camel.impl.ReportingTypeConverterRegistry;
import org.apache.camel.util.ObjectHelper;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.site.decoration.Body;
import org.apache.maven.doxia.site.decoration.DecorationModel;
import org.apache.maven.doxia.site.decoration.Skin;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.doxia.siterenderer.RendererException;
import org.apache.maven.doxia.siterenderer.SiteRenderingContext;
import org.apache.maven.doxia.siterenderer.sink.SiteRendererSink;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;

/**
 * Generate report of available type conversions.
 *
 * @goal converters-report
 * @requiresDependencyResolution runtime
 * @phase verify
 */
public class ConvertersMojo extends AbstractMavenReport {

    private static final String WIKI_TYPECONVERER_URL = "http://activemq.apache.org/camel/type-converter.html";
    private static final String CONVERTER_TYPE_STATIC = "org.apache.camel.impl.converter.StaticMethodTypeConverter";
    private static final String CONVERTER_TYPE_INSTANCE = "org.apache.camel.impl.converter.InstanceMethodTypeConverter";
    private static final String REPORT_METHOD_STATIC = "STATIC";
    private static final String REPORT_METHOD_INSTANCE = "INSTANCE";
    private static final String REPORT_METHOD_UNKNOWN = "UNKNOWN";

    /**
     * Remote repositories which will be searched for source attachments.
     *
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    protected List remoteArtifactRepositories;

    /**
     * Local maven repository.
     *
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    protected ArtifactRepository localRepository;

    /**
     * The component that is used to resolve additional artifacts required.
     *
     * @component
     */
    protected ArtifactResolver artifactResolver;

    /**
     * The component used for creating artifact instances.
     *
     * @component
     */
    protected ArtifactFactory artifactFactory;

    /**
     * Base output directory for reports.
     *
     * @parameter default-value="${project.build.directory}/site"
     * @readonly
     * @required
     */
    private File outputDirectory;

    /**
     * Reference to Maven 2 Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * Doxia SiteRenderer.
     *
     * @component
     */
    private Renderer renderer;

    /**
     * Gets resource bundle for given locale.
     *
     * @param locale    locale
     * @return resource bundle
     */
    protected ResourceBundle getBundle(final Locale locale) {
        return ResourceBundle.getBundle("camel-maven-plugin", locale, this
                .getClass().getClassLoader());
    }

    /**
     * @param locale
     *            report locale.
     * @return report description.
     * @see org.apache.maven.reporting.MavenReport#getDescription(Locale)
     */
    public String getDescription(final Locale locale) {
        return getBundle(locale).getString("report.converters.description");
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getName(Locale)
     */
    public String getName(final Locale locale) {
        return getBundle(locale).getString("report.converters.name");
    }

    public String getOutputName() {
        return "camel-converters";
    }

    @Override
    protected String getOutputDirectory() {
        return outputDirectory.getAbsolutePath();
    }

    @Override
    protected MavenProject getProject() {
        return this.project;
    }

    @Override
    protected Renderer getSiteRenderer() {
        return renderer;
    }

    public void execute() throws MojoExecutionException {
        if (!canGenerateReport()) {
            return;
        }

        try {
            DecorationModel model = new DecorationModel();
            model.setBody(new Body());
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put("outputEncoding", "UTF-8");
            attributes.put("project", project);
            Locale locale = Locale.getDefault();

            SiteRenderingContext siteContext = renderer.createContextForSkin(
                    getSkinArtifactFile(model), attributes, model,
                    getName(locale), locale);

            RenderingContext context = new RenderingContext(
                    getReportOutputDirectory(), getOutputName() + ".html");
            SiteRendererSink sink = new SiteRendererSink(context);
            generate(sink, locale);

            Writer writer = new OutputStreamWriter(new FileOutputStream(
                    new File(getReportOutputDirectory(), getOutputName()
                            + ".html")), "UTF-8");

            renderer.generateDocument(writer, sink, siteContext);
            renderer.copyResources(siteContext, new File(project.getBasedir(),
                    "src/site/resources"), outputDirectory);
        } catch (IOException e) {
            throw new MojoExecutionException("Error copying resources.", e);
        } catch (RendererException e) {
            throw new MojoExecutionException("Error while rendering report.", e);
        } catch (MojoFailureException e) {
            throw new MojoExecutionException(
                    "Cannot find skin artifact for report.", e);
        } catch (MavenReportException e) {
            throw new MojoExecutionException("Error generating report.", e);
        }
    }

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {

        if (!createOutputDirectory(outputDirectory)) {
            throw new MavenReportException("Failed to create report directory "
                    + outputDirectory.getAbsolutePath());
        }

        ClassLoader oldClassLoader = Thread.currentThread()
                .getContextClassLoader();
        try {
            // TODO: this badly needs some refactoring
            // mojo.createClassLoader creates a URLClassLoader with whatever is
            // in
            // ${project.testClasspathElements}, reason why we don't see all
            // converters
            // in the report. First we need a list of classpath elements the
            // user
            // could customize via plugin configuration, and elements of that
            // list
            // be added to the URLClassLoader. This should also be factored out
            // into
            // a utility class.
            // TODO: there is some interference with the site plugin that needs
            // investigated.
            List<?> list = project.getTestClasspathElements();
            EmbeddedMojo mojo = new EmbeddedMojo();
            mojo.setClasspathElements(list);
            ClassLoader newClassLoader = mojo.createClassLoader(oldClassLoader);
            Thread.currentThread().setContextClassLoader(newClassLoader);

            ReportingTypeConverterLoader loader = new ReportingTypeConverterLoader();
            ReportingTypeConverterRegistry registry = new ReportingTypeConverterRegistry();
            loader.load(registry);
            getLog().error(
                    "FOUND type mapping; count = "
                            + loader.getTypeConversions().length);

            String[] errors = registry.getErrors();
            for (String error : errors) {
                getLog().error(error);
            }

            generateReport(getSink(), locale, loader.getTypeConversions());
        } catch (Exception e) {
            throw new MavenReportException(
                    "Failed to generate TypeConverters report", e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

    private boolean createOutputDirectory(final File outputDir) {
        if (outputDir.exists()) {
            if (!outputDir.isDirectory()) {
                getLog().error(
                        "File with same name already exists: "
                                + outputDir.getAbsolutePath());
                return false;
            }
        } else {
            if (!outputDir.mkdirs()) {
                getLog().error(
                        "Cannot make output directory at: "
                                + outputDir.getAbsolutePath());
                return false;
            }
        }
        return true;
    }

    private File getSkinArtifactFile(DecorationModel decoration) throws MojoFailureException {

        Skin skin = decoration.getSkin();
        if (skin == null) {
            skin = Skin.getDefaultSkin();
        }

        String version = skin.getVersion();
        Artifact artifact;
        try {
            if (version == null) {
                version = Artifact.RELEASE_VERSION;
            }

            VersionRange versionSpec = VersionRange
                    .createFromVersionSpec(version);
            artifact = artifactFactory.createDependencyArtifact(skin
                    .getGroupId(), skin.getArtifactId(), versionSpec, "jar",
                    null, null);

            artifactResolver.resolve(artifact, remoteArtifactRepositories,
                    localRepository);
            return artifact.getFile();
        } catch (InvalidVersionSpecificationException e) {
            throw new MojoFailureException("The skin version '" + version
                    + "' is not valid: " + e.getMessage());
        } catch (ArtifactResolutionException e) {
            throw new MojoFailureException("Unable to fink skin: "
                    + e.getMessage());
        } catch (ArtifactNotFoundException e) {
            throw new MojoFailureException("The skin does not exist: "
                    + e.getMessage());
        }
    }

    private String converterType(String converterClassName) {
        if (CONVERTER_TYPE_STATIC.equals(converterClassName)) {
            return REPORT_METHOD_STATIC;
        } else if (CONVERTER_TYPE_INSTANCE.equals(converterClassName)) {
            return REPORT_METHOD_INSTANCE;
        } else {
            return REPORT_METHOD_UNKNOWN;
        }
    }

    private void generateReport(Sink sink, Locale locale, TypeMapping[] mappings)
        throws MojoExecutionException {
        beginReport(sink, locale);

        Set<String> classes;
        Map<String, Set<String>> packages = new TreeMap<String, Set<String>>();
        Class<?> prevFrom = null;
        Class<?> prevTo = null;

        sink.table();
        tableHeader(sink, locale);

        for (TypeMapping mapping : mappings) {
            boolean ignored = false;
            Class<?> from = mapping.getFromType();
            Class<?> to = mapping.getToType();
            if (ObjectHelper.equal(from, prevFrom)
                    && ObjectHelper.equal(to, prevTo)) {
                ignored = true;
            }
            prevFrom = from;
            prevTo = to;
            Method method = mapping.getMethod();
            Class<?> methodClass = method.getDeclaringClass();
            String packageName = methodClass.getPackage().getName();
            if (packages.containsKey(packageName)) {
                classes = packages.get(packageName);
            } else {
                classes = new TreeSet<String>();
                packages.put(packageName, classes);
            }
            classes.add(methodClass.getName());

            if (ignored) {
                sink.italic();
                this.tableRow(sink, from.getSimpleName(), to.getSimpleName(),
                        method.getName(), methodClass, mapping
                                .getConverterType().getName());
                sink.italic_();
            } else {
                this.tableRow(sink, from.getSimpleName(), to.getSimpleName(),
                        method.getName(), methodClass, mapping
                                .getConverterType().getName());
            }
        }
        sink.table_();

        generatePackageReport(sink, packages);

        endReport(sink);
    }

    private void generatePackageReport(Sink sink,
            Map<String, Set<String>> packages) {
        for (Map.Entry<String, Set<String>> entry : packages.entrySet()) {
            sink.section2();
            sink.sectionTitle2();
            sink.text(entry.getKey());
            sink.sectionTitle2_();
            sink.list();
            for (String clazz : entry.getValue()) {
                sink.listItem();
                sink.anchor(clazz);
                sink.text(clazz);
                sink.anchor_();
                sink.listItem_();
            }
            sink.list_();
            sink.section2_();
        }
    }

    private void beginReport(Sink sink, Locale locale) {
        String title = getBundle(locale).getString(
                "report.converters.report.title");
        String header = getBundle(locale).getString(
                "report.converters.report.header");
        String intro = getBundle(locale).getString(
                "report.converters.report.intro");
        String seealso = getBundle(locale).getString(
                "report.converters.report.seealso");

        sink.head();
        sink.title();
        sink.text(title);
        sink.title_();
        sink.head_();

        sink.body();

        sink.section1();

        sink.sectionTitle1();
        sink.text(header);
        sink.sectionTitle1_();

        sink.paragraph();
        sink.text(intro);
        sink.paragraph_();
        sink.paragraph();
        sink.text(seealso);
        sink.list();
        sink.listItem();
        sink.link(WIKI_TYPECONVERER_URL);
        sink.text(WIKI_TYPECONVERER_URL);
        sink.link_();
        sink.listItem_();
        sink.list_();
        sink.paragraph_();
    }

    private void tableHeader(Sink sink, Locale locale) {
        String caption = getBundle(locale).getString(
                "report.converters.report.table.caption");
        String head1 = getBundle(locale).getString(
                "report.converters.report.table.head1");
        String head2 = getBundle(locale).getString(
                "report.converters.report.table.head2");
        String head3 = getBundle(locale).getString(
                "report.converters.report.table.head3");
        String head4 = getBundle(locale).getString(
                "report.converters.report.table.head4");
        String head5 = getBundle(locale).getString(
                "report.converters.report.table.head5");

        sink.tableCaption();
        sink.text(caption);
        sink.tableCaption_();

        sink.tableRow();
        sink.tableHeaderCell();
        sink.text(head1);
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text(head2);
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text(head3);
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text(head4);
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text(head5);
        sink.tableHeaderCell_();
        sink.tableRow();
    }

    private void tableRow(Sink sink, String from, String to, String method,
            Class<?> clazz, String type) {

        sink.tableRow();
        sink.tableCell();
        sink.text(from);
        sink.tableCell_();
        sink.tableCell();
        sink.text(to);
        sink.tableCell_();
        sink.tableCell();
        sink.text(method);
        sink.tableCell_();
        sink.tableCell();
        sink.link(clazz.getName());
        sink.text(clazz.getSimpleName());
        sink.link_();
        sink.tableCell_();
        sink.tableCell();
        sink.text(converterType(type));
        sink.tableCell_();
        sink.tableRow();
    }

    private void endReport(Sink sink) {
        sink.section1_();

        sink.body_();
        sink.flush();
        sink.close();
    }
}
