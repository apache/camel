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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.tools.apt.JsonSchemaHelper.sanitizeDescription;
import static org.apache.camel.tools.apt.Strings.canonicalClassName;
import static org.apache.camel.tools.apt.Strings.getOrElse;
import static org.apache.camel.tools.apt.Strings.isNullOrEmpty;
import static org.apache.camel.tools.apt.Strings.safeNull;

/**
 * Processes all Camel {@link UriEndpoint}s and generate json schema and html documentation for the endpoint/component.
 */
@SupportedAnnotationTypes({"org.apache.camel.spi.*"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class EndpointAnnotationProcessor extends AbstractAnnotationProcessor {

    public boolean process(Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(UriEndpoint.class);
        for (Element element : elements) {
            if (element instanceof TypeElement) {
                processEndpointClass(roundEnv, (TypeElement) element);
            }
        }
        return true;
    }

    protected void processEndpointClass(final RoundEnvironment roundEnv, final TypeElement classElement) {
        final UriEndpoint uriEndpoint = classElement.getAnnotation(UriEndpoint.class);
        if (uriEndpoint != null) {
            String scheme = uriEndpoint.scheme();
            final String label = uriEndpoint.label();
            if (!isNullOrEmpty(scheme)) {
                // support multiple schemes separated by comma, which maps to the exact same component
                // for example camel-mail has a bunch of component schema names that does that
                String[] schemes = scheme.split(",");
                for (final String alias : schemes) {
                    // write html documentation
                    String name = canonicalClassName(classElement.getQualifiedName().toString());
                    String packageName = name.substring(0, name.lastIndexOf("."));
                    String fileName = alias + ".html";
                    Func1<PrintWriter, Void> handler = new Func1<PrintWriter, Void>() {
                        @Override
                        public Void call(PrintWriter writer) {
                            writeHtmlDocumentation(writer, roundEnv, classElement, uriEndpoint, alias, label);
                            return null;
                        }
                    };
                    processFile(packageName, fileName, handler);

                    // write json schema
                    fileName = alias + ".json";
                    handler = new Func1<PrintWriter, Void>() {
                        @Override
                        public Void call(PrintWriter writer) {
                            writeJSonSchemeDocumentation(writer, roundEnv, classElement, uriEndpoint, alias, label);
                            return null;
                        }
                    };
                    processFile(packageName, fileName, handler);
                }
            }
        }
    }

    protected void writeHtmlDocumentation(PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement, UriEndpoint uriEndpoint, String scheme, String label) {
        writer.println("<html>");
        writer.println("<header>");
        String title = scheme + " endpoint";
        writer.println("<title>" + title  + "</title>");
        writer.println("</header>");
        writer.println("<body>");
        writer.println("<h1>" + title + "</h1>");

        if (label != null) {
            String[] labels = label.split(",");
            writer.println("<ul>");
            for (String text : labels) {
                writer.println("<li>" + text + "</li>");
            }
            writer.println("</ul>");
        }

        showDocumentationAndFieldInjections(writer, roundEnv, classElement, "");

        // This code is not my fault, it seems to honestly be the hacky way to find a class name in APT :)
        TypeMirror consumerType = null;
        try {
            uriEndpoint.consumerClass();
        } catch (MirroredTypeException mte) {
            consumerType = mte.getTypeMirror();
        }

        boolean found = false;
        String consumerClassName = null;
        String consumerPrefix = getOrElse(uriEndpoint.consumerPrefix(), "");
        if (consumerType != null) {
            consumerClassName = consumerType.toString();
            TypeElement consumerElement = findTypeElement(roundEnv, consumerClassName);
            if (consumerElement != null) {
                writer.println("<h2>" + scheme + " consumer" + "</h2>");
                showDocumentationAndFieldInjections(writer, roundEnv, consumerElement, consumerPrefix);
                found = true;
            }
        }
        if (!found && consumerClassName != null) {
            warning("APT could not find consumer class " + consumerClassName);
        }
        writer.println("</body>");
        writer.println("</html>");
    }

    protected void writeJSonSchemeDocumentation(PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement, UriEndpoint uriEndpoint, String scheme, String label) {
        // gather component information
        ComponentModel componentModel = findComponentProperties(roundEnv, uriEndpoint, scheme, label);

        // get endpoint information which is divided into paths and options (though there should really only be one path)
        Set<EndpointPath> endpointPaths = new LinkedHashSet<EndpointPath>();
        Set<EndpointOption> endpointOptions = new LinkedHashSet<EndpointOption>();
        Set<ComponentOption> componentOptions = new LinkedHashSet<ComponentOption>();

        TypeElement componentClassElement = findTypeElement(roundEnv, componentModel.getJavaType());
        if (componentClassElement != null) {
            findComponentClassProperties(writer, roundEnv, componentOptions, componentClassElement, "");
        }

        findClassProperties(writer, roundEnv, endpointPaths, endpointOptions, classElement, "");

        String json = createParameterJsonSchema(componentModel, componentOptions, endpointPaths, endpointOptions);
        writer.println(json);
    }

    public String createParameterJsonSchema(ComponentModel componentModel, Set<ComponentOption> componentOptions,
                                            Set<EndpointPath> endpointPaths, Set<EndpointOption> endpointOptions) {
        StringBuilder buffer = new StringBuilder("{");
        // component model
        buffer.append("\n \"component\": {");
        buffer.append("\n    \"kind\": \"").append("component").append("\",");
        buffer.append("\n    \"scheme\": \"").append(componentModel.getScheme()).append("\",");
        buffer.append("\n    \"syntax\": \"").append(componentModel.getSyntax()).append("\",");
        buffer.append("\n    \"description\": \"").append(componentModel.getDescription()).append("\",");
        buffer.append("\n    \"label\": \"").append(getOrElse(componentModel.getLabel(), "")).append("\",");
        if (componentModel.isConsumerOnly()) {
            buffer.append("\n    \"consumerOnly\": \"").append("true").append("\",");
        } else if (componentModel.isProducerOnly()) {
            buffer.append("\n    \"producerOnly\": \"").append("true").append("\",");
        }
        buffer.append("\n    \"javaType\": \"").append(componentModel.getJavaType()).append("\",");
        buffer.append("\n    \"groupId\": \"").append(componentModel.getGroupId()).append("\",");
        buffer.append("\n    \"artifactId\": \"").append(componentModel.getArtifactId()).append("\",");
        buffer.append("\n    \"version\": \"").append(componentModel.getVersionId()).append("\"");
        buffer.append("\n  },");

        // and component properties
        buffer.append("\n  \"componentProperties\": {");
        boolean first = true;
        for (ComponentOption entry : componentOptions) {
            if (first) {
                first = false;
            } else {
                buffer.append(",");
            }
            buffer.append("\n    ");
            // as its json we need to sanitize the docs
            String doc = entry.getDocumentationWithNotes();
            doc = sanitizeDescription(doc, false);
            Boolean required = entry.getRequired() != null ? Boolean.valueOf(entry.getRequired()) : null;

            buffer.append(JsonSchemaHelper.toJson(entry.getName(), "property", required, entry.getType(), entry.getDefaultValue(), doc,
                    entry.isDeprecated(), entry.getLabel(), entry.isEnumType(), entry.getEnums(), false, null));
        }
        buffer.append("\n  },");

        buffer.append("\n  \"properties\": {");
        first = true;

        // include paths in the top
        for (EndpointPath entry : endpointPaths) {
            String label = entry.getLabel();
            if (label != null) {
                // skip options which are either consumer or producer labels but the component does not support them
                if (label.contains("consumer") && componentModel.isProducerOnly()) {
                    continue;
                } else if (label.contains("producer") && componentModel.isConsumerOnly()) {
                    continue;
                }
            }

            if (first) {
                first = false;
            } else {
                buffer.append(",");
            }
            buffer.append("\n    ");
            String doc = entry.getDocumentation();
            doc = sanitizeDescription(doc, false);
            Boolean required = entry.getRequired() != null ? Boolean.valueOf(entry.getRequired()) : null;

            buffer.append(JsonSchemaHelper.toJson(entry.getName(), "path", required, entry.getType(), entry.getDefaultValue(), doc,
                    entry.isDeprecated(), label, entry.isEnumType(), entry.getEnums(), false, null));
        }

        // and then regular parameter options
        for (EndpointOption entry : endpointOptions) {
            String label = entry.getLabel();
            if (label != null) {
                // skip options which are either consumer or producer labels but the component does not support them
                if (label.contains("consumer") && componentModel.isProducerOnly()) {
                    continue;
                } else if (label.contains("producer") && componentModel.isConsumerOnly()) {
                    continue;
                }
            }

            if (first) {
                first = false;
            } else {
                buffer.append(",");
            }
            buffer.append("\n    ");
            // as its json we need to sanitize the docs
            String doc = entry.getDocumentationWithNotes();
            doc = sanitizeDescription(doc, false);
            Boolean required = entry.getRequired() != null ? Boolean.valueOf(entry.getRequired()) : null;

            buffer.append(JsonSchemaHelper.toJson(entry.getName(), "parameter", required, entry.getType(), entry.getDefaultValue(),
                    doc, entry.isDeprecated(), label, entry.isEnumType(), entry.getEnums(), false, null));
        }
        buffer.append("\n  }");

        buffer.append("\n}\n");
        return buffer.toString();
    }

    protected void showDocumentationAndFieldInjections(PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement, String prefix) {
        String classDoc = processingEnv.getElementUtils().getDocComment(classElement);
        if (!isNullOrEmpty(classDoc)) {
            // remove dodgy @version that we may have in class javadoc
            classDoc = classDoc.replaceFirst("\\@version", "");
            classDoc = classDoc.trim();
            writer.println("<p>" + classDoc + "</p>");
        }

        Set<EndpointPath> endpointPaths = new LinkedHashSet<EndpointPath>();
        Set<EndpointOption> endpointOptions = new LinkedHashSet<EndpointOption>();
        findClassProperties(writer, roundEnv, endpointPaths, endpointOptions, classElement, prefix);

        if (!endpointOptions.isEmpty() || !endpointPaths.isEmpty()) {
            writer.println("<table class='table'>");
            writer.println("  <tr>");
            writer.println("    <th>Name</th>");
            writer.println("    <th>Kind</th>");
            writer.println("    <th>Type</th>");
            writer.println("    <th>Required</th>");
            writer.println("    <th>Deprecated</th>");
            writer.println("    <th>Default Value</th>");
            writer.println("    <th>Enum Values</th>");
            writer.println("    <th>Description</th>");
            writer.println("  </tr>");
            // include paths in the top
            for (EndpointPath path : endpointPaths) {
                writer.println("  <tr>");
                writer.println("    <td>" + path.getName() + "</td>");
                writer.println("    <td>" + "path" + "</td>");
                writer.println("    <td>" + path.getType() + "</td>");
                writer.println("    <td>" + safeNull(path.getRequired()) + "</td>");
                writer.println("    <td>" + path.isDeprecated() + "</td>");
                writer.println("    <td>" + path.getEnumValuesAsHtml() + "</td>");
                writer.println("    <td>" + path.getDocumentation() + "</td>");
                writer.println("  </tr>");
            }
            // and then regular parameter options
            for (EndpointOption option : endpointOptions) {
                writer.println("  <tr>");
                writer.println("    <td>" + option.getName() + "</td>");
                writer.println("    <td>" + "parameter" + "</td>");
                writer.println("    <td>" + option.getType() + "</td>");
                writer.println("    <td>" + safeNull(option.getRequired()) + "</td>");
                writer.println("    <td>" + option.isDeprecated() + "</td>");
                writer.println("    <td>" + option.getDefaultValue() + "</td>");
                writer.println("    <td>" + option.getEnumValuesAsHtml() + "</td>");
                writer.println("    <td>" + option.getDocumentationWithNotes() + "</td>");
                writer.println("  </tr>");
            }
            writer.println("</table>");
        }
    }

    protected ComponentModel findComponentProperties(RoundEnvironment roundEnv, UriEndpoint uriEndpoint, String scheme, String label) {
        ComponentModel model = new ComponentModel(scheme);
        model.setSyntax(uriEndpoint.syntax());
        model.setLabel(label);
        model.setConsumerOnly(uriEndpoint.consumerOnly());
        model.setProducerOnly(uriEndpoint.producerOnly());

        String data = loadResource("META-INF/services/org/apache/camel/component", scheme);
        if (data != null) {
            Map<String, String> map = parseAsMap(data);
            model.setJavaType(map.get("class"));
        }

        data = loadResource("META-INF/services/org/apache/camel", "component.properties");
        if (data != null) {
            Map<String, String> map = parseAsMap(data);
            // now we have a lot more data, so we need to load it as key/value
            // need to sanitize the description first
            String doc = map.get("projectDescription");
            if (doc != null) {
                model.setDescription(sanitizeDescription(doc, true));
            } else {
                model.setDescription("");
            }
            if (map.containsKey("groupId")) {
                model.setGroupId(map.get("groupId"));
            } else {
                model.setGroupId("");
            }
            if (map.containsKey("artifactId")) {
                model.setArtifactId(map.get("artifactId"));
            } else {
                model.setArtifactId("");
            }
            if (map.containsKey("version")) {
                model.setVersionId(map.get("version"));
            } else {
                model.setVersionId("");
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

    protected void findComponentClassProperties(PrintWriter writer, RoundEnvironment roundEnv, Set<ComponentOption> componentOptions, TypeElement classElement, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();
        while (true) {
            List<VariableElement> fieldElements = ElementFilter.fieldsIn(classElement.getEnclosedElements());
            for (VariableElement fieldElement : fieldElements) {
                String fieldName = fieldElement.getSimpleName().toString();
                boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;
                Metadata metadata = fieldElement.getAnnotation(Metadata.class);

                // skip unwanted fields as they are inherited from default component and are not intended for end users to configure
                if ("endpointClass".equals(fieldName) || "camelContext".equals(fieldName)) {
                    continue;
                }

                // must be a getter/setter pair
                ExecutableElement getter = findGetter(fieldName, classElement);
                ExecutableElement setter = findSetter(fieldName, classElement);
                if (getter != null && setter != null) {
                    String name = fieldName;
                    name = prefix + name;
                    TypeMirror fieldType = fieldElement.asType();
                    String fieldTypeName = fieldType.toString();
                    TypeElement fieldTypeElement = findTypeElement(roundEnv, fieldTypeName);

                    String required =  metadata != null ? metadata.required() : null;
                    String label = metadata != null ? metadata.label() : null;

                    // we do not yet have default values / notes / as no annotation support yet
                    // String defaultValueNote = param.defaultValueNote();
                    String defaultValue = metadata != null ? metadata.defaultValue() : null;
                    String defaultValueNote = null;

                    String docComment = findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, false);
                    if (docComment == null) {
                        docComment = "";
                    }

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

                    ComponentOption option = new ComponentOption(name, fieldTypeName, required, defaultValue, defaultValueNote,
                            docComment.trim(), deprecated, label, isEnum, enums);
                    componentOptions.add(option);
                }
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

    protected void findClassProperties(PrintWriter writer, RoundEnvironment roundEnv, Set<EndpointPath> endpointPaths, Set<EndpointOption> endpointOptions, TypeElement classElement, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();
        while (true) {
            List<VariableElement> fieldElements = ElementFilter.fieldsIn(classElement.getEnclosedElements());
            for (VariableElement fieldElement : fieldElements) {

                Metadata metadata = fieldElement.getAnnotation(Metadata.class);
                boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;

                UriPath path = fieldElement.getAnnotation(UriPath.class);
                String fieldName = fieldElement.getSimpleName().toString();
                if (path != null) {
                    String name = path.name();
                    if (isNullOrEmpty(name)) {
                        name = fieldName;
                    }
                    name = prefix + name;

                    String defaultValue = path.defaultValue();
                    if (defaultValue == null && metadata != null) {
                        defaultValue = metadata.defaultValue();
                    }
                    String defaultValueNote = path.defaultValueNote();
                    String required =  metadata != null ? metadata.required() : null;
                    String label = path.label();

                    TypeMirror fieldType = fieldElement.asType();
                    String fieldTypeName = fieldType.toString();
                    TypeElement fieldTypeElement = findTypeElement(roundEnv, fieldTypeName);

                    String docComment = findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, false);
                    if (isNullOrEmpty(docComment)) {
                        docComment = path.description();
                    }

                    // gather enums
                    Set<String> enums = new LinkedHashSet<String>();

                    boolean isEnum;
                    if (!Strings.isNullOrEmpty(path.enums())) {
                        isEnum = true;
                        String[] values = path.enums().split(",");
                        for (String val : values) {
                            enums.add(val);
                        }
                    } else {
                        isEnum = fieldTypeElement != null && fieldTypeElement.getKind() == ElementKind.ENUM;
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
                    }

                    EndpointPath ep = new EndpointPath(name, fieldTypeName, required, defaultValue, docComment, deprecated, label, isEnum, enums);
                    endpointPaths.add(ep);
                }

                UriParam param = fieldElement.getAnnotation(UriParam.class);
                fieldName = fieldElement.getSimpleName().toString();
                if (param != null) {
                    String name = param.name();
                    if (isNullOrEmpty(name)) {
                        name = fieldName;
                    }
                    name = prefix + name;

                    String defaultValue = param.defaultValue();
                    if (defaultValue == null && metadata != null) {
                        defaultValue = metadata.defaultValue();
                    }
                    String defaultValueNote = param.defaultValueNote();
                    String required =  metadata != null ? metadata.required() : null;
                    String label = param.label();

                    // if the field type is a nested parameter then iterate through its fields
                    TypeMirror fieldType = fieldElement.asType();
                    String fieldTypeName = fieldType.toString();
                    TypeElement fieldTypeElement = findTypeElement(roundEnv, fieldTypeName);
                    UriParams fieldParams = null;
                    if (fieldTypeElement != null) {
                        fieldParams = fieldTypeElement.getAnnotation(UriParams.class);
                    }
                    if (fieldParams != null) {
                        String nestedPrefix = prefix;
                        String extraPrefix = fieldParams.prefix();
                        if (!isNullOrEmpty(extraPrefix)) {
                            nestedPrefix += extraPrefix;
                        }
                        findClassProperties(writer, roundEnv, endpointPaths, endpointOptions, fieldTypeElement, nestedPrefix);
                    } else {
                        String docComment = findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, false);
                        if (isNullOrEmpty(docComment)) {
                            docComment = param.description();
                        }

                        // gather enums
                        Set<String> enums = new LinkedHashSet<String>();

                        boolean isEnum;
                        if (!Strings.isNullOrEmpty(param.enums())) {
                            isEnum = true;
                            String[] values = param.enums().split(",");
                            for (String val : values) {
                                enums.add(val);
                            }
                        } else {
                            isEnum = fieldTypeElement != null && fieldTypeElement.getKind() == ElementKind.ENUM;
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
                        }

                        EndpointOption option = new EndpointOption(name, fieldTypeName, required, defaultValue, defaultValueNote,
                                docComment.trim(), deprecated, label, isEnum, enums);
                        endpointOptions.add(option);
                    }
                }
            }

            // check super classes which may also have @UriParam fields
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

    protected Map<String, String> parseAsMap(String data) {
        Map<String, String> answer = new HashMap<String, String>();
        String[] lines = data.split("\n");
        for (String line : lines) {
            int idx = line.indexOf('=');
            String key = line.substring(0, idx);
            String value = line.substring(idx + 1);
            // remove ending line break for the values
            value = value.trim().replaceAll("\n", "");
            answer.put(key.trim(), value);
        }
        return answer;
    }

    private static final class ComponentModel {

        private String scheme;
        private String syntax;
        private String javaType;
        private String description;
        private String groupId;
        private String artifactId;
        private String versionId;
        private String label;
        private boolean consumerOnly;
        private boolean producerOnly;

        private ComponentModel(String scheme) {
            this.scheme = scheme;
        }

        public String getScheme() {
            return scheme;
        }

        public String getSyntax() {
            return syntax;
        }

        public void setSyntax(String syntax) {
            this.syntax = syntax;
        }

        public String getJavaType() {
            return javaType;
        }

        public void setJavaType(String javaType) {
            this.javaType = javaType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getVersionId() {
            return versionId;
        }

        public void setVersionId(String versionId) {
            this.versionId = versionId;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public boolean isConsumerOnly() {
            return consumerOnly;
        }

        public void setConsumerOnly(boolean consumerOnly) {
            this.consumerOnly = consumerOnly;
        }

        public boolean isProducerOnly() {
            return producerOnly;
        }

        public void setProducerOnly(boolean producerOnly) {
            this.producerOnly = producerOnly;
        }
    }

    private static final class ComponentOption {

        private String name;
        private String type;
        private String required;
        private String defaultValue;
        private String defaultValueNote;
        private String documentation;
        private boolean deprecated;
        private String label;
        private boolean enumType;
        private Set<String> enums;

        private ComponentOption(String name, String type, String required, String defaultValue, String defaultValueNote,
                                String documentation, boolean deprecated, String label, boolean enumType, Set<String> enums) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
            this.defaultValueNote = defaultValueNote;
            this.documentation = documentation;
            this.deprecated = deprecated;
            this.label = label;
            this.enumType = enumType;
            this.enums = enums;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getRequired() {
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

        public String getEnumValuesAsHtml() {
            CollectionStringBuffer csb = new CollectionStringBuffer("<br/>");
            if (enums != null && enums.size() > 0) {
                for (String e : enums) {
                    csb.append(e);
                }
            }
            return csb.toString();
        }

        public String getDocumentationWithNotes() {
            StringBuilder sb = new StringBuilder();
            sb.append(documentation);

            if (!isNullOrEmpty(defaultValueNote)) {
                sb.append(". Default value notice: ").append(defaultValueNote);
            }

            return sb.toString();
        }

        public boolean isEnumType() {
            return enumType;
        }

        public Set<String> getEnums() {
            return enums;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            EndpointOption that = (EndpointOption) o;

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

    private static final class EndpointOption {

        private String name;
        private String type;
        private String required;
        private String defaultValue;
        private String defaultValueNote;
        private String documentation;
        private boolean deprecated;
        private String label;
        private boolean enumType;
        private Set<String> enums;

        private EndpointOption(String name, String type, String required, String defaultValue, String defaultValueNote,
                               String documentation, boolean deprecated, String label, boolean enumType, Set<String> enums) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
            this.defaultValueNote = defaultValueNote;
            this.documentation = documentation;
            this.deprecated = deprecated;
            this.label = label;
            this.enumType = enumType;
            this.enums = enums;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getRequired() {
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

        public String getEnumValuesAsHtml() {
            CollectionStringBuffer csb = new CollectionStringBuffer("<br/>");
            if (enums != null && enums.size() > 0) {
                for (String e : enums) {
                    csb.append(e);
                }
            }
            return csb.toString();
        }

        public String getDocumentationWithNotes() {
            StringBuilder sb = new StringBuilder();
            sb.append(documentation);

            if (!isNullOrEmpty(defaultValueNote)) {
                sb.append(". Default value notice: ").append(defaultValueNote);
            }

            return sb.toString();
        }

        public boolean isEnumType() {
            return enumType;
        }

        public Set<String> getEnums() {
            return enums;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            EndpointOption that = (EndpointOption) o;

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

    private static final class EndpointPath {

        private String name;
        private String type;
        private String required;
        private String defaultValue;
        private String documentation;
        private boolean deprecated;
        private String label;
        private boolean enumType;
        private Set<String> enums;

        private EndpointPath(String name, String type, String required, String defaultValue, String documentation, boolean deprecated,
                             String label, boolean enumType, Set<String> enums) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
            this.documentation = documentation;
            this.deprecated = deprecated;
            this.label = label;
            this.enumType = enumType;
            this.enums = enums;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getRequired() {
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

        public String getEnumValuesAsHtml() {
            CollectionStringBuffer csb = new CollectionStringBuffer("<br/>");
            if (enums != null && enums.size() > 0) {
                for (String e : enums) {
                    csb.append(e);
                }
            }
            return csb.toString();
        }

        public boolean isEnumType() {
            return enumType;
        }

        public Set<String> getEnums() {
            return enums;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            EndpointPath that = (EndpointPath) o;

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
