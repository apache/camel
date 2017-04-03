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
package org.apache.camel.generator.swagger.apt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;

import org.apache.camel.generator.swagger.DestinationGenerator;
import org.apache.camel.generator.swagger.RestDslGenerator;
import org.apache.camel.generator.swagger.RestDslSourceCodeGenerator;
import org.apache.camel.util.ObjectHelper;

/**
 * Annotation processor that generates REST DSL definitions from Swagger
 * specification.
 */
@SupportedAnnotationTypes("org.apache.camel.generator.swagger.apt.SwaggerRestDsl")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class RestDslSwaggerAnnotationProcessor extends AbstractProcessor {

    private Filer filer;

    private Messager messager;

    private TypeMirror swaggerRestDslType;

    @Override
    public synchronized void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        final Element actionElement = processingEnv.getElementUtils().getTypeElement(SwaggerRestDsl.class.getName());

        swaggerRestDslType = actionElement.asType();

        filer = processingEnv.getFiler();

        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        roundEnv.getElementsAnnotatedWith(SwaggerRestDsl.class).forEach(this::process);

        return true;
    }

    /**
     * Generates CLASSPATH style string with all entries present on the
     * {@code javac} classpath. This is done by getting the CLASSPATH from
     * {@link JavacProcessingEnvironment} through the underlying
     * {@link JavaFileManager}. This will enumerate all files present in the
     * classpath, i.e. go through all JARs and list all files within those JARs
     * to get the list of JAR files that constitute the classpath. This works
     * only when runing {@code javac}, and will cause {@link ClassCastException}
     * otherwise.
     *
     * @return CLASSPATH of the current {@code javac} tool
     */
    String determineDelegateClasspath() throws IOException {
        final JavacProcessingEnvironment javacEnv = (JavacProcessingEnvironment) processingEnv;

        final Context context = javacEnv.getContext();
        try (final JavaFileManager fileManager = context.get(JavaFileManager.class)) {
            final Set<String> classpathElements = StreamSupport
                .stream(
                    fileManager.list(StandardLocation.CLASS_PATH, "", EnumSet.allOf(JavaFileObject.Kind.class), true)
                        .spliterator(),
                    false)
                .map(RestDslSwaggerAnnotationProcessor::basePathOf).filter(Objects::nonNull)
                .collect(Collectors.toSet());

            return String.join(File.pathSeparator, classpathElements);
        }
    }

    /**
     * Tries to open an {@link InputStream} to he specified URI. Only local
     * filesystem paths are currently supported. If the specificationUri points
     * to a file it is used, then file is searched relative to
     * {@code src/main/resources} and finally in the tool output directory or
     * the source path. As {@link StandardLocation#SOURCE_PATH} can be
     * optionally supported by the tool this can fail.
     *
     * @param specificationUri URI i.e. currently only filesystem path (relative
     *            or absolute)
     * @param element element to report errors on
     * @return stream to the specification pointed by specificationUri
     */
    InputStream determineSpecificationStream(final String specificationUri, final Element element) throws IOException {
        final Path specificationFilePath = new File(specificationUri).toPath();
        if (Files.exists(specificationFilePath)) {
            return Files.newInputStream(specificationFilePath);
        }

        final Path resourcesPath = Paths.get("src", "main", "resources");
        final Path specificationInResourcesPath = new File(resourcesPath.toFile(), specificationUri).toPath();
        if (Files.exists(specificationInResourcesPath)) {
            return Files.newInputStream(specificationInResourcesPath);
        }

        FileObject specification;
        try {
            specification = loadResource(StandardLocation.CLASS_OUTPUT, specificationUri);
        } catch (final IOException e) {
            try {
                specification = loadResource(StandardLocation.SOURCE_PATH, specificationUri);
            } catch (final IOException | IllegalArgumentException second) {
                messager.printMessage(Kind.ERROR, "Unable to load specification '" + specificationUri
                    + "' from output directory, this could be because the build system did not copy the specification"
                    + " to the output, perhaps using the 'javac' tool directly? (got: " + e + " and " + second + ")",
                    element);
                throw second;
            }
        }

        return specification.openInputStream();
    }

    /**
     * Tries to load the resourceName from the specified location and in doing
     * so tries to test if the resource can be read. As {@link Filer} can return
     * {@link FileObject}s that are non-existent.
     */
    FileObject loadResource(final Location location, final String resourceName) throws IOException {
        final File tmpFile = new File(resourceName);
        final String packageName = Optional.ofNullable(tmpFile.getParent()).orElse("").replace('/', '.').replace('\\',
            '.');
        final String resource = tmpFile.getName();

        final FileObject specification = filer.getResource(location, packageName, resource);

        try (final InputStream tmp = specification.openInputStream()) {
            // test the file existence
        }

        return specification;
    }

    /**
     * Processes a single class annotated with {@link SwaggerRestDsl} annotation
     * and invokes the configured {@link RestDslGenerator}.
     *
     * @param element the annotated element
     */
    void process(final Element element) {
        final SwaggerRestDsl swaggerRestDsl = element.getAnnotation(SwaggerRestDsl.class);

        try {
            final Swagger swagger = tryLoadingSpecificationFrom(swaggerRestDsl, element);

            final Optional<DestinationGenerator> destinationGenerator = resolveDestinationGeneratorType(element);

            final RestDslSourceCodeGenerator<Filer> generator = RestDslGenerator.toFiler(swagger);
            if (destinationGenerator.isPresent()) {
                generator.withDestinationGenerator(destinationGenerator.get());
            }

            final String className = swaggerRestDsl.className();
            if (ObjectHelper.isNotEmpty(className)) {
                generator.withClassName(className);
            }

            final String packageName = swaggerRestDsl.packageName();
            if (ObjectHelper.isNotEmpty(packageName)) {
                generator.withPackageName(packageName);
            }

            generator.generate(filer);
        } catch (final IOException e) {
            messager.printMessage(Kind.NOTE, "Unable to process '" + element + "' (got: " + e + ")", element);
        }
    }

    /**
     * Determines the {@link SwaggerRestDsl#destinationGenerator()} class by
     * looking in the output for an already compiled version, and if not found
     * tries to compile the appropriate source code in a temp directory and load
     * it. This has many ways of not succeeding, for instance if the
     * {@link DestinationGenerator} has dependencies that cannot be
     * loaded/instantiated.
     *
     * @param element the annotated element
     * @return maybe DestinationGenerator
     */
    Optional<DestinationGenerator> resolveDestinationGeneratorType(final Element element) throws IOException {
        // an awkward way of getting annotation value that is of Class type
        final Optional<? extends AnnotationMirror> maybeAnnotation = element.getAnnotationMirrors().stream()
            .filter(m -> swaggerRestDslType.equals(m.getAnnotationType())).findAny();

        if (!maybeAnnotation.isPresent()) {
            return Optional.empty();
        }

        final AnnotationMirror annotation = maybeAnnotation.get();

        final Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValues = annotation
            .getElementValues();

        final Optional<? extends AnnotationValue> maybeAnnotationValue = annotationValues.keySet().stream()
            .filter(k -> "destinationGenerator".equals(k.getSimpleName().toString())).findAny()
            .map(annotationValues::get);

        if (!maybeAnnotationValue.isPresent()) {
            return Optional.empty();
        }

        final AnnotationValue annotationValue = maybeAnnotationValue.get();

        // here we finally get the type of the destinationGenerator
        final TypeMirror annotationTypeMirrorValue = (TypeMirror) annotationValue.getValue();

        final Types types = processingEnv.getTypeUtils();

        // converted to TypeElement
        final TypeElement annotationType = (TypeElement) types.asElement(annotationTypeMirrorValue);

        // uses black magic to load the class
        final Optional<Class<?>> maybeClazz = Stream
            .of(tryLoadingCompiledClass(annotationType), tryCompilingTheClass(annotationType)).filter(Objects::nonNull)
            .findFirst();

        if (!maybeClazz.isPresent()) {
            messager.printMessage(Kind.ERROR,
                "You have specified the 'destinationGenerator' property on the @SwaggerRestDsl annotation but the"
                    + " specified DestinationGenerator class could not be loaded",
                element);
            return Optional.empty();
        }

        if (!DestinationGenerator.class.isAssignableFrom(maybeClazz.get())) {
            messager.printMessage(Kind.ERROR,
                "The specified destination generator class does not implement the DestinationGenerator interface",
                element, annotation, annotationValue);
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        final Class<DestinationGenerator> destinationGenerator = (Class<DestinationGenerator>) maybeClazz.get();

        try {
            return Optional.of(destinationGenerator.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            messager.printMessage(Kind.ERROR,
                "Unable to create an instanceo of the specified destination generator, due to: '" + e.getMessage()
                    + "'. Make sure that the DestinationGenerator has a public no argument constructor",
                element, annotation, annotationValue);
        }

        return Optional.empty();
    }

    /**
     * Determines the correct source file and invokes the {@code javac} tool
     * with classpath of the current compilation to compile the source into a
     * temp directory. And using the {@link URLClassLoader} loads it from there.
     * Basically witchcraft.
     *
     * @param annotationType the type to compile and load
     *
     * @return the compiled/loaded class
     */
    Class<?> tryCompilingTheClass(final TypeElement annotationType) {
        final StringBuilder fullyQualifiedClassName = new StringBuilder(annotationType.getSimpleName());

        Name className = annotationType.getSimpleName();
        Element enclosingElement = annotationType.getEnclosingElement();
        while (enclosingElement.getKind() != ElementKind.PACKAGE) {
            className = enclosingElement.getSimpleName();
            fullyQualifiedClassName.insert(0, '$').insert(0, enclosingElement.getSimpleName());
            enclosingElement = enclosingElement.getEnclosingElement();
        }

        final CharSequence packageName = ((PackageElement) enclosingElement).getQualifiedName();
        if (packageName.length() > 0) {
            fullyQualifiedClassName.insert(0, '.').insert(0, packageName);
        }

        final String sourceFileName = className + ".java";

        try {
            final FileObject resource;
            try {
                resource = filer.getResource(StandardLocation.SOURCE_PATH, packageName, sourceFileName);
            } catch (final Exception e) {
                messager.printMessage(Kind.ERROR, "Unable to Java source file for '" + packageName + "/"
                    + sourceFileName + "' from source directory (got: " + e + ")");
                return null;
            }

            try (final InputStream stream = resource.openInputStream()) {
                // check that the resource exists
            }

            final URI uri = resource.toUri();
            final Path sourceFilePath = Paths.get(uri);

            final String classpath = determineDelegateClasspath();

            final List<String> javacArgs = new ArrayList<>();
            javacArgs.add("-cp");
            javacArgs.add(classpath);

            final File tmpDir = File.createTempFile("rest-dsl-swagger-apt", ".classes");
            tmpDir.delete();
            tmpDir.mkdirs();
            tmpDir.deleteOnExit();
            final String output = tmpDir.getCanonicalPath();

            javacArgs.add("-d");
            javacArgs.add(output);

            javacArgs.add("-proc:none");

            javacArgs.add(sourceFilePath.toAbsolutePath().toString());

            ToolProvider.getSystemJavaCompiler().run(null, null, null, javacArgs.toArray(new String[javacArgs.size()]));

            try (final URLClassLoader classLoader = new URLClassLoader(new URL[] {new URL("file://" + output + "/")},
                RestDslSwaggerAnnotationProcessor.class.getClassLoader())) {

                final Class<?> clazz = classLoader.loadClass(fullyQualifiedClassName.toString());

                return clazz;
            }
        } catch (final IOException e) {
            messager.printMessage(Kind.NOTE,
                "Unable to find the specified source file for the destination generator, it was expected at "
                    + packageName + "/" + sourceFileName + ", (got: " + e + ")",
                annotationType);
            return null;
        } catch (final ClassNotFoundException e) {
            messager.printMessage(Kind.WARNING,
                "Unable to load the compiled destination generator class (got: " + e + ")", annotationType);
            return null;
        }
    }

    /**
     * Tries to load the compiled class from the compiler output. In most cases
     * if this is not incremental compilation (i.e. clean compile) the class
     * will not exist at this point and it will fail to load it.
     */
    Class<?> tryLoadingCompiledClass(final TypeElement annotationType) {
        final StringBuilder classFileName = new StringBuilder(annotationType.getSimpleName()).append(".class");
        Element enclosingElement = annotationType.getEnclosingElement();
        while (enclosingElement.getKind() != ElementKind.PACKAGE) {
            classFileName.insert(0, '$').insert(0, enclosingElement.getSimpleName());
            enclosingElement = enclosingElement.getEnclosingElement();
        }

        final CharSequence packageName = ((PackageElement) enclosingElement).getQualifiedName();

        try {
            final FileObject classResource = filer.getResource(StandardLocation.CLASS_OUTPUT, packageName,
                classFileName);

            final OutputClassLoader outputClassLoader = new OutputClassLoader();
            final Class<?> clazz = outputClassLoader.load(classResource);

            return clazz;
        } catch (final IOException e) {
            messager.printMessage(Kind.NOTE,
                "The class file containing the specified destination generator is not yet compiled, (got: " + e + ")",
                annotationType);
            return null;
        }
    }

    /**
     * Tries to load the specification pointed at by the {@link SwaggerRestDsl}
     * annotation.
     */
    Swagger tryLoadingSpecificationFrom(final SwaggerRestDsl swaggerRestDsl, final Element element) throws IOException {
        String specificationUri = swaggerRestDsl.specificationUri();

        if (specificationUri == null || specificationUri.trim().isEmpty()) {
            specificationUri = "swagger.json";
        }

        try (InputStream stream = determineSpecificationStream(specificationUri, element)) {
            final JsonNode tree = new ObjectMapper().reader().readTree(stream);

            try {
                return new SwaggerParser().read(tree);
            } catch (final ServiceConfigurationError ignored) {
                // SwaggerParser::getExtensions uses ServiceLoader::load that in
                // turn uses thread context classloader which does not go well
                // with OSGI (Eclipse) we need to set the context classloader to
                // Swagger's own classloader for this to work
                final ClassLoader original = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(SwaggerParser.class.getClassLoader());

                    return new SwaggerParser().read(tree);
                } finally {
                    Thread.currentThread().setContextClassLoader(original);
                }
            }
        } catch (final IOException e) {
            messager.printMessage(Kind.ERROR,
                "Could not parse the given specification loaded from '" + specificationUri + "' (got: " + e + ")",
                element);
            throw e;
        }
    }

    /**
     * Computes the base path of the {@link JavaFileObject}, that is the path to
     * the JAR file that contains the given object. If it's path to a file then
     * a stream of parent directory that is named 'bin' or contains 'classes' is
     * returned.
     *
     * @param javaFileObject the object (class, resource, ...) contained within
     *            a JAR file
     * @return paths to the JAR files or directories containing the object
     */
    static String basePathOf(final JavaFileObject javaFileObject) {
        final URI uri = javaFileObject.toUri();

        final String scheme = uri.getScheme();
        if ("jar".equals(scheme)) {
            final String schemeSpecificPart = uri.getSchemeSpecificPart();

            final int start = schemeSpecificPart.indexOf(':') + 1;
            final int end = schemeSpecificPart.indexOf('!');

            final String jarPath = schemeSpecificPart.substring(start, end);

            return jarPath;
        } else if ("file".equals(scheme)) {
            Path path = Paths.get(uri);

            while ((path = path.getParent()) != null && path.getFileName() != null) {
                final String name = path.getFileName().toString();

                if ("bin".equals(name) || name.contains("classes")) {
                    return path.toString();
                }
            }
        }

        return null;
    }

}
