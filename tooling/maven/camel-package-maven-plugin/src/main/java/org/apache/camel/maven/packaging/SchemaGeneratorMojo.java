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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

import org.apache.camel.maven.packaging.generics.GenericsUtil;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.EipModel.EipOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.JavadocHelper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.tooling.util.srcgen.GenericType;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassInfo.NestingType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;

@Mojo(name = "generate-schema", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class SchemaGeneratorMojo extends AbstractGeneratorMojo {

    public static final DotName XML_ROOT_ELEMENT = DotName.createSimple(XmlRootElement.class.getName());
    public static final DotName XML_TYPE = DotName.createSimple(XmlType.class.getName());

    // special when using expression/predicates in the model
    private static final String ONE_OF_TYPE_NAME = "org.apache.camel.model.ExpressionSubElementDefinition";
    private static final String[] ONE_OF_LANGUAGES = new String[] {
            "org.apache.camel.model.language.ExpressionDefinition",
            "org.apache.camel.model.language.NamespaceAwareExpression" };
    // special for inputs (these classes have sub classes, so we use this to find all classes)
    private static final String[] ONE_OF_INPUTS
            = new String[] { "org.apache.camel.model.ProcessorDefinition", "org.apache.camel.model.rest.VerbDefinition" };
    // special for outputs (these classes have sub classes, so we use this to
    // find all classes - and not in particular if they support outputs or not)
    private static final String[] ONE_OF_OUTPUTS = new String[] {
            "org.apache.camel.model.ProcessorDefinition", "org.apache.camel.model.NoOutputDefinition",
            "org.apache.camel.model.OutputDefinition", "org.apache.camel.model.OutputExpressionNode",
            "org.apache.camel.model.NoOutputExpressionNode", "org.apache.camel.model.SendDefinition",
            "org.apache.camel.model.InterceptDefinition", "org.apache.camel.model.WhenDefinition",
            "org.apache.camel.model.ToDynamicDefinition" };
    // special for verbs (these classes have sub classes, so we use this to find all classes)
    private static final String[] ONE_OF_VERBS = new String[] { "org.apache.camel.model.rest.VerbDefinition" };

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    protected File classesDirectory;
    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    private IndexView indexView;
    private final Map<String, JavaClassSource> sources = new HashMap<>();

    public SchemaGeneratorMojo() {
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (classesDirectory == null) {
            classesDirectory = new File(project.getBuild().getOutputDirectory());
        }
        if (sourcesOutputDir == null) {
            sourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
        }
        if (resourcesOutputDir == null) {
            resourcesOutputDir = new File(project.getBasedir(), "src/generated/resources");
        }
        if (!classesDirectory.isDirectory()) {
            return;
        }

        IndexView index = getIndex();

        index.getAnnotations(XML_ROOT_ELEMENT);
        //
        // @XmlRootElement
        // core elements
        //
        Set<ClassInfo> coreElements = index.getAnnotations(XML_ROOT_ELEMENT).stream()
                .filter(cpa -> cpa.target().kind() == Kind.CLASS)
                .filter(cpa -> cpa.target().asClass().nestingType() == NestingType.TOP_LEVEL)
                .filter(cpa -> cpa.target().asClass().name().toString().startsWith("org.apache.camel.model."))
                .map(cpa -> cpa.target().asClass())
                .collect(Collectors.toSet());
        if (!coreElements.isEmpty()) {
            getLog().info(String.format("Found %d core elements", coreElements.size()));
        }

        // we want them to be sorted
        for (ClassInfo element : coreElements) {
            processModelClass(element, null);
        }

        // spring elements
        Set<ClassInfo> springElements = index.getAnnotations(XML_ROOT_ELEMENT).stream()
                .filter(cpa -> cpa.target().kind() == Kind.CLASS)
                .filter(cpa -> cpa.target().asClass().nestingType() == NestingType.TOP_LEVEL)
                .filter(cpa -> {
                    String javaTypeName = cpa.target().asClass().name().toString();
                    return javaTypeName.startsWith("org.apache.camel.spring.")
                            || javaTypeName.startsWith("org.apache.camel.core.xml.");
                })
                .map(cpa -> cpa.target().asClass())
                .collect(Collectors.toSet());
        if (!springElements.isEmpty()) {
            getLog().info(String.format("Found %d spring elements", springElements.size()));
        }

        for (ClassInfo element : springElements) {
            processModelClass(element, null);
        }
    }

    private void processModelClass(ClassInfo element, Set<String> propertyPlaceholderDefinitions)
            throws MojoExecutionException {
        // skip abstract classes
        if (Modifier.isAbstract(element.flags())) {
            return;
        }
        // skip unwanted classes which are "abstract" holders
        if (element.name().toString().equals(ONE_OF_TYPE_NAME)) {
            return;
        }

        AnnotationValue annotationValue = element.classAnnotation(XML_ROOT_ELEMENT).value("name");
        String aName = annotationValue != null ? annotationValue.asString() : null;
        if (Strings.isNullOrEmpty(aName) || "##default".equals(aName)) {
            aName = element.classAnnotation(XML_TYPE).value("name").asString();
        }
        final String name = aName;

        // lets use the xsd name as the file name
        String fileName;
        if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
            fileName = element.simpleName() + PackageHelper.JSON_SUFIX;
        } else {
            fileName = name + PackageHelper.JSON_SUFIX;
        }

        String javaTypeName = element.name().toString();
        Class<?> classElement = loadClass(javaTypeName);

        // gather eip information
        EipModel eipModel = findEipModelProperties(classElement, name);

        // get endpoint information which is divided into paths and options
        // (though there should really only be one path)
        Set<EipOptionModel> eipOptions = new TreeSet<>(new EipOptionComparator(eipModel));
        findClassProperties(eipOptions, classElement, classElement, "", name);

        eipOptions.forEach(eipModel::addOption);

        // after we have found all the options then figure out if the model
        // accepts input/output
        eipModel.setInput(hasInput(classElement));
        eipModel.setOutput(hasOutput(eipModel));

        if (Strings.isNullOrEmpty(eipModel.getTitle())) {
            eipModel.setTitle(Strings.asTitle(eipModel.getName()));
        }
        if (eipModel.isOutput()) {
            // filter out outputs if we do not support it
            eipModel.getOptions().removeIf(o -> "outputs".equals(o.getName()));
        }

        // write json schema file
        String packageName = javaTypeName.substring(0, javaTypeName.lastIndexOf('.'));
        String json = JsonMapper.createParameterJsonSchema(eipModel);
        updateResource(
                resourcesOutputDir.toPath(),
                packageName.replace('.', '/') + "/" + fileName,
                json);

    }

    private IndexView getIndex() {
        if (indexView == null) {
            Path output = Paths.get(project.getBuild().getOutputDirectory());
            try (InputStream is = Files.newInputStream(output.resolve("META-INF/jandex.idx"))) {
                indexView = new IndexReader(is).read();
            } catch (IOException e) {
                throw new RuntimeException("IOException: " + e.getMessage(), e);
            }
        }
        return indexView;
    }

    protected EipModel findEipModelProperties(Class<?> classElement, String name) {
        EipModel model = new EipModel();
        model.setJavaType(classElement.getName());
        model.setName(name);

        boolean deprecated = classElement.getAnnotation(Deprecated.class) != null;
        model.setDeprecated(deprecated);

        Metadata metadata = classElement.getAnnotation(Metadata.class);
        if (metadata != null) {
            if (!Strings.isNullOrEmpty(metadata.label())) {
                model.setLabel(metadata.label());
            }
            if (!Strings.isNullOrEmpty(metadata.title())) {
                model.setTitle(metadata.title());
            }
            if (!Strings.isNullOrEmpty(metadata.firstVersion())) {
                model.setFirstVersion(metadata.firstVersion());
            }
        }

        // favor to use class javadoc of component as description
        String doc = getDocComment(classElement);
        if (doc != null) {
            // need to sanitize the description first (we only want a
            // summary)
            doc = JavadocHelper.sanitizeDescription(doc, true);
            // the javadoc may actually be empty, so only change the doc
            // if we got something
            if (!Strings.isNullOrEmpty(doc)) {
                model.setDescription(doc);
            }
        }

        return model;
    }

    protected void findClassProperties(
            Set<EipOptionModel> eipOptions,
            Class<?> originalClassType, Class<?> classElement,
            String prefix, String modelName) {
        while (true) {
            for (Field fieldElement : classElement.getDeclaredFields()) {

                String fieldName = fieldElement.getName();

                XmlAttribute attribute = fieldElement.getAnnotation(XmlAttribute.class);
                if (attribute != null) {
                    boolean skip = processAttribute(originalClassType, classElement, fieldElement, fieldName, attribute,
                            eipOptions, prefix, modelName);
                    if (skip) {
                        continue;
                    }
                }

                XmlValue value = fieldElement.getAnnotation(XmlValue.class);
                if (value != null) {
                    processValue(originalClassType, classElement, fieldElement, fieldName, value, eipOptions, prefix,
                            modelName);
                }

                XmlElements elements = fieldElement.getAnnotation(XmlElements.class);
                if (elements != null) {
                    processElements(originalClassType, classElement, elements, fieldElement, eipOptions, prefix);
                }

                XmlElement element = fieldElement.getAnnotation(XmlElement.class);
                if (element != null) {
                    processElement(originalClassType, classElement, element, fieldElement, eipOptions, prefix);
                }

                // special for eips which has outputs or requires an expressions
                XmlElementRef elementRef = fieldElement.getAnnotation(XmlElementRef.class);
                if (elementRef != null) {

                    // special for routes
                    processRoutes(originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for outputs
                    processOutputs(originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for when clauses (choice eip)
                    processRefWhenClauses(originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for rests (rest-dsl)
                    processRests(originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for verbs (rest-dsl)
                    processVerbs(originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for expression
                    processRefExpression(originalClassType, classElement, elementRef, fieldElement, fieldName, eipOptions,
                            prefix);

                }
            }

            // special when we process these nodes as they do not use JAXB
            // annotations on fields, but on methods
            if ("OptionalIdentifiedDefinition".equals(classElement.getSimpleName())) {
                processIdentified(originalClassType, classElement, eipOptions, prefix);
            } else if ("RouteDefinition".equals(classElement.getSimpleName())) {
                processRoute(originalClassType, classElement, eipOptions, prefix);
            }

            // check super classes which may also have fields
            Class<?> superclass = classElement.getSuperclass();
            if (superclass != null) {
                classElement = superclass;
            } else {
                break;
            }
        }
    }

    private boolean processAttribute(
            Class<?> originalClassType, Class<?> classElement,
            Field fieldElement, String fieldName,
            XmlAttribute attribute, Set<EipOptionModel> eipOptions,
            String prefix, String modelName) {
        String name = attribute.name();
        if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
            name = fieldName;
        }

        // we want to skip inheritErrorHandler which is only applicable for
        // the load-balancer
        boolean loadBalancer = "LoadBalanceDefinition".equals(originalClassType.getSimpleName().toString());
        if (!loadBalancer && "inheritErrorHandler".equals(name)) {
            return true;
        }

        Metadata metadata = fieldElement.getAnnotation(Metadata.class);

        name = prefix + name;
        Class<?> fieldTypeElement = fieldElement.getType();
        String fieldTypeName = getTypeName(GenericsUtil.resolveType(originalClassType, fieldElement));
        boolean isDuration = false;
        if (metadata != null && !Strings.isNullOrEmpty(metadata.javaType())) {
            String jt = metadata.javaType();
            if ("java.time.Duration".equals(jt)) {
                isDuration = true;
            } else {
                fieldTypeName = jt;
            }
        }

        String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
        String docComment = findJavaDoc(fieldElement, fieldName, name, classElement, true);
        boolean required = attribute.required();
        // metadata may overrule element required
        required = findRequired(fieldElement, required);

        // gather enums
        Set<String> enums = new TreeSet<>();
        boolean isEnum;
        if (metadata != null && !Strings.isNullOrEmpty(metadata.enums())) {
            isEnum = true;
            String[] values = metadata.enums().split(",");
            for (String val : values) {
                enums.add(val.trim());
            }
        } else {
            isEnum = fieldTypeElement.isEnum();
            if (isEnum) {
                for (Object val : fieldTypeElement.getEnumConstants()) {
                    // make the enum nicely human readable instead of typically upper cased
                    String str = val.toString();
                    str = SchemaHelper.camelCaseToDash(str);
                    enums.add(str);
                }
            }
        }

        String displayName = null;
        if (metadata != null) {
            displayName = metadata.displayName();
        }
        boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;
        String deprecationNote = null;
        if (metadata != null) {
            deprecationNote = metadata.deprecationNote();
        }

        EipOptionModel ep = createOption(name, displayName, "attribute", fieldTypeName,
                required, defaultValue, docComment, deprecated, deprecationNote, isEnum, enums,
                false, null, false, isDuration);
        eipOptions.add(ep);

        return false;
    }

    private void processValue(
            Class<?> originalClassType, Class<?> classElement, Field fieldElement,
            String fieldName, XmlValue value, Set<EipOptionModel> eipOptions, String prefix, String modelName) {
        // XmlValue has no name attribute
        String name = fieldName;

        if ("method".equals(modelName) || "tokenize".equals(modelName) || "xtokenize".equals(modelName)) {
            // skip expression attribute on these three languages as they are
            // solely configured using attributes
            if ("expression".equals(name)) {
                return;
            }
        }

        name = prefix + name;
        String fieldTypeName = getTypeName(GenericsUtil.resolveType(originalClassType, fieldElement));

        String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
        String docComment = findJavaDoc(fieldElement, fieldName, name, classElement, true);
        boolean required = true;
        // metadata may overrule element required
        required = findRequired(fieldElement, required);

        String displayName = null;
        boolean isDuration = false;
        Metadata metadata = fieldElement.getAnnotation(Metadata.class);
        if (metadata != null && !Strings.isNullOrEmpty(metadata.javaType())) {
            String jt = metadata.javaType();
            if ("java.time.Duration".equals(jt)) {
                isDuration = true;
            } else {
                fieldTypeName = jt;
            }
        }

        if (metadata != null) {
            displayName = metadata.displayName();
        }
        boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;
        String deprecationNote = null;
        if (metadata != null) {
            deprecationNote = metadata.deprecationNote();
        }

        EipOptionModel ep = createOption(name, displayName, "value", fieldTypeName, required,
                defaultValue, docComment, deprecated, deprecationNote, false, null,
                false, null, false, isDuration);
        eipOptions.add(ep);
    }

    private void processElement(
            Class<?> originalClassType, Class<?> classElement, XmlElement element, Field fieldElement,
            Set<EipOptionModel> eipOptions, String prefix) {
        String fieldName = fieldElement.getName();
        if (element != null) {

            Metadata metadata = fieldElement.getAnnotation(Metadata.class);

            String kind = "element";
            String name = element.name();
            if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            Class<?> fieldTypeElement = fieldElement.getType();
            String fieldTypeName = getTypeName(GenericsUtil.resolveType(originalClassType, fieldElement));
            boolean isDuration = false;
            if (metadata != null && !Strings.isNullOrEmpty(metadata.javaType())) {
                String jt = metadata.javaType();
                if ("java.time.Duration".equals(jt)) {
                    isDuration = true;
                } else {
                    fieldTypeName = jt;
                }
            }

            String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
            String docComment = findJavaDoc(fieldElement, fieldName, name, classElement, true);
            boolean required = element.required();
            // metadata may overrule element required
            required = findRequired(fieldElement, required);

            // is it used as predicate (check field first and then fallback to
            // its class)
            boolean asPredicate = fieldElement.getAnnotation(AsPredicate.class) != null;
            if (!asPredicate) {
                asPredicate = classElement.getAnnotation(AsPredicate.class) != null;
            }

            // gather enums
            Set<String> enums = new TreeSet<>();
            boolean isEnum;
            if (metadata != null && !Strings.isNullOrEmpty(metadata.enums())) {
                isEnum = true;
                String[] values = metadata.enums().split(",");
                for (String val : values) {
                    enums.add(val.trim());
                }
            } else {
                isEnum = fieldTypeElement.isEnum();
                if (isEnum) {
                    for (Object val : fieldTypeElement.getEnumConstants()) {
                        // make the enum nicely human readable instead of typically upper cased
                        String str = val.toString();
                        str = SchemaHelper.camelCaseToDash(str);
                        enums.add(str);
                    }
                }
            }

            // gather oneOf expression/predicates which uses language
            Set<String> oneOfTypes = new TreeSet<>();
            boolean isOneOf = ONE_OF_TYPE_NAME.equals(fieldTypeName);
            if (isOneOf) {
                // okay its actually an language expression, so favor using that
                // in the eip option
                kind = "expression";
                oneOfTypes = getOneOfs(ONE_OF_LANGUAGES);
            }
            // special for otherwise as we want to indicate that the element is
            if ("otherwise".equals(name)) {
                isOneOf = true;
                oneOfTypes.add("otherwise");
            }

            String displayName = null;
            if (metadata != null) {
                displayName = metadata.displayName();
            }
            boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;
            String deprecationNote = null;
            if (metadata != null) {
                deprecationNote = metadata.deprecationNote();
            }

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, required, defaultValue,
                    docComment, deprecated, deprecationNote, isEnum, enums, isOneOf,
                    oneOfTypes, asPredicate, isDuration);
            eipOptions.add(ep);
        }
    }

    private void processElements(
            Class<?> originalClassType, Class<?> classElement, XmlElements elements, Field fieldElement,
            Set<EipOptionModel> eipOptions, String prefix) {
        String fieldName = fieldElement.getName();
        if (elements != null) {
            String kind = "element";
            String name = fieldName;
            name = prefix + name;

            String fieldTypeName = getTypeName(GenericsUtil.resolveType(originalClassType, fieldElement));

            String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
            String docComment = findJavaDoc(fieldElement, fieldName, name, classElement, true);

            boolean required = true;
            required = findRequired(fieldElement, required);

            // gather oneOf of the elements
            Set<String> oneOfTypes = new TreeSet<>();
            for (XmlElement element : elements.value()) {
                String child = element.name();
                oneOfTypes.add(child);
            }

            String displayName = null;
            Metadata metadata = fieldElement.getAnnotation(Metadata.class);
            if (metadata != null) {
                displayName = metadata.displayName();
            }
            boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;
            String deprecationNote = null;
            if (metadata != null) {
                deprecationNote = metadata.deprecationNote();
            }

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, required, defaultValue, docComment,
                    deprecated, deprecationNote, false, null, true, oneOfTypes,
                    false, false);
            eipOptions.add(ep);
        }
    }

    private void processRoute(
            Class<?> originalClassType, Class<?> classElement,
            Set<EipOptionModel> eipOptions, String prefix) {

        // group
        String docComment = findJavaDoc(null, "group", null, classElement, true);
        EipOptionModel ep = createOption("group", "Group", "attribute", "java.lang.String", false, "", docComment, false, null,
                false, null, false, null, false, false);
        eipOptions.add(ep);

        // group
        docComment = findJavaDoc(null, "streamCache", null, classElement, true);
        ep = createOption("streamCache", "Stream Cache", "attribute", "java.lang.String", false, "", docComment, false, null,
                false, null, false, null, false, false);
        eipOptions.add(ep);

        // trace
        docComment = findJavaDoc(null, "trace", null, classElement, true);
        ep = createOption("trace", "Trace", "attribute", "java.lang.String", false, "", docComment, false, null, false, null,
                false, null, false, false);
        eipOptions.add(ep);

        // message history
        docComment = findJavaDoc(null, "messageHistory", null, classElement, true);
        ep = createOption("messageHistory", "Message History", "attribute", "java.lang.String", false, "true", docComment,
                false, null, false, null, false, null, false, false);
        eipOptions.add(ep);

        // log mask
        docComment = findJavaDoc(null, "logMask", null, classElement, true);
        ep = createOption("logMask", "Log Mask", "attribute", "java.lang.String", false, "false", docComment, false, null,
                false, null, false, null, false, false);
        eipOptions.add(ep);

        // delayer
        docComment = findJavaDoc(null, "delayer", null, classElement, true);
        ep = createOption("delayer", "Delayer", "attribute", "java.lang.String", false, "", docComment, false, null, false,
                null, false, null, false, true);
        eipOptions.add(ep);

        // autoStartup
        docComment = findJavaDoc(null, "autoStartup", null, classElement, true);
        ep = createOption("autoStartup", "Auto Startup", "attribute", "java.lang.String", false, "true", docComment, false,
                null, false, null, false, null, false, false);
        eipOptions.add(ep);

        // startupOrder
        docComment = findJavaDoc(null, "startupOrder", null, classElement, true);
        ep = createOption("startupOrder", "Startup Order", "attribute", "java.lang.Integer", false, "", docComment, false, null,
                false, null, false, null, false, false);
        eipOptions.add(ep);

        // errorHandlerRef
        docComment = findJavaDoc(null, "errorHandlerRef", null, classElement, true);
        ep = createOption("errorHandlerRef", "Error Handler", "attribute", "java.lang.String", false, "", docComment, false,
                null, false, null, false, null, false, false);
        eipOptions.add(ep);

        // routePolicyRef
        docComment = findJavaDoc(null, "routePolicyRef", null, classElement, true);
        ep = createOption("routePolicyRef", "Route Policy", "attribute", "java.lang.String", false, "", docComment, false, null,
                false, null, false, null, false, false);
        eipOptions.add(ep);

        // shutdownRoute
        Set<String> enums = new LinkedHashSet<>();
        enums.add("Default");
        enums.add("Defer");
        docComment = findJavaDoc(null, "shutdownRoute", "Default", classElement, true);
        ep = createOption("shutdownRoute", "Shutdown Route", "attribute", "org.apache.camel.ShutdownRoute", false, "",
                docComment, false, null, true, enums, false, null, false, false);
        eipOptions.add(ep);

        // shutdownRunningTask
        enums = new LinkedHashSet<>();
        enums.add("CompleteCurrentTaskOnly");
        enums.add("CompleteAllTasks");
        docComment = findJavaDoc(null, "shutdownRunningTask", "CompleteCurrentTaskOnly", classElement, true);
        ep = createOption("shutdownRunningTask", "Shutdown Running Task", "attribute", "org.apache.camel.ShutdownRunningTask",
                false, "", docComment, false, null, true, enums,
                false, null, false, false);
        eipOptions.add(ep);

        // input
        Set<String> oneOfTypes = new TreeSet<>();
        oneOfTypes.add("from");
        docComment = findJavaDoc(null, "input", null, classElement, true);
        ep = createOption("input", "Input", "element", "org.apache.camel.model.FromDefinition", true, "", docComment, false,
                null, false, null, true, oneOfTypes, false, false);
        eipOptions.add(ep);

        // outputs
        // gather oneOf which extends any of the output base classes
        oneOfTypes = new TreeSet<>();
        // find all classes that has that superClassName
        for (String superclass : ONE_OF_OUTPUTS) {
            for (ClassInfo ci : getIndex().getAllKnownSubclasses(DotName.createSimple(superclass))) {
                Class<?> child = loadClass(ci.name().toString());
                XmlRootElement rootElement = child.getAnnotation(XmlRootElement.class);
                if (rootElement != null) {
                    String childName = rootElement.name();
                    oneOfTypes.add(childName);
                }
            }
        }

        // remove some types which are not intended as an output in eips
        oneOfTypes.remove("route");

        docComment = findJavaDoc(null, "outputs", null, classElement, true);
        ep = createOption("outputs", "Outputs", "element", "java.util.List<org.apache.camel.model.ProcessorDefinition<?>>",
                true, "", docComment, false, null, false, null, true,
                oneOfTypes, false, false);
        eipOptions.add(ep);
    }

    /**
     * Special for process the OptionalIdentifiedDefinition
     */
    private void processIdentified(
            Class<?> originalClassType, Class<?> classElement,
            Set<EipOptionModel> eipOptions, String prefix) {

        // id
        String docComment = findJavaDoc(null, "id", null, classElement, true);
        EipOptionModel ep = createOption("id", "Id", "attribute", "java.lang.String", false, "", docComment, false, null, false,
                null, false, null, false, false);
        eipOptions.add(ep);

        // description
        docComment = findJavaDoc(null, "description", null, classElement, true);
        ep = createOption("description", "Description", "element", "org.apache.camel.model.DescriptionDefinition", false, "",
                docComment, false, null, false, null, false, null,
                false, false);
        eipOptions.add(ep);
    }

    /**
     * Special for processing an @XmlElementRef routes field
     */
    private void processRoutes(
            Class<?> originalClassType, XmlElementRef elementRef,
            Field fieldElement, String fieldName,
            Set<EipOptionModel> eipOptions, String prefix) {
        if ("routes".equals(fieldName)) {

            String fieldTypeName = getTypeName(GenericsUtil.resolveType(originalClassType, fieldElement));

            Set<String> oneOfTypes = new TreeSet<>();
            oneOfTypes.add("route");

            EipOptionModel ep = createOption("routes", "Routes", "element",
                    fieldTypeName, false, "", "Contains the Camel routes",
                    false, null, false, null, true, oneOfTypes,
                    false, false);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef rests field
     */
    private void processRests(
            Class<?> originalClassType, XmlElementRef elementRef,
            Field fieldElement, String fieldName,
            Set<EipOptionModel> eipOptions, String prefix) {
        if ("rests".equals(fieldName)) {

            String fieldTypeName = getTypeName(GenericsUtil.resolveType(originalClassType, fieldElement));

            Set<String> oneOfTypes = new TreeSet<>();
            oneOfTypes.add("rest");

            EipOptionModel ep = createOption("rests", "Rests", "element", fieldTypeName, false, "",
                    "Contains the rest services defined using the rest-dsl", false, null, false,
                    null, true, oneOfTypes, false, false);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef outputs field
     */
    private void processOutputs(
            Class<?> originalClassType, XmlElementRef elementRef,
            Field fieldElement, String fieldName, Set<EipOptionModel> eipOptions, String prefix) {

        if ("outputs".equals(fieldName) && supportOutputs(originalClassType)) {
            String kind = "element";
            String name = elementRef.name();
            if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            String fieldTypeName = getTypeName(GenericsUtil.resolveType(originalClassType, fieldElement));

            // gather oneOf which extends any of the output base classes
            Set<String> oneOfTypes = getOneOfs(ONE_OF_OUTPUTS);

            // remove some types which are not intended as an output in eips
            oneOfTypes.remove("route");
            String displayName = null;
            Metadata metadata = fieldElement.getAnnotation(Metadata.class);
            if (metadata != null) {
                displayName = metadata.displayName();
            }
            boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;
            String deprecationNote = null;
            if (metadata != null) {
                deprecationNote = metadata.deprecationNote();
            }

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, true, "", "", deprecated, deprecationNote,
                    false, null, true, oneOfTypes, false, false);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef verbs field (rest-dsl)
     */
    private void processVerbs(
            Class<?> originalClassType, XmlElementRef elementRef, Field fieldElement,
            String fieldName, Set<EipOptionModel> eipOptions, String prefix) {

        if ("verbs".equals(fieldName) && supportOutputs(originalClassType)) {
            String kind = "element";
            String name = elementRef.name();
            if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            String fieldTypeName = getTypeName(GenericsUtil.resolveType(originalClassType, fieldElement));

            String docComment = findJavaDoc(fieldElement, fieldName, name, originalClassType, true);

            // gather oneOf which extends any of the output base classes
            Set<String> oneOfTypes = getOneOfs(ONE_OF_VERBS);
            String displayName = null;
            Metadata metadata = fieldElement.getAnnotation(Metadata.class);
            if (metadata != null) {
                displayName = metadata.displayName();
            }
            boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;
            String deprecationNote = null;
            if (metadata != null) {
                deprecationNote = metadata.deprecationNote();
            }

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, true, "", docComment, deprecated,
                    deprecationNote, false, null, true, oneOfTypes, false, false);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef expression field
     */
    private void processRefExpression(
            Class<?> originalClassType, Class<?> classElement,
            XmlElementRef elementRef, Field fieldElement,
            String fieldName, Set<EipOptionModel> eipOptions, String prefix) {

        if ("expression".equals(fieldName)) {
            String kind = "expression";
            String name = elementRef.name();
            if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            String fieldTypeName = getTypeName(GenericsUtil.resolveType(originalClassType, fieldElement));

            // find javadoc from original class as it will override the
            // setExpression method where we can provide the javadoc for the
            // given EIP
            String docComment = findJavaDoc(fieldElement, fieldName, name, originalClassType, true);

            // is it used as predicate (check field first and then fallback to
            // its class / original class)
            boolean asPredicate = fieldElement.getAnnotation(AsPredicate.class) != null;
            if (!asPredicate) {
                asPredicate = classElement.getAnnotation(AsPredicate.class) != null;
            }
            if (!asPredicate) {
                asPredicate = originalClassType.getAnnotation(AsPredicate.class) != null;
            }

            // gather oneOf expression/predicates which uses language
            Set<String> oneOfTypes = getOneOfs(ONE_OF_LANGUAGES);

            String displayName = null;
            Metadata metadata = fieldElement.getAnnotation(Metadata.class);
            if (metadata != null) {
                displayName = metadata.displayName();
            }
            boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;
            String deprecationNote = null;
            if (metadata != null) {
                deprecationNote = metadata.deprecationNote();
            }

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, true, "", docComment, deprecated,
                    deprecationNote, false, null, true, oneOfTypes, asPredicate, false);
            eipOptions.add(ep);
        }
    }

    private Set<String> getOneOfs(String[] classes) {
        Set<String> oneOfTypes = new TreeSet<>();
        for (String superclass : classes) {
            for (ClassInfo ci : getIndex().getAllKnownSubclasses(DotName.createSimple(superclass))) {
                Class<?> child = loadClass(ci.name().toString());
                XmlRootElement rootElement = child.getAnnotation(XmlRootElement.class);
                if (rootElement != null) {
                    String childName = rootElement.name();
                    oneOfTypes.add(childName);
                }
            }
        }
        return oneOfTypes;
    }

    /**
     * Special for processing an @XmlElementRef when field
     */
    private void processRefWhenClauses(
            Class<?> originalClassType, XmlElementRef elementRef,
            Field fieldElement, String fieldName,
            Set<EipOptionModel> eipOptions, String prefix) {
        if ("whenClauses".equals(fieldName)) {
            String kind = "element";
            String name = elementRef.name();
            if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            String fieldTypeName = getTypeName(GenericsUtil.resolveType(originalClassType, fieldElement));

            // find javadoc from original class as it will override the
            // setExpression method where we can provide the javadoc for the
            // given EIP
            String docComment = findJavaDoc(fieldElement, fieldName, name, originalClassType, true);

            // indicate that this element is one of when
            Set<String> oneOfTypes = new HashSet<>();
            oneOfTypes.add("when");

            // when is predicate
            boolean asPredicate = true;

            String displayName = null;
            Metadata metadata = fieldElement.getAnnotation(Metadata.class);
            if (metadata != null) {
                displayName = metadata.displayName();
            }
            boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;
            String deprecationNote = null;
            if (metadata != null) {
                deprecationNote = metadata.deprecationNote();
            }

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, false, "", docComment, deprecated,
                    deprecationNote, false, null, true, oneOfTypes,
                    asPredicate, false);
            eipOptions.add(ep);
        }
    }

    /**
     * Whether the class supports outputs.
     * <p/>
     * There are some classes which does not support outputs, even though they have a outputs element.
     */
    private boolean supportOutputs(Class<?> classElement) {
        return loadClass("org.apache.camel.model.OutputNode").isAssignableFrom(classElement);
    }

    private String findDefaultValue(Field fieldElement, String fieldTypeName) {
        String defaultValue = null;
        Metadata metadata = fieldElement.getAnnotation(Metadata.class);
        if (metadata != null) {
            if (!Strings.isNullOrEmpty(metadata.defaultValue())) {
                defaultValue = metadata.defaultValue();
            }
        }
        if (defaultValue == null) {
            // if its a boolean type, then we use false as the default
            if ("boolean".equals(fieldTypeName) || "java.lang.Boolean".equals(fieldTypeName)) {
                defaultValue = "false";
            }
        }

        return defaultValue;
    }

    private boolean findRequired(Field fieldElement, boolean defaultValue) {
        Metadata metadata = fieldElement.getAnnotation(Metadata.class);
        if (metadata != null) {
            return metadata.required();
        }
        return defaultValue;
    }

    private boolean hasInput(Class<?> classElement) {
        for (String name : ONE_OF_INPUTS) {
            if (hasSuperClass(classElement, name)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOutput(EipModel model) {
        switch (model.getName()) {
            // if we are route/rest then we accept output
            case "route":
            case "routeTemplate":
            case "rest":
                return true;
            // special for transacted/policy which should not have output
            case "policy":
            case "transacted":
                return false;
            default:
                return model.getOptions().stream().anyMatch(option -> "outputs".equals(option.getName()));
        }
    }

    private EipOptionModel createOption(
            String name, String displayName, String kind, String type, boolean required, String defaultValue,
            String description, boolean deprecated,
            String deprecationNote, boolean enumType, Set<String> enums, boolean oneOf, Set<String> oneOfs, boolean asPredicate,
            boolean isDuration) {
        EipOptionModel option = new EipOptionModel();
        option.setName(name);
        option.setDisplayName(Strings.isNullOrEmpty(displayName) ? Strings.asTitle(name) : displayName);
        option.setKind(kind);
        option.setRequired(required);
        option.setDefaultValue("java.lang.Boolean".equals(type) && !Strings.isNullOrEmpty(defaultValue)
                ? Boolean.parseBoolean(defaultValue) : defaultValue);
        option.setDescription(JavadocHelper.sanitizeDescription(description, false));
        option.setDeprecated(deprecated);
        option.setDeprecationNote(Strings.isNullOrEmpty(deprecationNote) ? null : deprecationNote);
        option.setType(getType(type, enumType, isDuration));
        option.setJavaType(type);
        option.setEnums(enums != null && !enums.isEmpty() ? new ArrayList<>(enums) : null);
        option.setOneOfs(oneOfs != null && !oneOfs.isEmpty() ? new ArrayList<>(oneOfs) : null);
        option.setAsPredicate(asPredicate);
        return option;
    }

    private boolean hasSuperClass(Class<?> classElement, String superClassName) {
        return loadClass(superClassName).isAssignableFrom(classElement);
    }

    private String findJavaDoc(
            Field fieldElement, String fieldName, String name, Class<?> classElement, boolean builderPattern) {
        if (fieldElement != null) {
            Metadata md = fieldElement.getAnnotation(Metadata.class);
            if (md != null) {
                String doc = md.description();
                if (!Strings.isNullOrEmpty(doc)) {
                    return doc;
                }
            }
        }

        JavaClassSource source = javaClassSource(classElement.getName());
        FieldSource<JavaClassSource> field = source.getField(fieldName);
        if (field != null) {
            String doc = field.getJavaDoc().getFullText();
            if (!Strings.isNullOrEmpty(doc)) {
                return doc;
            }
        }

        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

        // special for mdcLoggingKeysPattern
        if ("setMdcLoggingKeysPattern".equals(setterName)) {
            setterName = "setMDCLoggingKeysPattern";
        }

        for (MethodSource<JavaClassSource> setter : source.getMethods()) {
            if (setter.getParameters().size() == 1
                    && setter.getName().equals(setterName)) {
                String doc = setter.getJavaDoc().getFullText();
                if (!Strings.isNullOrEmpty(doc)) {
                    return doc;
                }
            }
        }

        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (MethodSource<JavaClassSource> setter : source.getMethods()) {
            if (setter.getParameters().size() == 0
                    && setter.getName().equals(getterName)) {
                String doc = setter.getJavaDoc().getFullText();
                if (!Strings.isNullOrEmpty(doc)) {
                    return doc;
                }
            }
        }

        if (builderPattern) {
            if (name != null && !name.equals(fieldName)) {
                for (MethodSource<JavaClassSource> builder : source.getMethods()) {
                    if (builder.getParameters().size() == 1 && builder.getName().equals(name)) {
                        String doc = builder.getJavaDoc().getFullText();
                        if (!Strings.isNullOrEmpty(doc)) {
                            return doc;
                        }
                    }
                }
                for (MethodSource<JavaClassSource> builder : source.getMethods()) {
                    if (builder.getParameters().size() == 0 && builder.getName().equals(name)) {
                        String doc = builder.getJavaDoc().getFullText();
                        if (!Strings.isNullOrEmpty(doc)) {
                            return doc;
                        }
                    }
                }
            }
            for (MethodSource<JavaClassSource> builder : source.getMethods()) {
                if (builder.getParameters().size() == 1 && builder.getName().equals(fieldName)) {
                    String doc = builder.getJavaDoc().getFullText();
                    if (!Strings.isNullOrEmpty(doc)) {
                        return doc;
                    }
                }
            }
            for (MethodSource<JavaClassSource> builder : source.getMethods()) {
                if (builder.getParameters().size() == 0 && builder.getName().equals(fieldName)) {
                    String doc = builder.getJavaDoc().getFullText();
                    if (!Strings.isNullOrEmpty(doc)) {
                        return doc;
                    }
                }
            }
        }

        return "";
    }

    private String getDocComment(Class<?> classElement) {
        JavaClassSource source = javaClassSource(classElement.getName());
        return source.getJavaDoc().getFullText();
    }

    private JavaClassSource javaClassSource(String className) {
        return sources.computeIfAbsent(className, this::doParseJavaClassSource);
    }

    private JavaClassSource doParseJavaClassSource(String className) {
        try {
            Path srcDir = project.getBasedir().toPath().resolve("src/main/java");
            Path file = srcDir.resolve(className.replace('.', '/') + ".java");
            return (JavaClassSource) Roaster.parse(file.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse java class " + className, e);
        }
    }

    private static String getTypeName(Type fieldType) {
        String fieldTypeName = new GenericType(fieldType).toString();
        fieldTypeName = fieldTypeName.replace('$', '.');
        return fieldTypeName;
    }

    /**
     * Gets the JSON schema type.
     *
     * @param  type the java type
     * @return      the json schema type, is never null, but returns <tt>object</tt> as the generic type
     */
    public static String getType(String type, boolean enumType, boolean isDuration) {
        if (enumType) {
            return "enum";
        } else if (isDuration) {
            return "duration";
        } else if (type == null) {
            // return generic type for unknown type
            return "object";
        } else if (type.equals(URI.class.getName()) || type.equals(URL.class.getName())) {
            return "string";
        } else if (type.equals(File.class.getName())) {
            return "string";
        } else if (type.equals(Date.class.getName())) {
            return "string";
        } else if (type.startsWith("java.lang.Class")) {
            return "string";
        } else if (type.startsWith("java.util.List") || type.startsWith("java.util.Collection")) {
            return "array";
        }

        String primitive = getPrimitiveType(type);
        if (primitive != null) {
            return primitive;
        }

        return "object";
    }

    /**
     * Gets the JSON schema primitive type.
     *
     * @param  name the java type
     * @return      the json schema primitive type, or <tt>null</tt> if not a primitive
     */
    public static String getPrimitiveType(String name) {
        // special for byte[] or Object[] as its common to use
        if ("java.lang.byte[]".equals(name) || "byte[]".equals(name)) {
            return "string";
        } else if ("java.lang.Byte[]".equals(name) || "Byte[]".equals(name)) {
            return "array";
        } else if ("java.lang.Object[]".equals(name) || "Object[]".equals(name)) {
            return "array";
        } else if ("java.lang.String[]".equals(name) || "String[]".equals(name)) {
            return "array";
        } else if ("java.lang.Character".equals(name) || "Character".equals(name) || "char".equals(name)) {
            return "string";
        } else if ("java.lang.String".equals(name) || "String".equals(name)) {
            return "string";
        } else if ("java.lang.Boolean".equals(name) || "Boolean".equals(name) || "boolean".equals(name)) {
            return "boolean";
        } else if ("java.lang.Integer".equals(name) || "Integer".equals(name) || "int".equals(name)) {
            return "integer";
        } else if ("java.lang.Long".equals(name) || "Long".equals(name) || "long".equals(name)) {
            return "integer";
        } else if ("java.lang.Short".equals(name) || "Short".equals(name) || "short".equals(name)) {
            return "integer";
        } else if ("java.lang.Byte".equals(name) || "Byte".equals(name) || "byte".equals(name)) {
            return "integer";
        } else if ("java.lang.Float".equals(name) || "Float".equals(name) || "float".equals(name)) {
            return "number";
        } else if ("java.lang.Double".equals(name) || "Double".equals(name) || "double".equals(name)) {
            return "number";
        }

        return null;
    }

    private static final class EipOptionComparator implements Comparator<EipOptionModel> {

        private final EipModel model;

        private EipOptionComparator(EipModel model) {
            this.model = model;
        }

        @Override
        public int compare(EipOptionModel o1, EipOptionModel o2) {
            int weight = weight(o1);
            int weight2 = weight(o2);

            if (weight == weight2) {
                // keep the current order
                return 1;
            } else {
                // sort according to weight
                return weight2 - weight;
            }
        }

        private int weight(EipOptionModel o) {
            String name = o.getName();

            // these should be first
            if ("expression".equals(name)) {
                return 10;
            }

            // these should be last
            if ("description".equals(name)) {
                return -10;
            } else if ("id".equals(name)) {
                return -9;
            } else if ("pattern".equals(name) && "to".equals(model.getName())) {
                // and pattern only for the to model
                return -8;
            }
            return 0;
        }
    }

}
