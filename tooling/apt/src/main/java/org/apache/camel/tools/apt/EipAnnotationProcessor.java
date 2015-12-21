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
package org.apache.camel.tools.apt;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

import org.apache.camel.spi.Metadata;
import org.apache.camel.tools.apt.helper.JsonSchemaHelper;
import org.apache.camel.tools.apt.helper.Strings;

import static org.apache.camel.tools.apt.helper.JsonSchemaHelper.sanitizeDescription;
import static org.apache.camel.tools.apt.helper.Strings.canonicalClassName;
import static org.apache.camel.tools.apt.helper.Strings.isNullOrEmpty;
import static org.apache.camel.tools.apt.helper.Strings.safeNull;

/**
 * Process all camel-core's model classes (EIPs and DSL) and generate json schema documentation
 */
@SupportedAnnotationTypes({"javax.xml.bind.annotation.*", "org.apache.camel.spi.Label"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class EipAnnotationProcessor extends AbstractAnnotationProcessor {

    // special when using expression/predicates in the model
    private static final String ONE_OF_TYPE_NAME = "org.apache.camel.model.ExpressionSubElementDefinition";
    private static final String[] ONE_OF_LANGUAGES = new String[]{
        "org.apache.camel.model.language.ExpressionDefinition",
        "org.apache.camel.model.language.NamespaceAwareExpression"
    };
    // special for inputs (these classes have sub classes, so we use this to find all classes)
    private static final String[] ONE_OF_INPUTS = new String[]{
        "org.apache.camel.model.ProcessorDefinition",
        "org.apache.camel.model.VerbDefinition"
    };
    // special for outputs (these classes have sub classes, so we use this to find all classes)
    private static final String[] ONE_OF_OUTPUTS = new String[]{
        "org.apache.camel.model.ProcessorDefinition",
        "org.apache.camel.model.NoOutputDefinition",
        "org.apache.camel.model.OutputDefinition",
        "org.apache.camel.model.ExpressionNode",
        "org.apache.camel.model.NoOutputExpressionNode",
        "org.apache.camel.model.SendDefinition",
        "org.apache.camel.model.InterceptDefinition",
        "org.apache.camel.model.WhenDefinition",
    };
    // special for verbs (these classes have sub classes, so we use this to find all classes)
    private static final String[] ONE_OF_VERBS = new String[]{
        "org.apache.camel.model.rest.VerbDefinition"
    };

    private boolean skipUnwanted = true;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            if (roundEnv.processingOver()) {
                return true;
            }

            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(XmlRootElement.class);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    processModelClass(roundEnv, (TypeElement) element);
                }
            }
        } catch (Throwable e) {
            dumpExceptionToErrorFile("camel-apt-error.log", "Error processing EIP model", e);
        }
        return true;
    }

    protected void processModelClass(final RoundEnvironment roundEnv, final TypeElement classElement) {
        // must be from org.apache.camel.model
        final String javaTypeName = canonicalClassName(classElement.getQualifiedName().toString());
        String packageName = javaTypeName.substring(0, javaTypeName.lastIndexOf("."));
        if (!javaTypeName.startsWith("org.apache.camel.model")) {
            return;
        }

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
        if (isNullOrEmpty(aName) || "##default".equals(aName)) {
            XmlType typeElement = classElement.getAnnotation(XmlType.class);
            aName = typeElement.name();
        }
        final String name = aName;

        // lets use the xsd name as the file name
        String fileName;
        if (isNullOrEmpty(name) || "##default".equals(name)) {
            fileName = classElement.getSimpleName().toString() + ".json";
        } else {
            fileName = name + ".json";
        }

        // write json schema
        Func1<PrintWriter, Void> handler = new Func1<PrintWriter, Void>() {
            @Override
            public Void call(PrintWriter writer) {
                writeJSonSchemeDocumentation(writer, roundEnv, classElement, rootElement, javaTypeName, name);
                return null;
            }
        };
        processFile(packageName, fileName, handler);
    }

    protected void writeJSonSchemeDocumentation(PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement, XmlRootElement rootElement,
                                                String javaTypeName, String name) {
        // gather eip information
        EipModel eipModel = findEipModelProperties(roundEnv, classElement, javaTypeName, name);

        // get endpoint information which is divided into paths and options (though there should really only be one path)
        Set<EipOption> eipOptions = new TreeSet<EipOption>(new EipOptionComparator(eipModel));
        findClassProperties(writer, roundEnv, eipOptions, classElement, classElement, "");

        // after we have found all the options then figure out if the model accepts input/output
        eipModel.setInput(hasInput(roundEnv, classElement));
        eipModel.setOutput(hasOutput(eipModel, eipOptions));

        String json = createParameterJsonSchema(eipModel, eipOptions);
        writer.println(json);
    }

    public String createParameterJsonSchema(EipModel eipModel, Set<EipOption> options) {
        StringBuilder buffer = new StringBuilder("{");
        // eip model
        buffer.append("\n \"model\": {");
        buffer.append("\n    \"kind\": \"").append("model").append("\",");
        buffer.append("\n    \"name\": \"").append(eipModel.getName()).append("\",");
        if (eipModel.getTitle() != null) {
            buffer.append("\n    \"title\": \"").append(eipModel.getTitle()).append("\",");
        } else {
            // fallback and use name as title
            buffer.append("\n    \"title\": \"").append(asTitle(eipModel.getName())).append("\",");
        }
        buffer.append("\n    \"description\": \"").append(safeNull(eipModel.getDescription())).append("\",");
        buffer.append("\n    \"javaType\": \"").append(eipModel.getJavaType()).append("\",");
        buffer.append("\n    \"label\": \"").append(safeNull(eipModel.getLabel())).append("\",");
        buffer.append("\n    \"input\": \"").append(eipModel.getInput()).append("\",");
        buffer.append("\n    \"output\": \"").append(eipModel.getOutput()).append("\"");
        buffer.append("\n  },");

        buffer.append("\n  \"properties\": {");
        boolean first = true;
        for (EipOption entry : options) {
            if (first) {
                first = false;
            } else {
                buffer.append(",");
            }
            buffer.append("\n    ");
            // as its json we need to sanitize the docs
            String doc = entry.getDocumentation();
            doc = sanitizeDescription(doc, false);
            buffer.append(JsonSchemaHelper.toJson(entry.getName(), entry.getKind(), entry.isRequired(), entry.getType(), entry.getDefaultValue(), doc,
                    entry.isDeprecated(), null, null, entry.isEnumType(), entry.getEnums(), entry.isOneOf(), entry.getOneOfTypes()));
        }
        buffer.append("\n  }");

        buffer.append("\n}\n");
        return buffer.toString();
    }

    protected EipModel findEipModelProperties(RoundEnvironment roundEnv, TypeElement classElement, String javaTypeName, String name) {
        EipModel model = new EipModel();
        model.setJavaType(javaTypeName);
        model.setName(name);

        Metadata metadata = classElement.getAnnotation(Metadata.class);
        if (metadata != null) {
            if (!Strings.isNullOrEmpty(metadata.label())) {
                model.setLabel(metadata.label());
            }
            if (!Strings.isNullOrEmpty(metadata.title())) {
                model.setTitle(metadata.title());
            }
        }

        // favor to use class javadoc of component as description
        if (model.getJavaType() != null) {
            Elements elementUtils = processingEnv.getElementUtils();
            TypeElement typeElement = findTypeElement(roundEnv, model.getJavaType());
            if (typeElement != null) {
                String doc = elementUtils.getDocComment(typeElement);
                if (doc != null) {
                    // need to sanitize the description first (we only want a summary)
                    doc = sanitizeDescription(doc, true);
                    // the javadoc may actually be empty, so only change the doc if we got something
                    if (!Strings.isNullOrEmpty(doc)) {
                        model.setDescription(doc);
                    }
                }
            }
        }

        return model;
    }

    protected void findClassProperties(PrintWriter writer, RoundEnvironment roundEnv, Set<EipOption> eipOptions,
                                       TypeElement originalClassType, TypeElement classElement, String prefix) {
        while (true) {
            List<VariableElement> fieldElements = ElementFilter.fieldsIn(classElement.getEnclosedElements());
            for (VariableElement fieldElement : fieldElements) {

                String fieldName = fieldElement.getSimpleName().toString();

                XmlAttribute attribute = fieldElement.getAnnotation(XmlAttribute.class);
                if (attribute != null) {
                    boolean skip = processAttribute(roundEnv, originalClassType, classElement, fieldElement, fieldName, attribute, eipOptions, prefix);
                    if (skip) {
                        continue;
                    }
                }

                XmlValue value = fieldElement.getAnnotation(XmlValue.class);
                if (value != null) {
                    processValue(roundEnv, originalClassType, classElement, fieldElement, fieldName, value, eipOptions, prefix);
                }

                XmlElements elements = fieldElement.getAnnotation(XmlElements.class);
                if (elements != null) {
                    processElements(roundEnv, classElement, elements, fieldElement, eipOptions, prefix);
                }

                XmlElement element = fieldElement.getAnnotation(XmlElement.class);
                if (element != null) {
                    processElement(roundEnv, classElement, element, fieldElement, eipOptions, prefix);
                }

                // special for eips which has outputs or requires an expressions
                XmlElementRef elementRef = fieldElement.getAnnotation(XmlElementRef.class);
                if (elementRef != null) {

                    // special for routes
                    processRoutes(roundEnv, originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for outputs
                    processOutputs(roundEnv, originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for when clauses (choice eip)
                    processRefWhenClauses(roundEnv, originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for rests (rest-dsl)
                    processRests(roundEnv, originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for verbs (rest-dsl)
                    processVerbs(roundEnv, originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for expression
                    processRefExpression(roundEnv, originalClassType, classElement, elementRef, fieldElement, fieldName, eipOptions, prefix);

                }
            }

            // special when we process these nodes as they do not use JAXB annotations on fields, but on methods
            if ("OptionalIdentifiedDefinition".equals(classElement.getSimpleName().toString())) {
                processIdentified(roundEnv, originalClassType, classElement, eipOptions, prefix);
            } else if ("RouteDefinition".equals(classElement.getSimpleName().toString())) {
                processRoute(roundEnv, originalClassType, classElement, eipOptions, prefix);
            }

            // check super classes which may also have fields
            TypeElement baseTypeElement = null;
            TypeMirror superclass = classElement.getSuperclass();
            if (superclass != null) {
                String superClassName = canonicalClassName(superclass.toString());
                baseTypeElement = findTypeElement(roundEnv, superClassName);
            }
            if (baseTypeElement != null) {
                classElement = baseTypeElement;
            } else {
                break;
            }
        }
    }

    private boolean processAttribute(RoundEnvironment roundEnv, TypeElement originalClassType, TypeElement classElement, VariableElement fieldElement, String fieldName, XmlAttribute attribute,
        Set<EipOption> eipOptions, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();

        String name = attribute.name();
        if (isNullOrEmpty(name) || "##default".equals(name)) {
            name = fieldName;
        }

        // lets skip some unwanted attributes
        if (skipUnwanted) {
            // we want to skip inheritErrorHandler which is only applicable for the load-balancer
            boolean loadBalancer = "LoadBalanceDefinition".equals(originalClassType.getSimpleName().toString());
            if (!loadBalancer && "inheritErrorHandler".equals(name)) {
                return true;
            }
        }

        name = prefix + name;
        TypeMirror fieldType = fieldElement.asType();
        String fieldTypeName = fieldType.toString();
        TypeElement fieldTypeElement = findTypeElement(roundEnv, fieldTypeName);

        String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
        String docComment = findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, true);
        boolean required = attribute.required();
        // metadata may overrule element required
        required = findRequired(fieldElement, required);

        // gather enums
        Set<String> enums = new TreeSet<String>();
        boolean isEnum = fieldTypeElement != null && fieldTypeElement.getKind() == ElementKind.ENUM;
        if (isEnum) {
            TypeElement enumClass = findTypeElement(roundEnv, fieldTypeElement.asType().toString());
            // find all the enum constants which has the possible enum value that can be used
            List<VariableElement> fields = ElementFilter.fieldsIn(enumClass.getEnclosedElements());
            for (VariableElement var : fields) {
                if (var.getKind() == ElementKind.ENUM_CONSTANT) {
                    String val = var.toString();
                    enums.add(val);
                }
            }
        }

        boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;

        EipOption ep = new EipOption(name, "attribute", fieldTypeName, required, defaultValue, docComment, deprecated, isEnum, enums, false, null);
        eipOptions.add(ep);

        return false;
    }

    private void processValue(RoundEnvironment roundEnv, TypeElement originalClassType, TypeElement classElement, VariableElement fieldElement, String fieldName, XmlValue value,
        Set<EipOption> eipOptions, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();

        // XmlValue has no name attribute
        String name = fieldName;

        name = prefix + name;
        TypeMirror fieldType = fieldElement.asType();
        String fieldTypeName = fieldType.toString();

        String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
        String docComment = findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, true);
        boolean required = true;
        // metadata may overrule element required
        required = findRequired(fieldElement, required);

        boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;

        EipOption ep = new EipOption(name, "value", fieldTypeName, required, defaultValue, docComment, deprecated, false, null, false, null);
        eipOptions.add(ep);
    }

    private void processElement(RoundEnvironment roundEnv, TypeElement classElement, XmlElement element, VariableElement fieldElement,
                                Set<EipOption> eipOptions, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();

        String fieldName;
        fieldName = fieldElement.getSimpleName().toString();
        if (element != null) {

            String kind = "element";
            String name = element.name();
            if (isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();
            TypeElement fieldTypeElement = findTypeElement(roundEnv, fieldTypeName);

            String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
            String docComment = findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, true);
            boolean required = element.required();
            // metadata may overrule element required
            required = findRequired(fieldElement, required);

            // gather enums
            Set<String> enums = new LinkedHashSet<String>();
            boolean isEnum = fieldTypeElement != null && fieldTypeElement.getKind() == ElementKind.ENUM;
            if (isEnum) {
                TypeElement enumClass = findTypeElement(roundEnv, fieldTypeElement.asType().toString());
                // find all the enum constants which has the possible enum value that can be used
                List<VariableElement> fields = ElementFilter.fieldsIn(enumClass.getEnclosedElements());
                for (VariableElement var : fields) {
                    if (var.getKind() == ElementKind.ENUM_CONSTANT) {
                        String val = var.toString();
                        enums.add(val);
                    }
                }
            }

            // gather oneOf expression/predicates which uses language
            Set<String> oneOfTypes = new TreeSet<String>();
            boolean isOneOf = ONE_OF_TYPE_NAME.equals(fieldTypeName);
            if (isOneOf) {
                // okay its actually an language expression, so favor using that in the eip option
                kind = "expression";
                for (String language : ONE_OF_LANGUAGES) {
                    fieldTypeName = language;
                    TypeElement languages = findTypeElement(roundEnv, language);
                    String superClassName = canonicalClassName(languages.toString());
                    // find all classes that has that superClassName
                    Set<TypeElement> children = new LinkedHashSet<TypeElement>();
                    findTypeElementChildren(roundEnv, children, superClassName);
                    for (TypeElement child : children) {
                        XmlRootElement rootElement = child.getAnnotation(XmlRootElement.class);
                        if (rootElement != null) {
                            String childName = rootElement.name();
                            if (childName != null) {
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

            boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;

            EipOption ep = new EipOption(name, kind, fieldTypeName, required, defaultValue, docComment, deprecated, isEnum, enums, isOneOf, oneOfTypes);
            eipOptions.add(ep);
        }
    }

    private void processElements(RoundEnvironment roundEnv, TypeElement classElement, XmlElements elements, VariableElement fieldElement,
                                 Set<EipOption> eipOptions, String prefix) {
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
            String docComment = findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, true);

            boolean required = true;
            required = findRequired(fieldElement, required);

            // gather oneOf of the elements
            Set<String> oneOfTypes = new TreeSet<String>();
            for (XmlElement element : elements.value()) {
                String child = element.name();
                oneOfTypes.add(child);
            }

            EipOption ep = new EipOption(name, kind, fieldTypeName, required, defaultValue, docComment, false, false, null, true, oneOfTypes);
            eipOptions.add(ep);
        }
    }

    private void processRoute(RoundEnvironment roundEnv, TypeElement originalClassType, TypeElement classElement,
                              Set<EipOption> eipOptions, String prefix) {

        Elements elementUtils = processingEnv.getElementUtils();

        // group
        String docComment = findJavaDoc(elementUtils, null, "group", null, classElement, true);
        EipOption ep = new EipOption("group", "attribute", "java.lang.String", false, "", docComment, false, false, null, false, null);
        eipOptions.add(ep);

        // group
        docComment = findJavaDoc(elementUtils, null, "streamCache", null, classElement, true);
        ep = new EipOption("streamCache", "attribute", "java.lang.String", false, "", docComment, false, false, null, false, null);
        eipOptions.add(ep);

        // trace
        docComment = findJavaDoc(elementUtils, null, "trace", null, classElement, true);
        ep = new EipOption("trace", "attribute", "java.lang.String", false, "", docComment, false, false, null, false, null);
        eipOptions.add(ep);

        // trace
        docComment = findJavaDoc(elementUtils, null, "messageHistory", null, classElement, true);
        ep = new EipOption("messageHistory", "attribute", "java.lang.String", false, "true", docComment, false, false, null, false, null);
        eipOptions.add(ep);

        // trace
        docComment = findJavaDoc(elementUtils, null, "handleFault", null, classElement, true);
        ep = new EipOption("handleFault", "attribute", "java.lang.String", false, "", docComment, false, false, null, false, null);
        eipOptions.add(ep);

        // delayer
        docComment = findJavaDoc(elementUtils, null, "delayer", null, classElement, true);
        ep = new EipOption("delayer", "attribute", "java.lang.String", false, "", docComment, false, false, null, false, null);
        eipOptions.add(ep);

        // autoStartup
        docComment = findJavaDoc(elementUtils, null, "autoStartup", null, classElement, true);
        ep = new EipOption("autoStartup", "attribute", "java.lang.String", false, "true", docComment, false, false, null, false, null);
        eipOptions.add(ep);

        // startupOrder
        docComment = findJavaDoc(elementUtils, null, "startupOrder", null, classElement, true);
        ep = new EipOption("startupOrder", "attribute", "java.lang.Integer", false, "", docComment, false, false, null, false, null);
        eipOptions.add(ep);

        // errorHandlerRef
        docComment = findJavaDoc(elementUtils, null, "errorHandlerRef", null, classElement, true);
        ep = new EipOption("errorHandlerRef", "attribute", "java.lang.String", false, "", docComment, false, false, null, false, null);
        eipOptions.add(ep);

        // routePolicyRef
        docComment = findJavaDoc(elementUtils, null, "routePolicyRef", null, classElement, true);
        ep = new EipOption("routePolicyRef", "attribute", "java.lang.String", false, "", docComment, false, false, null, false, null);
        eipOptions.add(ep);

        // shutdownRoute
        Set<String> enums = new LinkedHashSet<String>();
        enums.add("Default");
        enums.add("Defer");
        docComment = findJavaDoc(elementUtils, null, "shutdownRoute", "Default", classElement, true);
        ep = new EipOption("shutdownRoute", "attribute", "org.apache.camel.ShutdownRoute", false, "", docComment, false, true, enums, false, null);
        eipOptions.add(ep);

        // shutdownRunningTask
        enums = new LinkedHashSet<String>();
        enums.add("CompleteCurrentTaskOnly");
        enums.add("CompleteAllTasks");
        docComment = findJavaDoc(elementUtils, null, "shutdownRunningTask", "CompleteCurrentTaskOnly", classElement, true);
        ep = new EipOption("shutdownRunningTask", "attribute", "org.apache.camel.ShutdownRunningTask", false, "", docComment, false, true, enums, false, null);
        eipOptions.add(ep);

        // inputs
        Set<String> oneOfTypes = new TreeSet<String>();
        oneOfTypes.add("from");
        docComment = findJavaDoc(elementUtils, null, "inputs", null, classElement, true);
        ep = new EipOption("inputs", "element", "java.util.List<org.apache.camel.model.FromDefinition>", true, "", docComment, false, false, null, true, oneOfTypes);
        eipOptions.add(ep);

        // outputs
        // gather oneOf which extends any of the output base classes
        oneOfTypes = new TreeSet<String>();
        // find all classes that has that superClassName
        Set<TypeElement> children = new LinkedHashSet<TypeElement>();
        for (String superclass : ONE_OF_OUTPUTS) {
            findTypeElementChildren(roundEnv, children, superclass);
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

        // remove some types which are not intended as an output in eips
        oneOfTypes.remove("route");

        docComment = findJavaDoc(elementUtils, null, "outputs", null, classElement, true);
        ep = new EipOption("outputs", "element", "java.util.List<org.apache.camel.model.ProcessorDefinition<?>>", true, "", docComment, false, false, null, true, oneOfTypes);
        eipOptions.add(ep);
    }

    /**
     * Special for process the OptionalIdentifiedDefinition
     */
    private void processIdentified(RoundEnvironment roundEnv, TypeElement originalClassType, TypeElement classElement,
                                   Set<EipOption> eipOptions, String prefix) {

        Elements elementUtils = processingEnv.getElementUtils();

        // id
        String docComment = findJavaDoc(elementUtils, null, "id", null, classElement, true);
        EipOption ep = new EipOption("id", "attribute", "java.lang.String", false, "", docComment, false, false, null, false, null);
        eipOptions.add(ep);

        // description
        docComment = findJavaDoc(elementUtils, null, "description", null, classElement, true);
        ep = new EipOption("description", "element", "org.apache.camel.model.DescriptionDefinition", false, "", docComment, false, false, null, false, null);
        eipOptions.add(ep);

        // lets skip custom id as it has no value for end users to configure
        if (!skipUnwanted) {
            // custom id
            docComment = findJavaDoc(elementUtils, null, "customId", null, classElement, true);
            ep = new EipOption("customId", "attribute", "java.lang.String", false, "", docComment, false, false, null, false, null);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef routes field
     */
    private void processRoutes(RoundEnvironment roundEnv, TypeElement originalClassType, XmlElementRef elementRef,
                               VariableElement fieldElement, String fieldName, Set<EipOption> eipOptions, String prefix) {
        if ("routes".equals(fieldName)) {

            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            Set<String> oneOfTypes = new TreeSet<String>();
            oneOfTypes.add("route");

            EipOption ep = new EipOption("routes", "element", fieldTypeName, false, "", "Contains the Camel routes", false, false, null, true, oneOfTypes);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef rests field
     */
    private void processRests(RoundEnvironment roundEnv, TypeElement originalClassType, XmlElementRef elementRef,
                               VariableElement fieldElement, String fieldName, Set<EipOption> eipOptions, String prefix) {
        if ("rests".equals(fieldName)) {

            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            Set<String> oneOfTypes = new TreeSet<String>();
            oneOfTypes.add("rest");

            EipOption ep = new EipOption("rests", "element", fieldTypeName, false, "", "Contains the rest services defined using the rest-dsl", false, false, null, true, oneOfTypes);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef outputs field
     */
    private void processOutputs(RoundEnvironment roundEnv, TypeElement originalClassType, XmlElementRef elementRef,
                                VariableElement fieldElement, String fieldName, Set<EipOption> eipOptions, String prefix) {
        if ("outputs".equals(fieldName) && supportOutputs(originalClassType)) {
            String kind = "element";
            String name = elementRef.name();
            if (isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            // gather oneOf which extends any of the output base classes
            Set<String> oneOfTypes = new TreeSet<String>();
            // find all classes that has that superClassName
            Set<TypeElement> children = new LinkedHashSet<TypeElement>();
            for (String superclass : ONE_OF_OUTPUTS) {
                findTypeElementChildren(roundEnv, children, superclass);
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

            // remove some types which are not intended as an output in eips
            oneOfTypes.remove("route");

            EipOption ep = new EipOption(name, kind, fieldTypeName, true, "", "", false, false, null, true, oneOfTypes);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef verbs field (rest-dsl)
     */
    private void processVerbs(RoundEnvironment roundEnv, TypeElement originalClassType, XmlElementRef elementRef,
                              VariableElement fieldElement, String fieldName, Set<EipOption> eipOptions, String prefix) {

        Elements elementUtils = processingEnv.getElementUtils();

        if ("verbs".equals(fieldName) && supportOutputs(originalClassType)) {
            String kind = "element";
            String name = elementRef.name();
            if (isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            String docComment = findJavaDoc(elementUtils, fieldElement, fieldName, name, originalClassType, true);

            // gather oneOf which extends any of the output base classes
            Set<String> oneOfTypes = new TreeSet<String>();
            // find all classes that has that superClassName
            Set<TypeElement> children = new LinkedHashSet<TypeElement>();
            for (String superclass : ONE_OF_VERBS) {
                findTypeElementChildren(roundEnv, children, superclass);
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

            EipOption ep = new EipOption(name, kind, fieldTypeName, true, "", docComment, false, false, null, true, oneOfTypes);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef expression field
     */
    private void processRefExpression(RoundEnvironment roundEnv, TypeElement originalClassType, TypeElement classElement,
                                      XmlElementRef elementRef, VariableElement fieldElement,
                                      String fieldName, Set<EipOption> eipOptions, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();

        if ("expression".equals(fieldName)) {
            String kind = "expression";
            String name = elementRef.name();
            if (isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            // find javadoc from original class as it will override the setExpression method where we can provide the javadoc for the given EIP
            String docComment = findJavaDoc(elementUtils, fieldElement, fieldName, name, originalClassType, true);

            // gather oneOf expression/predicates which uses language
            Set<String> oneOfTypes = new TreeSet<String>();
            for (String language : ONE_OF_LANGUAGES) {
                TypeElement languages = findTypeElement(roundEnv, language);
                String superClassName = canonicalClassName(languages.toString());
                // find all classes that has that superClassName
                Set<TypeElement> children = new LinkedHashSet<TypeElement>();
                findTypeElementChildren(roundEnv, children, superClassName);
                for (TypeElement child : children) {
                    XmlRootElement rootElement = child.getAnnotation(XmlRootElement.class);
                    if (rootElement != null) {
                        String childName = rootElement.name();
                        if (childName != null) {
                            oneOfTypes.add(childName);
                        }
                    }
                }
            }

            boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;

            EipOption ep = new EipOption(name, kind, fieldTypeName, true, "", docComment, deprecated, false, null, true, oneOfTypes);
            eipOptions.add(ep);
        }
    }

    /**
     * Special for processing an @XmlElementRef when field
     */
    private void processRefWhenClauses(RoundEnvironment roundEnv, TypeElement originalClassType, XmlElementRef elementRef,
                                       VariableElement fieldElement, String fieldName, Set<EipOption> eipOptions, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();

        if ("whenClauses".equals(fieldName)) {
            String kind = "element";
            String name = elementRef.name();
            if (isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            // find javadoc from original class as it will override the setExpression method where we can provide the javadoc for the given EIP
            String docComment = findJavaDoc(elementUtils, fieldElement, fieldName, name, originalClassType, true);
            boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;

            // indicate that this element is one of when
            Set<String> oneOfTypes = new HashSet<String>();
            oneOfTypes.add("when");

            EipOption ep = new EipOption(name, kind, fieldTypeName, false, "", docComment, deprecated, false, null, true, oneOfTypes);
            eipOptions.add(ep);
        }
    }

    /**
     * Whether the class supports outputs.
     * <p/>
     * There are some classes which does not support outputs, even though they have a outputs element.
     */
    private boolean supportOutputs(TypeElement classElement) {
        String superclass = canonicalClassName(classElement.getSuperclass().toString());
        return !"org.apache.camel.model.NoOutputExpressionNode".equals(superclass);
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
            if (!Strings.isNullOrEmpty(metadata.required())) {
                defaultValue = "true".equals(metadata.required());
            }
        }
        return defaultValue;
    }

    /**
     * Capitializes the name as a title
     *
     * @param name  the name
     * @return as a title
     */
    private static String asTitle(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            boolean upper = Character.isUpperCase(c);
            boolean first = sb.length() == 0;
            if (first) {
                sb.append(Character.toUpperCase(c));
            } else if (upper) {
                sb.append(' ');
                sb.append(c);
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString().trim();
    }

    private boolean hasInput(RoundEnvironment roundEnv, TypeElement classElement) {
        for (String name : ONE_OF_INPUTS) {
            if (hasSuperClass(roundEnv, classElement, name)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOutput(EipModel model, Set<EipOption> options) {
        // if we are route/rest then we accept output
        if ("route".equals(model.getName()) || "rest".equals(model.getName())) {
            return true;
        }

        for (EipOption option : options) {
            if ("outputs".equals(option.getName())) {
                return true;
            }
        }
        return false;
    }

    private static final class EipModel {

        private String name;
        private String title;
        private String javaType;
        private String label;
        private String description;
        private boolean input;
        private boolean output;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getJavaType() {
            return javaType;
        }

        public void setJavaType(String javaType) {
            this.javaType = javaType;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isInput() {
            return input;
        }

        public void setInput(boolean input) {
            this.input = input;
        }

        public String getInput() {
            return input ? "true" : "false";
        }

        public boolean isOutput() {
            return output;
        }

        public void setOutput(boolean output) {
            this.output = output;
        }

        public String getOutput() {
            return output ? "true" : "false";
        }

    }

    private static final class EipOption {

        private String name;
        private String kind;
        private String type;
        private boolean required;
        private String defaultValue;
        private String documentation;
        private boolean deprecated;
        private boolean enumType;
        private Set<String> enums;
        private boolean oneOf;
        private Set<String> oneOfTypes;

        private EipOption(String name, String kind, String type, boolean required, String defaultValue, String documentation, boolean deprecated,
                          boolean enumType, Set<String> enums, boolean oneOf, Set<String> oneOfTypes) {
            this.name = name;
            this.kind = kind;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
            this.documentation = documentation;
            this.deprecated = deprecated;
            this.enumType = enumType;
            this.enums = enums;
            this.oneOf = oneOf;
            this.oneOfTypes = oneOfTypes;
        }

        public String getName() {
            return name;
        }

        public String getKind() {
            return kind;
        }

        public String getType() {
            return type;
        }

        public boolean isRequired() {
            return required;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public String getDocumentation() {
            return documentation;
        }

        public boolean isDeprecated() {
            return deprecated;
        }

        public boolean isEnumType() {
            return enumType;
        }

        public Set<String> getEnums() {
            return enums;
        }

        public boolean isOneOf() {
            return oneOf;
        }

        public Set<String> getOneOfTypes() {
            return oneOfTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            EipOption that = (EipOption) o;

            if (!name.equals(that.name)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    private static final class EipOptionComparator implements Comparator<EipOption> {

        private final EipModel model;

        private EipOptionComparator(EipModel model) {
            this.model = model;
        }

        @Override
        public int compare(EipOption o1, EipOption o2) {
            int weigth = weigth(o1);
            int weigth2 = weigth(o2);

            if (weigth == weigth2) {
                // keep the current order
                return 1;
            } else {
                // sort according to weight
                return weigth2 - weigth;
            }
        }

        private int weigth(EipOption o) {
            String name = o.getName();
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
