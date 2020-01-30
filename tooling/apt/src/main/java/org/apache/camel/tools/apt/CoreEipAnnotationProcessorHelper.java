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
package org.apache.camel.tools.apt;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.EipModel.EipOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.JavadocHelper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;

/**
 * Process all camel-core's model classes (EIPs and DSL) and generate json
 * schema documentation and for some models java source code is generated which
 * allows for faster property placeholder resolution at runtime; without the
 * overhead of using reflections.
 */
public class CoreEipAnnotationProcessorHelper {

    // special when using expression/predicates in the model
    private static final String ONE_OF_TYPE_NAME = "org.apache.camel.model.ExpressionSubElementDefinition";
    private static final String[] ONE_OF_LANGUAGES = new String[] {"org.apache.camel.model.language.ExpressionDefinition",
                                                                   "org.apache.camel.model.language.NamespaceAwareExpression"};
    // special for inputs (these classes have sub classes, so we use this to
    // find all classes)
    private static final String[] ONE_OF_INPUTS = new String[] {"org.apache.camel.model.ProcessorDefinition", "org.apache.camel.model.VerbDefinition"};
    // special for outputs (these classes have sub classes, so we use this to
    // find all classes - and not in particular if they support outputs or not)
    private static final String[] ONE_OF_OUTPUTS = new String[] {"org.apache.camel.model.ProcessorDefinition", "org.apache.camel.model.NoOutputDefinition",
                                                                 "org.apache.camel.model.OutputDefinition", "org.apache.camel.model.OutputExpressionNode",
                                                                 "org.apache.camel.model.NoOutputExpressionNode", "org.apache.camel.model.SendDefinition",
                                                                 "org.apache.camel.model.InterceptDefinition", "org.apache.camel.model.WhenDefinition",
                                                                 "org.apache.camel.model.ToDynamicDefinition"};
    // special for verbs (these classes have sub classes, so we use this to find
    // all classes)
    private static final String[] ONE_OF_VERBS = new String[] {"org.apache.camel.model.rest.VerbDefinition"};

    private boolean skipUnwanted = true;

    protected void generatePropertyPlaceholderProviderSource(ProcessingEnvironment processingEnv, TypeElement parent, String fqnDef, Set<EipOptionModel> options) {

        String def = fqnDef.substring(fqnDef.lastIndexOf('.') + 1);
        String cn = def + "PropertyPlaceholderProvider";
        String fqn = "org.apache.camel.model.placeholder." + cn;
        try (Writer w = processingEnv.getFiler().createSourceFile(fqn, parent).openWriter()) {
            PropertyPlaceholderGenerator.generatePropertyPlaceholderProviderSource(def, fqnDef, cn, options, w);
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to generate source code file: " + fqn + ": " + e.getMessage());
            AnnotationProcessorHelper.dumpExceptionToErrorFile("camel-apt-error.log", "Unable to generate source code file: " + fqn, e);
        }
    }

    protected void processModelClass(final ProcessingEnvironment processingEnv, final RoundEnvironment roundEnv, final TypeElement classElement,
                                     Set<String> propertyPlaceholderDefinitions) {
        final String javaTypeName = Strings.canonicalClassName(classElement.getQualifiedName().toString());
        String packageName = javaTypeName.substring(0, javaTypeName.lastIndexOf("."));

        // skip abstract classes
        if (classElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return;
        }

        // skip unwanted classes which are "abstract" holders
        if (skipUnwanted) {
            if (classElement.getQualifiedName().toString().equals(ONE_OF_TYPE_NAME)) {
                return;
            }
        }

        final XmlRootElement rootElement = classElement.getAnnotation(XmlRootElement.class);
        if (rootElement == null) {
            return;
        }

        String aName = rootElement.name();
        if (Strings.isNullOrEmpty(aName) || "##default".equals(aName)) {
            XmlType typeElement = classElement.getAnnotation(XmlType.class);
            aName = typeElement.name();
        }
        final String name = aName;

        // lets use the xsd name as the file name
        String fileName;
        if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
            fileName = classElement.getSimpleName().toString() + PackageHelper.JSON_SUFIX;
        } else {
            fileName = name + PackageHelper.JSON_SUFIX;
        }

        // write json schema and property placeholder provider
        AnnotationProcessorHelper.processFile(processingEnv, packageName, fileName,
            writer -> writeJSonSchemeAndPropertyPlaceholderProvider(processingEnv, writer, roundEnv, classElement, rootElement, javaTypeName,
                                                                                                      name, propertyPlaceholderDefinitions));
    }

    protected void writeJSonSchemeAndPropertyPlaceholderProvider(ProcessingEnvironment processingEnv, PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement,
                                                                 XmlRootElement rootElement, String javaTypeName, String modelName, Set<String> propertyPlaceholderDefinitions) {
        // gather eip information
        EipModel eipModel = findEipModelProperties(processingEnv, roundEnv, classElement, javaTypeName, modelName);

        // get endpoint information which is divided into paths and options
        // (though there should really only be one path)
        Set<EipOptionModel> eipOptions = new TreeSet<>(new EipOptionComparator(eipModel));
        findClassProperties(processingEnv, writer, roundEnv, eipOptions, classElement, classElement, "", modelName);

        eipOptions.forEach(eipModel::addOption);

        // after we have found all the options then figure out if the model
        // accepts input/output
        eipModel.setInput(hasInput(processingEnv, roundEnv, classElement));
        eipModel.setOutput(hasOutput(eipModel));

        if (Strings.isNullOrEmpty(eipModel.getTitle())) {
            eipModel.setTitle(Strings.asTitle(eipModel.getName()));
        }
        if (eipModel.isOutput()) {
            // filter out outputs if we do not support it
            eipModel.getOptions().removeIf(o -> "outputs".equals(o.getName()));
        }

        // write json schema file
        String json = JsonMapper.createParameterJsonSchema(eipModel);
        writer.println(json);

        // generate property placeholder provider java source code
        generatePropertyPlaceholderProviderSource(processingEnv, writer, roundEnv, classElement, eipModel, eipOptions, propertyPlaceholderDefinitions);
    }

    protected void generatePropertyPlaceholderProviderSource(ProcessingEnvironment processingEnv, PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement,
                                                             EipModel eipModel, Set<EipOptionModel> options, Set<String> propertyPlaceholderDefinitions) {

        // not ever model classes support property placeholders as this has been
        // limited to mainly Camel routes
        // so filter out unwanted models
        boolean rest = classElement.getQualifiedName().toString().startsWith("org.apache.camel.model.rest");
        boolean processor = AnnotationProcessorHelper.hasSuperClass(processingEnv, roundEnv, classElement, "org.apache.camel.model.ProcessorDefinition");
        boolean language = AnnotationProcessorHelper.hasSuperClass(processingEnv, roundEnv, classElement, "org.apache.camel.model.language.ExpressionDefinition");
        boolean dataformat = AnnotationProcessorHelper.hasSuperClass(processingEnv, roundEnv, classElement, "org.apache.camel.model.DataFormatDefinition");

        if (!rest && !processor && !language && !dataformat) {
            return;
        }

        TypeElement parent = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, "org.apache.camel.spi.PropertyPlaceholderConfigurer");
        String fqnDef = classElement.getQualifiedName().toString();

        generatePropertyPlaceholderProviderSource(processingEnv, parent, fqnDef, options);
        propertyPlaceholderDefinitions.add(fqnDef);

        // we also need to generate from when we generate route as from can also
        // configure property placeholders
        if (fqnDef.equals("org.apache.camel.model.RouteDefinition")) {
            fqnDef = "org.apache.camel.model.FromDefinition";

            options.clear();
            options.add(createOption("id", null, null, "java.lang.String", false, null, null, false, null, false, null, false, null, false));
            options.add(createOption("uri", null, null, "java.lang.String", false, null, null, false, null, false, null, false, null, false));

            generatePropertyPlaceholderProviderSource(processingEnv, parent, fqnDef, options);
            propertyPlaceholderDefinitions.add(fqnDef);
        }
    }

    protected EipModel findEipModelProperties(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement classElement, String javaTypeName, String name) {
        EipModel model = new EipModel();
        model.setJavaType(javaTypeName);
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
        if (model.getJavaType() != null) {
            Elements elementUtils = processingEnv.getElementUtils();
            TypeElement typeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, model.getJavaType());
            if (typeElement != null) {
                String doc = elementUtils.getDocComment(typeElement);
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
            }
        }

        return model;
    }

    protected void findClassProperties(ProcessingEnvironment processingEnv, PrintWriter writer, RoundEnvironment roundEnv, Set<EipOptionModel> eipOptions,
                                       TypeElement originalClassType, TypeElement classElement, String prefix, String modelName) {
        while (true) {
            List<VariableElement> fieldElements = ElementFilter.fieldsIn(classElement.getEnclosedElements());
            for (VariableElement fieldElement : fieldElements) {

                String fieldName = fieldElement.getSimpleName().toString();

                XmlAttribute attribute = fieldElement.getAnnotation(XmlAttribute.class);
                if (attribute != null) {
                    boolean skip = processAttribute(processingEnv, roundEnv, originalClassType, classElement, fieldElement, fieldName, attribute, eipOptions, prefix, modelName);
                    if (skip) {
                        continue;
                    }
                }

                XmlValue value = fieldElement.getAnnotation(XmlValue.class);
                if (value != null) {
                    processValue(processingEnv, roundEnv, originalClassType, classElement, fieldElement, fieldName, value, eipOptions, prefix, modelName);
                }

                XmlElements elements = fieldElement.getAnnotation(XmlElements.class);
                if (elements != null) {
                    processElements(processingEnv, roundEnv, classElement, elements, fieldElement, eipOptions, prefix);
                }

                XmlElement element = fieldElement.getAnnotation(XmlElement.class);
                if (element != null) {
                    processElement(processingEnv, roundEnv, classElement, element, fieldElement, eipOptions, prefix);
                }

                // special for eips which has outputs or requires an expressions
                XmlElementRef elementRef = fieldElement.getAnnotation(XmlElementRef.class);
                if (elementRef != null) {

                    // special for routes
                    processRoutes(roundEnv, originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for outputs
                    processOutputs(processingEnv, roundEnv, originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for when clauses (choice eip)
                    processRefWhenClauses(processingEnv, roundEnv, originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for rests (rest-dsl)
                    processRests(roundEnv, originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for verbs (rest-dsl)
                    processVerbs(processingEnv, roundEnv, originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for expression
                    processRefExpression(processingEnv, roundEnv, originalClassType, classElement, elementRef, fieldElement, fieldName, eipOptions, prefix);

                }
            }

            // special when we process these nodes as they do not use JAXB
            // annotations on fields, but on methods
            if ("OptionalIdentifiedDefinition".equals(classElement.getSimpleName().toString())) {
                processIdentified(processingEnv, roundEnv, originalClassType, classElement, eipOptions, prefix);
            } else if ("RouteDefinition".equals(classElement.getSimpleName().toString())) {
                processRoute(processingEnv, roundEnv, originalClassType, classElement, eipOptions, prefix);
            }

            // check super classes which may also have fields
            TypeElement baseTypeElement = null;
            TypeMirror superclass = classElement.getSuperclass();
            if (superclass != null) {
                String superClassName = Strings.canonicalClassName(superclass.toString());
                baseTypeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, superClassName);
            }
            if (baseTypeElement != null) {
                classElement = baseTypeElement;
            } else {
                break;
            }
        }
    }

    private boolean processAttribute(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement originalClassType, TypeElement classElement,
                                     VariableElement fieldElement, String fieldName, XmlAttribute attribute, Set<EipOptionModel> eipOptions, String prefix, String modelName) {
        Elements elementUtils = processingEnv.getElementUtils();

        String name = attribute.name();
        if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
            name = fieldName;
        }

        // lets skip some unwanted attributes
        if (skipUnwanted) {
            // we want to skip inheritErrorHandler which is only applicable for
            // the load-balancer
            boolean loadBalancer = "LoadBalanceDefinition".equals(originalClassType.getSimpleName().toString());
            if (!loadBalancer && "inheritErrorHandler".equals(name)) {
                return true;
            }
        }

        Metadata metadata = fieldElement.getAnnotation(Metadata.class);

        name = prefix + name;
        TypeMirror fieldType = fieldElement.asType();
        String fieldTypeName = fieldType.toString();
        TypeElement fieldTypeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeName);
        if (metadata != null && !Strings.isNullOrEmpty(metadata.javaType())) {
            fieldTypeName = metadata.javaType();
        }

        String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
        String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, true);
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
                enums.add(val);
            }
        } else {
            isEnum = fieldTypeElement != null && fieldTypeElement.getKind() == ElementKind.ENUM;
            if (isEnum) {
                TypeElement enumClass = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeElement.asType().toString());
                if (enumClass != null) {
                    // find all the enum constants which has the possible enum
                    // value that can be used
                    List<VariableElement> fields = ElementFilter.fieldsIn(enumClass.getEnclosedElements());
                    for (VariableElement var : fields) {
                        if (var.getKind() == ElementKind.ENUM_CONSTANT) {
                            String val = var.toString();
                            enums.add(val);
                        }
                    }
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

        EipOptionModel ep = createOption(name, displayName, "attribute", fieldTypeName, required, defaultValue, docComment, deprecated, deprecationNote, isEnum, enums, false, null,
                                         false);
        eipOptions.add(ep);

        return false;
    }

    private void processValue(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement originalClassType, TypeElement classElement, VariableElement fieldElement,
                              String fieldName, XmlValue value, Set<EipOptionModel> eipOptions, String prefix, String modelName) {
        Elements elementUtils = processingEnv.getElementUtils();

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
        TypeMirror fieldType = fieldElement.asType();
        String fieldTypeName = fieldType.toString();

        String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
        String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, true);
        boolean required = true;
        // metadata may overrule element required
        required = findRequired(fieldElement, required);

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

        EipOptionModel ep = createOption(name, displayName, "value", fieldTypeName, required, defaultValue, docComment, deprecated, deprecationNote, false, null, false, null,
                                         false);
        eipOptions.add(ep);
    }

    private void processElement(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement classElement, XmlElement element, VariableElement fieldElement,
                                Set<EipOptionModel> eipOptions, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();

        String fieldName;
        fieldName = fieldElement.getSimpleName().toString();
        if (element != null) {

            Metadata metadata = fieldElement.getAnnotation(Metadata.class);

            String kind = "element";
            String name = element.name();
            if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();
            TypeElement fieldTypeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeName);

            String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
            String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, true);
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
                    enums.add(val);
                }
            } else {
                isEnum = fieldTypeElement != null && fieldTypeElement.getKind() == ElementKind.ENUM;
                if (isEnum) {
                    TypeElement enumClass = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeElement.asType().toString());
                    if (enumClass != null) {
                        // find all the enum constants which has the possible
                        // enum value that can be used
                        List<VariableElement> fields = ElementFilter.fieldsIn(enumClass.getEnclosedElements());
                        for (VariableElement var : fields) {
                            if (var.getKind() == ElementKind.ENUM_CONSTANT) {
                                String val = var.toString();
                                enums.add(val);
                            }
                        }
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
                for (String language : ONE_OF_LANGUAGES) {
                    fieldTypeName = language;
                    TypeElement languages = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, language);
                    if (languages != null) {
                        String superClassName = Strings.canonicalClassName(languages.toString());
                        // find all classes that has that superClassName
                        Set<TypeElement> children = new LinkedHashSet<>();
                        AnnotationProcessorHelper.findTypeElementChildren(processingEnv, roundEnv, children, superClassName);
                        for (TypeElement child : children) {
                            XmlRootElement rootElement = child.getAnnotation(XmlRootElement.class);
                            if (rootElement != null) {
                                String childName = rootElement.name();
                                oneOfTypes.add(childName);
                            }
                        }
                    }
                }
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

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, required, defaultValue, docComment, deprecated, deprecationNote, isEnum, enums, isOneOf,
                                             oneOfTypes, asPredicate);
            eipOptions.add(ep);
        }
    }

    private void processElements(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement classElement, XmlElements elements, VariableElement fieldElement,
                                 Set<EipOptionModel> eipOptions, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();

        String fieldName;
        fieldName = fieldElement.getSimpleName().toString();
        if (elements != null) {
            String kind = "element";
            String name = fieldName;
            name = prefix + name;

            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
            String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, true);

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

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, required, defaultValue, docComment, deprecated, deprecationNote, false, null, true, oneOfTypes,
                                             false);
            eipOptions.add(ep);
        }
    }

    private void processRoute(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement originalClassType, TypeElement classElement,
                              Set<EipOptionModel> eipOptions, String prefix) {

        Elements elementUtils = processingEnv.getElementUtils();

        // group
        String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "group", null, classElement, true);
        EipOptionModel ep = createOption("group", "Group", "attribute", "java.lang.String", false, "", docComment, false, null, false, null, false, null, false);
        eipOptions.add(ep);

        // group
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "streamCache", null, classElement, true);
        ep = createOption("streamCache", "Stream Cache", "attribute", "java.lang.String", false, "", docComment, false, null, false, null, false, null, false);
        eipOptions.add(ep);

        // trace
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "trace", null, classElement, true);
        ep = createOption("trace", "Trace", "attribute", "java.lang.String", false, "", docComment, false, null, false, null, false, null, false);
        eipOptions.add(ep);

        // message history
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "messageHistory", null, classElement, true);
        ep = createOption("messageHistory", "Message History", "attribute", "java.lang.String", false, "true", docComment, false, null, false, null, false, null, false);
        eipOptions.add(ep);

        // log mask
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "logMask", null, classElement, true);
        ep = createOption("logMask", "Log Mask", "attribute", "java.lang.String", false, "false", docComment, false, null, false, null, false, null, false);
        eipOptions.add(ep);

        // delayer
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "delayer", null, classElement, true);
        ep = createOption("delayer", "Delayer", "attribute", "java.lang.String", false, "", docComment, false, null, false, null, false, null, false);
        eipOptions.add(ep);

        // autoStartup
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "autoStartup", null, classElement, true);
        ep = createOption("autoStartup", "Auto Startup", "attribute", "java.lang.String", false, "true", docComment, false, null, false, null, false, null, false);
        eipOptions.add(ep);

        // startupOrder
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "startupOrder", null, classElement, true);
        ep = createOption("startupOrder", "Startup Order", "attribute", "java.lang.Integer", false, "", docComment, false, null, false, null, false, null, false);
        eipOptions.add(ep);

        // errorHandlerRef
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "errorHandlerRef", null, classElement, true);
        ep = createOption("errorHandlerRef", "Error Handler", "attribute", "java.lang.String", false, "", docComment, false, null, false, null, false, null, false);
        eipOptions.add(ep);

        // routePolicyRef
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "routePolicyRef", null, classElement, true);
        ep = createOption("routePolicyRef", "Route Policy", "attribute", "java.lang.String", false, "", docComment, false, null, false, null, false, null, false);
        eipOptions.add(ep);

        // shutdownRoute
        Set<String> enums = new LinkedHashSet<>();
        enums.add("Default");
        enums.add("Defer");
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "shutdownRoute", "Default", classElement, true);
        ep = createOption("shutdownRoute", "Shutdown Route", "attribute", "org.apache.camel.ShutdownRoute", false, "", docComment, false, null, true, enums, false, null, false);
        eipOptions.add(ep);

        // shutdownRunningTask
        enums = new LinkedHashSet<>();
        enums.add("CompleteCurrentTaskOnly");
        enums.add("CompleteAllTasks");
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "shutdownRunningTask", "CompleteCurrentTaskOnly", classElement, true);
        ep = createOption("shutdownRunningTask", "Shutdown Running Task", "attribute", "org.apache.camel.ShutdownRunningTask", false, "", docComment, false, null, true, enums,
                          false, null, false);
        eipOptions.add(ep);

        // input
        Set<String> oneOfTypes = new TreeSet<>();
        oneOfTypes.add("from");
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "input", null, classElement, true);
        ep = createOption("input", "Input", "element", "org.apache.camel.model.FromDefinition", true, "", docComment, false, null, false, null, true, oneOfTypes, false);
        eipOptions.add(ep);

        // outputs
        // gather oneOf which extends any of the output base classes
        oneOfTypes = new TreeSet<>();
        // find all classes that has that superClassName
        Set<TypeElement> children = new LinkedHashSet<>();
        for (String superclass : ONE_OF_OUTPUTS) {
            AnnotationProcessorHelper.findTypeElementChildren(processingEnv, roundEnv, children, superclass);
        }
        for (TypeElement child : children) {
            XmlRootElement rootElement = child.getAnnotation(XmlRootElement.class);
            if (rootElement != null) {
                String childName = rootElement.name();
                oneOfTypes.add(childName);
            }
        }

        // remove some types which are not intended as an output in eips
        oneOfTypes.remove("route");

        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "outputs", null, classElement, true);
        ep = createOption("outputs", "Outputs", "element", "java.util.List<org.apache.camel.model.ProcessorDefinition<?>>", true, "", docComment, false, null, false, null, true,
                          oneOfTypes, false);
        eipOptions.add(ep);
    }

    /**
     * Special for process the OptionalIdentifiedDefinition
     */
    private void processIdentified(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement originalClassType, TypeElement classElement,
                                   Set<EipOptionModel> eipOptions, String prefix) {

        Elements elementUtils = processingEnv.getElementUtils();

        // id
        String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "id", null, classElement, true);
        EipOptionModel ep = createOption("id", "Id", "attribute", "java.lang.String", false, "", docComment, false, null, false, null, false, null, false);
        eipOptions.add(ep);

        // description
        docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "description", null, classElement, true);
        ep = createOption("description", "Description", "element", "org.apache.camel.model.DescriptionDefinition", false, "", docComment, false, null, false, null, false, null,
                          false);
        eipOptions.add(ep);

        // lets skip custom id as it has no value for end users to configure
        if (!skipUnwanted) {
            // custom id
            docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, null, "customId", null, classElement, true);
            ep = createOption("customId", "Custom Id", "attribute", "java.lang.String", false, "", docComment, false, null, false, null, false, null, false);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef routes field
     */
    private void processRoutes(RoundEnvironment roundEnv, TypeElement originalClassType, XmlElementRef elementRef, VariableElement fieldElement, String fieldName,
                               Set<EipOptionModel> eipOptions, String prefix) {
        if ("routes".equals(fieldName)) {

            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            Set<String> oneOfTypes = new TreeSet<>();
            oneOfTypes.add("route");

            EipOptionModel ep = createOption("routes", "Routes", "element", fieldTypeName, false, "", "Contains the Camel routes", false, null, false, null, true, oneOfTypes,
                                             false);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef rests field
     */
    private void processRests(RoundEnvironment roundEnv, TypeElement originalClassType, XmlElementRef elementRef, VariableElement fieldElement, String fieldName,
                              Set<EipOptionModel> eipOptions, String prefix) {
        if ("rests".equals(fieldName)) {

            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            Set<String> oneOfTypes = new TreeSet<>();
            oneOfTypes.add("rest");

            EipOptionModel ep = createOption("rests", "Rests", "element", fieldTypeName, false, "", "Contains the rest services defined using the rest-dsl", false, null, false,
                                             null, true, oneOfTypes, false);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef outputs field
     */
    private void processOutputs(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement originalClassType, XmlElementRef elementRef,
                                VariableElement fieldElement, String fieldName, Set<EipOptionModel> eipOptions, String prefix) {
        if ("outputs".equals(fieldName) && supportOutputs(processingEnv, roundEnv, originalClassType)) {
            String kind = "element";
            String name = elementRef.name();
            if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            // gather oneOf which extends any of the output base classes
            Set<String> oneOfTypes = new TreeSet<>();
            // find all classes that has that superClassName
            Set<TypeElement> children = new LinkedHashSet<>();
            for (String superclass : ONE_OF_OUTPUTS) {
                AnnotationProcessorHelper.findTypeElementChildren(processingEnv, roundEnv, children, superclass);
            }
            for (TypeElement child : children) {
                XmlRootElement rootElement = child.getAnnotation(XmlRootElement.class);
                if (rootElement != null) {
                    String childName = rootElement.name();
                    oneOfTypes.add(childName);
                }
            }

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

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, true, "", "", deprecated, deprecationNote, false, null, true, oneOfTypes, false);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef verbs field (rest-dsl)
     */
    private void processVerbs(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement originalClassType, XmlElementRef elementRef, VariableElement fieldElement,
                              String fieldName, Set<EipOptionModel> eipOptions, String prefix) {

        Elements elementUtils = processingEnv.getElementUtils();

        if ("verbs".equals(fieldName) && supportOutputs(processingEnv, roundEnv, originalClassType)) {
            String kind = "element";
            String name = elementRef.name();
            if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, fieldElement, fieldName, name, originalClassType, true);

            // gather oneOf which extends any of the output base classes
            Set<String> oneOfTypes = new TreeSet<>();
            // find all classes that has that superClassName
            Set<TypeElement> children = new LinkedHashSet<>();
            for (String superclass : ONE_OF_VERBS) {
                AnnotationProcessorHelper.findTypeElementChildren(processingEnv, roundEnv, children, superclass);
            }
            for (TypeElement child : children) {
                XmlRootElement rootElement = child.getAnnotation(XmlRootElement.class);
                if (rootElement != null) {
                    String childName = rootElement.name();
                    if (childName != null) {
                        oneOfTypes.add(childName);
                    }
                }
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

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, true, "", docComment, deprecated, deprecationNote, false, null, true, oneOfTypes, false);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef expression field
     */
    private void processRefExpression(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement originalClassType, TypeElement classElement,
                                      XmlElementRef elementRef, VariableElement fieldElement, String fieldName, Set<EipOptionModel> eipOptions, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();

        if ("expression".equals(fieldName)) {
            String kind = "expression";
            String name = elementRef.name();
            if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            // find javadoc from original class as it will override the
            // setExpression method where we can provide the javadoc for the
            // given EIP
            String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, fieldElement, fieldName, name, originalClassType, true);

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
            Set<String> oneOfTypes = new TreeSet<>();
            for (String language : ONE_OF_LANGUAGES) {
                TypeElement languages = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, language);
                String superClassName = Strings.canonicalClassName(languages.toString());
                // find all classes that has that superClassName
                Set<TypeElement> children = new LinkedHashSet<>();
                AnnotationProcessorHelper.findTypeElementChildren(processingEnv, roundEnv, children, superClassName);
                for (TypeElement child : children) {
                    XmlRootElement rootElement = child.getAnnotation(XmlRootElement.class);
                    if (rootElement != null) {
                        String childName = rootElement.name();
                        oneOfTypes.add(childName);
                    }
                }
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

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, true, "", docComment, deprecated, deprecationNote, false, null, true, oneOfTypes, asPredicate);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef when field
     */
    private void processRefWhenClauses(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement originalClassType, XmlElementRef elementRef,
                                       VariableElement fieldElement, String fieldName, Set<EipOptionModel> eipOptions, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();

        if ("whenClauses".equals(fieldName)) {
            String kind = "element";
            String name = elementRef.name();
            if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            // find javadoc from original class as it will override the
            // setExpression method where we can provide the javadoc for the
            // given EIP
            String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, fieldElement, fieldName, name, originalClassType, true);

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

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, false, "", docComment, deprecated, deprecationNote, false, null, true, oneOfTypes,
                                             asPredicate);
            eipOptions.add(ep);
        }
    }

    /**
     * Whether the class supports outputs.
     * <p/>
     * There are some classes which does not support outputs, even though they
     * have a outputs element.
     */
    private boolean supportOutputs(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement classElement) {
        return AnnotationProcessorHelper.implementsInterface(processingEnv, roundEnv, classElement, "org.apache.camel.model.OutputNode");
    }

    private String findDefaultValue(VariableElement fieldElement, String fieldTypeName) {
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

    private boolean findRequired(VariableElement fieldElement, boolean defaultValue) {
        Metadata metadata = fieldElement.getAnnotation(Metadata.class);
        if (metadata != null) {
            return metadata.required();
        }
        return defaultValue;
    }

    private boolean hasInput(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement classElement) {
        for (String name : ONE_OF_INPUTS) {
            if (AnnotationProcessorHelper.hasSuperClass(processingEnv, roundEnv, classElement, name)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOutput(EipModel model) {
        switch (model.getName()) {
        // if we are route/rest then we accept output
        case "route":
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

    private EipOptionModel createOption(String name, String displayName, String kind, String type, boolean required, String defaultValue, String description, boolean deprecated,
                                        String deprecationNote, boolean enumType, Set<String> enums, boolean oneOf, Set<String> oneOfs, boolean asPredicate) {
        EipOptionModel option = new EipOptionModel();
        option.setName(name);
        option.setDisplayName(Strings.isNullOrEmpty(displayName) ? Strings.asTitle(name) : displayName);
        option.setKind(kind);
        option.setRequired(required);
        option.setDefaultValue("java.lang.Boolean".equals(type) && !Strings.isNullOrEmpty(defaultValue) ? Boolean.parseBoolean(defaultValue) : defaultValue);
        option.setDescription(JavadocHelper.sanitizeDescription(description, false));
        option.setDeprecated(deprecated);
        option.setDeprecationNote(Strings.isNullOrEmpty(deprecationNote) ? null : deprecationNote);
        option.setType(AnnotationProcessorHelper.getType(type, enumType));
        option.setJavaType(type);
        option.setEnums(enums != null && !enums.isEmpty() ? new ArrayList<>(enums) : null);
        option.setOneOfs(oneOfs != null && !oneOfs.isEmpty() ? new ArrayList<>(oneOfs) : null);
        option.setAsPredicate(asPredicate);
        return option;
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
