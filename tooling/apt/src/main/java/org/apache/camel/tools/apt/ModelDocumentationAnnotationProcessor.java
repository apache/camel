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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Label;

import static org.apache.camel.tools.apt.JsonSchemaHelper.sanitizeDescription;
import static org.apache.camel.tools.apt.Strings.canonicalClassName;
import static org.apache.camel.tools.apt.Strings.isNullOrEmpty;
import static org.apache.camel.tools.apt.Strings.safeNull;

// TODO: skip abstract model classes

/**
 * Process all camel-core's model classes (EIPs and DSL) and generate json schema documentation
 */
@SupportedAnnotationTypes({"javax.xml.bind.annotation.*", "org.apache.camel.spi.Label"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class ModelDocumentationAnnotationProcessor extends AbstractAnnotationProcessor {

    // special when using expression/predicates in the model
    private static final String ONE_OF_TYPE_NAME = "org.apache.camel.model.ExpressionSubElementDefinition";
    private static final String ONE_OF_LANGUAGES = "org.apache.camel.model.language.ExpressionDefinition";
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

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(XmlRootElement.class);
        for (Element element : elements) {
            if (element instanceof TypeElement) {
                processModelClass(roundEnv, (TypeElement) element);
            }
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

        final XmlRootElement rootElement = classElement.getAnnotation(XmlRootElement.class);
        final String name = rootElement.name();

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
        Set<EipOption> eipOptions = new LinkedHashSet<EipOption>();
        findClassProperties(writer, roundEnv, eipOptions, classElement, classElement, "");

        String json = createParameterJsonSchema(eipModel, eipOptions);
        writer.println(json);
    }

    public String createParameterJsonSchema(EipModel eipModel, Set<EipOption> options) {
        StringBuilder buffer = new StringBuilder("{");
        // eip model
        buffer.append("\n \"model\": {");
        buffer.append("\n    \"name\": \"").append(eipModel.getName()).append("\",");
        buffer.append("\n    \"description\": \"").append(safeNull(eipModel.getDescription())).append("\",");
        buffer.append("\n    \"javaType\": \"").append(eipModel.getJavaType()).append("\",");
        buffer.append("\n    \"label\": \"").append(safeNull(eipModel.getLabel())).append("\",");
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
                    entry.isEnumType(), entry.getEnums(), entry.isOneOf(), entry.getOneOfTypes()));
        }
        buffer.append("\n  }");

        buffer.append("\n}\n");
        return buffer.toString();
    }

    protected EipModel findEipModelProperties(RoundEnvironment roundEnv, TypeElement classElement, String javaTypeName, String name) {
        EipModel model = new EipModel();
        model.setJavaType(javaTypeName);
        model.setName(name);

        Label label = classElement.getAnnotation(Label.class);
        if (label != null) {
            model.setLabel(label.value());
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
        Elements elementUtils = processingEnv.getElementUtils();
        while (true) {
            List<VariableElement> fieldElements = ElementFilter.fieldsIn(classElement.getEnclosedElements());
            for (VariableElement fieldElement : fieldElements) {

                XmlAttribute attribute = fieldElement.getAnnotation(XmlAttribute.class);
                String fieldName = fieldElement.getSimpleName().toString();
                if (attribute != null) {
                    String name = attribute.name();
                    if (isNullOrEmpty(name) || "##default".equals(name)) {
                        name = fieldName;
                    }
                    name = prefix + name;
                    TypeMirror fieldType = fieldElement.asType();
                    String fieldTypeName = fieldType.toString();
                    TypeElement fieldTypeElement = findTypeElement(roundEnv, fieldTypeName);

                    String docComment = findJavaDoc(elementUtils, fieldElement, fieldName, classElement, true);
                    boolean required = attribute.required();

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

                    EipOption ep = new EipOption(name, "attribute", fieldTypeName, required, "", docComment, isEnum, enums, false, null);
                    eipOptions.add(ep);
                }

                XmlElement element = fieldElement.getAnnotation(XmlElement.class);
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

                    String docComment = findJavaDoc(elementUtils, fieldElement, fieldName, classElement, true);
                    boolean required = element.required();

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
                        TypeElement languages = findTypeElement(roundEnv, ONE_OF_LANGUAGES);
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

                    EipOption ep = new EipOption(name, kind, fieldTypeName, required, "", docComment, isEnum, enums, isOneOf, oneOfTypes);
                    eipOptions.add(ep);
                }

                // special for eips which has outputs or requires an expressions
                XmlElementRef elementRef = fieldElement.getAnnotation(XmlElementRef.class);
                fieldName = fieldElement.getSimpleName().toString();
                if (elementRef != null) {

                    // special for outputs
                    processOutputs(roundEnv, originalClassType, elementRef, fieldElement, fieldName, eipOptions, prefix);

                    // special for expression
                    processExpression(roundEnv, elementRef, fieldElement, fieldName, eipOptions, prefix);
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

    private void processRoute(RoundEnvironment roundEnv, TypeElement originalClassType, TypeElement classElement,
                              Set<EipOption> eipOptions, String prefix) {

        Elements elementUtils = processingEnv.getElementUtils();

        // group
        String docComment = findJavaDoc(elementUtils, null, "group", classElement, true);
        EipOption ep = new EipOption("group", "attribute", "java.lang.String", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);

        // group
        docComment = findJavaDoc(elementUtils, null, "streamCache", classElement, true);
        ep = new EipOption("streamCache", "attribute", "java.lang.String", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);

        // trace
        docComment = findJavaDoc(elementUtils, null, "trace", classElement, true);
        ep = new EipOption("trace", "attribute", "java.lang.String", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);

        // trace
        docComment = findJavaDoc(elementUtils, null, "messageHistory", classElement, true);
        ep = new EipOption("messageHistory", "attribute", "java.lang.String", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);

        // trace
        docComment = findJavaDoc(elementUtils, null, "handleFault", classElement, true);
        ep = new EipOption("handleFault", "attribute", "java.lang.String", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);

        // delayer
        docComment = findJavaDoc(elementUtils, null, "delayer", classElement, true);
        ep = new EipOption("delayer", "attribute", "java.lang.String", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);

        // autoStartup
        docComment = findJavaDoc(elementUtils, null, "autoStartup", classElement, true);
        ep = new EipOption("autoStartup", "attribute", "java.lang.String", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);

        // startupOrder
        docComment = findJavaDoc(elementUtils, null, "startupOrder", classElement, true);
        ep = new EipOption("startupOrder", "attribute", "java.lang.Integer", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);

        // errorHandlerRef
        docComment = findJavaDoc(elementUtils, null, "errorHandlerRef", classElement, true);
        ep = new EipOption("errorHandlerRef", "attribute", "java.lang.String", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);

        // routePolicyRef
        docComment = findJavaDoc(elementUtils, null, "routePolicyRef", classElement, true);
        ep = new EipOption("routePolicyRef", "attribute", "java.lang.String", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);

        // shutdownRoute
        Set<String> enums = new LinkedHashSet<String>();
        enums.add("Default");
        enums.add("Defer");
        docComment = findJavaDoc(elementUtils, null, "shutdownRoute", classElement, true);
        ep = new EipOption("shutdownRoute", "attribute", "org.apache.camel.ShutdownRoute", false, "", docComment, true, enums, false, null);
        eipOptions.add(ep);

        // shutdownRunningTask
        enums = new LinkedHashSet<String>();
        enums.add("CompleteCurrentTaskOnly");
        enums.add("CompleteAllTasks");
        docComment = findJavaDoc(elementUtils, null, "shutdownRunningTask", classElement, true);
        ep = new EipOption("shutdownRunningTask", "attribute", "org.apache.camel.ShutdownRunningTask", false, "", docComment, true, enums, false, null);
        eipOptions.add(ep);

        // inputs
        Set<String> oneOfTypes = new TreeSet<String>();
        oneOfTypes.add("from");
        docComment = findJavaDoc(elementUtils, null, "inputs", classElement, true);
        ep = new EipOption("inputs", "element", "java.util.List<org.apache.camel.model.FromDefinition>", true, "", docComment, false, null, true, oneOfTypes);
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

        docComment = findJavaDoc(elementUtils, null, "outputs", classElement, true);
        ep = new EipOption("outputs", "element", "java.util.List<org.apache.camel.model.ProcessorDefinition<?>>", true, "", docComment, false, null, true, oneOfTypes);
        eipOptions.add(ep);
    }

    /**
     * Special for process the OptionalIdentifiedDefinition
     */
    private void processIdentified(RoundEnvironment roundEnv, TypeElement originalClassType, TypeElement classElement,
                                   Set<EipOption> eipOptions, String prefix) {

        Elements elementUtils = processingEnv.getElementUtils();

        // id
        String docComment = findJavaDoc(elementUtils, classElement, "id", classElement, true);
        EipOption ep = new EipOption("id", "attribute", "java.lang.String", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);

        // description
        docComment = findJavaDoc(elementUtils, classElement, "description", classElement, true);
        ep = new EipOption("description", "element", "org.apache.camel.model.DescriptionDefinition", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);

        // custom id
        docComment = findJavaDoc(elementUtils, classElement, "customId", classElement, true);
        ep = new EipOption("customId", "attribute", "java.lang.String", false, "", docComment, false, null, false, null);
        eipOptions.add(ep);
    }

    /**
     * Special for processing an @XmlElementRef expression field
     */
    private void processExpression(RoundEnvironment roundEnv, XmlElementRef elementRef, VariableElement fieldElement,
                                   String fieldName, Set<EipOption> eipOptions, String prefix) {
        if ("expression".equals(fieldName)) {
            String kind = "element";
            String name = elementRef.name();
            if (isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();

            // gather oneOf expression/predicates which uses language
            Set<String> oneOfTypes = new TreeSet<String>();
            TypeElement languages = findTypeElement(roundEnv, ONE_OF_LANGUAGES);
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

            EipOption ep = new EipOption(name, kind, fieldTypeName, true, "", "", false, null, true, oneOfTypes);
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

            EipOption ep = new EipOption(name, kind, fieldTypeName, true, "", "", false, null, true, oneOfTypes);
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

    private static final class EipModel {

        private String name;
        private String javaType;
        private String label;
        private String description;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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
    }

    private static final class EipOption {

        private String name;
        private String kind;
        private String type;
        private boolean required;
        private String defaultValue;
        private String documentation;
        private boolean enumType;
        private Set<String> enums;
        private boolean oneOf;
        private Set<String> oneOfTypes;

        private EipOption(String name, String kind, String type, boolean required, String defaultValue, String documentation,
                          boolean enumType, Set<String> enums, boolean oneOf, Set<String> oneOfTypes) {
            this.name = name;
            this.kind = kind;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
            this.documentation = documentation;
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

}