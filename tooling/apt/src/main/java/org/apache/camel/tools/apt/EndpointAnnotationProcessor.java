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
import java.util.ArrayList;
import java.util.Collections;
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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.tools.apt.helper.EndpointHelper;
import org.apache.camel.tools.apt.helper.JsonSchemaHelper;
import org.apache.camel.tools.apt.helper.Strings;
import org.apache.camel.tools.apt.model.ComponentModel;
import org.apache.camel.tools.apt.model.ComponentOption;
import org.apache.camel.tools.apt.model.EndpointOption;
import org.apache.camel.tools.apt.model.EndpointPath;

import static org.apache.camel.tools.apt.helper.JsonSchemaHelper.sanitizeDescription;
import static org.apache.camel.tools.apt.helper.Strings.canonicalClassName;
import static org.apache.camel.tools.apt.helper.Strings.getOrElse;
import static org.apache.camel.tools.apt.helper.Strings.isNullOrEmpty;
import static org.apache.camel.tools.apt.helper.Strings.safeNull;

/**
 * Processes all Camel {@link UriEndpoint}s and generate json schema and html documentation for the endpoint/component.
 */
@SupportedAnnotationTypes({"org.apache.camel.spi.*"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class EndpointAnnotationProcessor extends AbstractAnnotationProcessor {

    // CHECKSTYLE:OFF

    private static final String HEADER_FILTER_STRATEGY_JAVADOC = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.";

    public boolean process(Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        try {
            if (roundEnv.processingOver()) {
                return true;
            }
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(UriEndpoint.class);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    processEndpointClass(roundEnv, (TypeElement) element);
                }
            }
        } catch (Throwable e) {
            dumpExceptionToErrorFile("camel-apt-error.log", "Error processing @UriEndpoint", e);
        }
        return true;
    }

    protected void processEndpointClass(final RoundEnvironment roundEnv, final TypeElement classElement) {
        final UriEndpoint uriEndpoint = classElement.getAnnotation(UriEndpoint.class);
        if (uriEndpoint != null) {
            String scheme = uriEndpoint.scheme();
            String extendsScheme = uriEndpoint.extendsScheme();
            String title = uriEndpoint.title();
            final String label = uriEndpoint.label();
            if (!isNullOrEmpty(scheme)) {
                // support multiple schemes separated by comma, which maps to the exact same component
                // for example camel-mail has a bunch of component schema names that does that
                String[] schemes = scheme.split(",");
                String[] titles = title.split(",");
                String[] extendsSchemes = extendsScheme != null ? extendsScheme.split(",") : null;
                for (int i = 0; i < schemes.length; i++) {
                    final String alias = schemes[i];
                    final String extendsAlias = extendsSchemes != null ? (i < extendsSchemes.length ? extendsSchemes[i] : extendsSchemes[0]) : null;
                    final String aliasTitle = i < titles.length ? titles[i] : titles[0];
                    // write html documentation
                    String name = canonicalClassName(classElement.getQualifiedName().toString());
                    String packageName = name.substring(0, name.lastIndexOf("."));
                    String fileName = alias + ".html";
                    Func1<PrintWriter, Void> handler = new Func1<PrintWriter, Void>() {
                        @Override
                        public Void call(PrintWriter writer) {
                            writeHtmlDocumentation(writer, roundEnv, classElement, uriEndpoint, aliasTitle, alias, extendsAlias, label);
                            return null;
                        }
                    };
                    processFile(packageName, fileName, handler);

                    // write json schema
                    fileName = alias + ".json";
                    handler = new Func1<PrintWriter, Void>() {
                        @Override
                        public Void call(PrintWriter writer) {
                            writeJSonSchemeDocumentation(writer, roundEnv, classElement, uriEndpoint, aliasTitle, alias, extendsAlias, label);
                            return null;
                        }
                    };
                    processFile(packageName, fileName, handler);
                }
            }
        }
    }

    protected void writeHtmlDocumentation(PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement, UriEndpoint uriEndpoint,
                                          String title, String scheme, String extendsScheme, String label) {
        // gather component information
        ComponentModel componentModel = findComponentProperties(roundEnv, uriEndpoint, title, scheme, extendsScheme, label);

        String syntax = componentModel.getSyntax();
        String alternativeSyntax = componentModel.getAlternativeSyntax();
        String description = componentModel.getDescription();

        writer.println("<html>");
        writer.println("<header>");
        writer.println("<title>" + title  + "</title>");
        writer.println("</header>");
        writer.println("<body>");
        writer.println("<h1>" + title + "</h1>");
        writer.println("<b>Scheme:</b> " + scheme + "<br/>");
        writer.println("<b>Syntax:</b> " + syntax + "<br/>");
        if (alternativeSyntax != null) {
            writer.println("<b>Alternative Syntax:</b> " + alternativeSyntax + "<br/>");
        }
        writer.println("<b>Description:</b> " + description + "<br/>");
        writer.println("<b>Maven:</b> " + componentModel.getGroupId() + "/" + componentModel.getArtifactId() + "/" + componentModel.getVersionId() + "<br/>");

        writeHtmlDocumentationAndFieldInjections(writer, roundEnv, componentModel, classElement, "", uriEndpoint.excludeProperties());

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
                writeHtmlDocumentationAndFieldInjections(writer, roundEnv, componentModel, consumerElement, consumerPrefix, uriEndpoint.excludeProperties());
                found = true;
            }
        }
        if (!found && consumerClassName != null) {
            warning("APT could not find consumer class " + consumerClassName);
        }
        writer.println("</body>");
        writer.println("</html>");
    }

    protected void writeJSonSchemeDocumentation(PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement, UriEndpoint uriEndpoint,
                                                String title, String scheme, String extendsScheme, String label) {
        // gather component information
        ComponentModel componentModel = findComponentProperties(roundEnv, uriEndpoint, title, scheme, extendsScheme, label);

        // get endpoint information which is divided into paths and options (though there should really only be one path)
        Set<EndpointPath> endpointPaths = new LinkedHashSet<EndpointPath>();
        Set<EndpointOption> endpointOptions = new LinkedHashSet<EndpointOption>();
        Set<ComponentOption> componentOptions = new LinkedHashSet<ComponentOption>();

        TypeElement componentClassElement = findTypeElement(roundEnv, componentModel.getJavaType());
        if (componentClassElement != null) {
            findComponentClassProperties(writer, roundEnv, componentModel, componentOptions, componentClassElement, "");
        }

        findClassProperties(writer, roundEnv, componentModel, endpointPaths, endpointOptions, classElement, "", uriEndpoint.excludeProperties());

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
        if (!Strings.isNullOrEmpty(componentModel.getExtendsScheme())) {
            buffer.append("\n    \"extendsScheme\": \"").append(componentModel.getExtendsScheme()).append("\",");
        }
        buffer.append("\n    \"syntax\": \"").append(componentModel.getSyntax()).append("\",");
        if (componentModel.getAlternativeSyntax() != null) {
            buffer.append("\n    \"alternativeSyntax\": \"").append(componentModel.getAlternativeSyntax()).append("\",");
        }
        buffer.append("\n    \"title\": \"").append(componentModel.getTitle()).append("\",");
        buffer.append("\n    \"description\": \"").append(componentModel.getDescription()).append("\",");
        buffer.append("\n    \"label\": \"").append(getOrElse(componentModel.getLabel(), "")).append("\",");
        if (componentModel.isConsumerOnly()) {
            buffer.append("\n    \"consumerOnly\": \"").append("true").append("\",");
        } else if (componentModel.isProducerOnly()) {
            buffer.append("\n    \"producerOnly\": \"").append("true").append("\",");
        }
        if (componentModel.isLenientProperties()) {
            buffer.append("\n    \"lenientProperties\": \"").append("true").append("\",");
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
            // either we have the documentation from this apt plugin or we need help to find it from extended component
            String doc = entry.getDocumentationWithNotes();
            if (Strings.isNullOrEmpty(doc)) {
                doc = DocumentationHelper.findComponentJavaDoc(componentModel.getScheme(), componentModel.getExtendsScheme(), entry.getName());
            }
            // as its json we need to sanitize the docs
            doc = sanitizeDescription(doc, false);
            Boolean required = entry.getRequired() != null ? Boolean.valueOf(entry.getRequired()) : null;
            String defaultValue = entry.getDefaultValue();
            if (Strings.isNullOrEmpty(defaultValue) && "boolean".equals(entry.getType())) {
                // fallback as false for boolean types
                defaultValue = "false";
            }

            // component options do not have prefix
            String optionalPrefix = "";
            String prefix = "";
            boolean multiValue = false;

            buffer.append(JsonSchemaHelper.toJson(entry.getName(), "property", required, entry.getType(), defaultValue, doc,
                    entry.isDeprecated(), entry.getGroup(), entry.getLabel(), entry.isEnumType(), entry.getEnums(), false, null,
                    optionalPrefix, prefix, multiValue));
        }
        buffer.append("\n  },");

        buffer.append("\n  \"properties\": {");
        first = true;

        // sort the endpoint options in the standard order we prefer
        List<EndpointPath> paths = new ArrayList<EndpointPath>();
        paths.addAll(endpointPaths);
        Collections.sort(paths, EndpointHelper.createPathComparator(componentModel.getSyntax()));

        // include paths in the top
        for (EndpointPath entry : paths) {
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
            // either we have the documentation from this apt plugin or we need help to find it from extended component
            String doc = entry.getDocumentation();
            if (Strings.isNullOrEmpty(doc)) {
                doc = DocumentationHelper.findEndpointJavaDoc(componentModel.getScheme(), componentModel.getExtendsScheme(), entry.getName());
            }
            // as its json we need to sanitize the docs
            doc = sanitizeDescription(doc, false);
            Boolean required = entry.getRequired() != null ? Boolean.valueOf(entry.getRequired()) : null;
            String defaultValue = entry.getDefaultValue();
            if (Strings.isNullOrEmpty(defaultValue) && "boolean".equals(entry.getType())) {
                // fallback as false for boolean types
                defaultValue = "false";
            }

            // @UriPath options do not have prefix
            String optionalPrefix = "";
            String prefix = "";
            boolean multiValue = false;

            buffer.append(JsonSchemaHelper.toJson(entry.getName(), "path", required, entry.getType(), defaultValue, doc,
                    entry.isDeprecated(), entry.getGroup(), entry.getLabel(), entry.isEnumType(), entry.getEnums(), false, null,
                    optionalPrefix, prefix, multiValue));
        }

        // sort the endpoint options in the standard order we prefer
        List<EndpointOption> options = new ArrayList<EndpointOption>();
        options.addAll(endpointOptions);
        Collections.sort(options, EndpointHelper.createGroupAndLabelComparator());

        // and then regular parameter options
        for (EndpointOption entry : options) {
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
            // either we have the documentation from this apt plugin or we need help to find it from extended component
            String doc = entry.getDocumentationWithNotes();
            if (Strings.isNullOrEmpty(doc)) {
                doc = DocumentationHelper.findEndpointJavaDoc(componentModel.getScheme(), componentModel.getExtendsScheme(), entry.getName());
            }
            // as its json we need to sanitize the docs
            doc = sanitizeDescription(doc, false);
            Boolean required = entry.getRequired() != null ? Boolean.valueOf(entry.getRequired()) : null;
            String defaultValue = entry.getDefaultValue();
            if (Strings.isNullOrEmpty(defaultValue) && "boolean".equals(entry.getType())) {
                // fallback as false for boolean types
                defaultValue = "false";
            }
            String optionalPrefix = entry.getOptionalPrefix();
            String prefix = entry.getPrefix();
            boolean multiValue = entry.isMultiValue();

            buffer.append(JsonSchemaHelper.toJson(entry.getName(), "parameter", required, entry.getType(), defaultValue,
                    doc, entry.isDeprecated(), entry.getGroup(), entry.getLabel(), entry.isEnumType(), entry.getEnums(), false, null,
                    optionalPrefix, prefix, multiValue));
        }
        buffer.append("\n  }");

        buffer.append("\n}\n");
        return buffer.toString();
    }

    protected void writeHtmlDocumentationAndFieldInjections(PrintWriter writer, RoundEnvironment roundEnv, ComponentModel componentModel,
                                                            TypeElement classElement, String prefix, String excludeProperties) {
        String classDoc = processingEnv.getElementUtils().getDocComment(classElement);
        if (!isNullOrEmpty(classDoc)) {
            // remove dodgy @version that we may have in class javadoc
            classDoc = classDoc.replaceFirst("\\@version", "");
            classDoc = classDoc.trim();
            writer.println("<p>" + classDoc + "</p>");
        }

        Set<EndpointPath> endpointPaths = new LinkedHashSet<EndpointPath>();
        Set<EndpointOption> endpointOptions = new LinkedHashSet<EndpointOption>();
        findClassProperties(writer, roundEnv, componentModel, endpointPaths, endpointOptions, classElement, prefix, excludeProperties);

        // sort the endpoint options in the standard order we prefer
        List<EndpointPath> paths = new ArrayList<EndpointPath>();
        paths.addAll(endpointPaths);
        Collections.sort(paths, EndpointHelper.createPathComparator(componentModel.getSyntax()));

        // sort the endpoint options in the standard order we prefer
        List<EndpointOption> options = new ArrayList<EndpointOption>();
        options.addAll(endpointOptions);
        Collections.sort(options, EndpointHelper.createGroupAndLabelComparator());

        if (!options.isEmpty() || !paths.isEmpty()) {
            writer.println("<table class='table'>");
            writer.println("  <tr>");
            writer.println("    <th align=\"left\">Name</th>");
            writer.println("    <th align=\"left\">Kind</th>");
            writer.println("    <th align=\"left\">Group</th>");
            writer.println("    <th align=\"left\">Required</th>");
            writer.println("    <th align=\"left\">Default</th>");
            writer.println("    <th align=\"left\">Type</th>");
            writer.println("    <th align=\"left\">Enum</th>");
            writer.println("    <th align=\"left\">Description</th>");
            writer.println("  </tr>");
            // include paths in the top
            for (EndpointPath path : paths) {
                writer.println("  <tr>");
                writer.println("    <td>" + path.getName() + "</td>");
                writer.println("    <td>" + "path" + "</td>");
                writer.println("    <td>" + path.getGroup() + "</td>");
                writer.println("    <td>" + safeNull(path.getRequired()) + "</td>");
                writer.println("    <td>" + path.getDefaultValue() + "</td>");
                writer.println("    <td>" + path.getType() + "</td>");
                writer.println("    <td>" + path.getEnumValuesAsHtml() + "</td>");
                writer.println("    <td>" + path.getDocumentation() + "</td>");
                writer.println("  </tr>");
            }
            // and then regular parameter options
            for (EndpointOption option : options) {
                writer.println("  <tr>");
                writer.println("    <td>" + option.getName() + "</td>");
                writer.println("    <td>" + "parameter" + "</td>");
                writer.println("    <td>" + option.getGroup() + "</td>");
                writer.println("    <td>" + safeNull(option.getRequired()) + "</td>");
                writer.println("    <td>" + option.getDefaultValue() + "</td>");
                writer.println("    <td>" + option.getType() + "</td>");
                writer.println("    <td>" + option.getEnumValuesAsHtml() + "</td>");
                writer.println("    <td>" + option.getDocumentationWithNotes() + "</td>");
                writer.println("  </tr>");
            }
            writer.println("</table>");
        }
    }

    protected ComponentModel findComponentProperties(RoundEnvironment roundEnv, UriEndpoint uriEndpoint,
                                                     String title, String scheme, String extendsScheme, String label) {
        ComponentModel model = new ComponentModel(scheme);

        // if the scheme is an alias then replace the scheme name from the syntax with the alias
        String syntax = scheme + ":" + Strings.after(uriEndpoint.syntax(), ":");
        // alternative syntax is optional
        if (!Strings.isNullOrEmpty(uriEndpoint.alternativeSyntax())) {
            String alternativeSyntax = scheme + ":" + Strings.after(uriEndpoint.alternativeSyntax(), ":");
            model.setAlternativeSyntax(alternativeSyntax);
        }

        model.setExtendsScheme(extendsScheme);
        model.setSyntax(syntax);
        model.setTitle(title);
        model.setLabel(label);
        model.setConsumerOnly(uriEndpoint.consumerOnly());
        model.setProducerOnly(uriEndpoint.producerOnly());
        model.setLenientProperties(uriEndpoint.lenientProperties());

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

    protected void findComponentClassProperties(PrintWriter writer, RoundEnvironment roundEnv, ComponentModel componentModel,
                                                Set<ComponentOption> componentOptions, TypeElement classElement, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();
        while (true) {
            List<ExecutableElement> methods = ElementFilter.methodsIn(classElement.getEnclosedElements());
            for (ExecutableElement method : methods) {
                String methodName = method.getSimpleName().toString();
                boolean deprecated = method.getAnnotation(Deprecated.class) != null;
                Metadata metadata = method.getAnnotation(Metadata.class);

                // must be the setter
                boolean isSetter = methodName.startsWith("set") && method.getParameters().size() == 1 & method.getReturnType().getKind().equals(TypeKind.VOID);
                if (!isSetter) {
                    continue;
                }

                // skip unwanted methods as they are inherited from default component and are not intended for end users to configure
                if ("setEndpointClass".equals(methodName) || "setCamelContext".equals(methodName) || "setEndpointHeaderFilterStrategy".equals(methodName)) {
                    continue;
                }

                // must be a getter/setter pair
                String fieldName = methodName.substring(3);
                fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);

                // we usually favor putting the @Metadata annotation on the field instead of the setter, so try to use it if its there
                VariableElement field = findFieldElement(classElement, fieldName);
                if (field != null && metadata == null) {
                    metadata = field.getAnnotation(Metadata.class);
                }

                String required = metadata != null ? metadata.required() : null;
                String label = metadata != null ? metadata.label() : null;

                // we do not yet have default values / notes / as no annotation support yet
                // String defaultValueNote = param.defaultValueNote();
                String defaultValue = metadata != null ? metadata.defaultValue() : null;
                String defaultValueNote = null;

                ExecutableElement setter = method;
                String name = fieldName;
                name = prefix + name;
                TypeMirror fieldType = setter.getParameters().get(0).asType();
                String fieldTypeName = fieldType.toString();
                TypeElement fieldTypeElement = findTypeElement(roundEnv, fieldTypeName);

                String docComment = findJavaDoc(elementUtils, method, fieldName, name, classElement, false);
                if (isNullOrEmpty(docComment)) {
                    docComment = metadata != null ? metadata.description() : null;
                }
                if (isNullOrEmpty(docComment)) {
                    // apt cannot grab javadoc from camel-core, only from annotations
                    if ("setHeaderFilterStrategy".equals(methodName)) {
                        docComment = HEADER_FILTER_STRATEGY_JAVADOC;
                    } else {
                        docComment = "";
                    }
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

                String group = EndpointHelper.labelAsGroupName(label, componentModel.isConsumerOnly(), componentModel.isProducerOnly());
                ComponentOption option = new ComponentOption(name, fieldTypeName, required, defaultValue, defaultValueNote,
                        docComment.trim(), deprecated, group, label, isEnum, enums);
                componentOptions.add(option);
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

    protected void findClassProperties(PrintWriter writer, RoundEnvironment roundEnv, ComponentModel componentModel,
                                       Set<EndpointPath> endpointPaths, Set<EndpointOption> endpointOptions,
                                       TypeElement classElement, String prefix, String excludeProperties) {
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

                    // should we exclude the name?
                    if (excludeProperty(excludeProperties, name)) {
                        continue;
                    }

                    String defaultValue = path.defaultValue();
                    if (defaultValue == null && metadata != null) {
                        defaultValue = metadata.defaultValue();
                    }
                    String defaultValueNote = path.defaultValueNote();
                    String required = metadata != null ? metadata.required() : null;
                    String label = path.label();
                    if (Strings.isNullOrEmpty(label) && metadata != null) {
                        label = metadata.label();
                    }

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

                    // the field type may be overloaded by another type
                    if (!Strings.isNullOrEmpty(path.javaType())) {
                        fieldTypeName = path.javaType();
                    }

                    String group = EndpointHelper.labelAsGroupName(label, componentModel.isConsumerOnly(), componentModel.isProducerOnly());
                    EndpointPath ep = new EndpointPath(name, fieldTypeName, required, defaultValue, docComment, deprecated, group, label, isEnum, enums);
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

                    // should we exclude the name?
                    if (excludeProperty(excludeProperties, name)) {
                        continue;
                    }

                    String paramOptionalPrefix = param.optionalPrefix();
                    String paramPrefix = param.prefix();
                    boolean multiValue = param.multiValue();
                    String defaultValue = param.defaultValue();
                    if (defaultValue == null && metadata != null) {
                        defaultValue = metadata.defaultValue();
                    }
                    String defaultValueNote = param.defaultValueNote();
                    String required = metadata != null ? metadata.required() : null;
                    String label = param.label();
                    if (Strings.isNullOrEmpty(label) && metadata != null) {
                        label = metadata.label();
                    }

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
                        findClassProperties(writer, roundEnv, componentModel, endpointPaths, endpointOptions, fieldTypeElement, nestedPrefix, excludeProperties);
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

                        // the field type may be overloaded by another type
                        if (!Strings.isNullOrEmpty(param.javaType())) {
                            fieldTypeName = param.javaType();
                        }

                        String group = EndpointHelper.labelAsGroupName(label, componentModel.isConsumerOnly(), componentModel.isProducerOnly());
                        EndpointOption option = new EndpointOption(name, fieldTypeName, required, defaultValue, defaultValueNote,
                                docComment.trim(), paramOptionalPrefix, paramPrefix, multiValue, deprecated, group, label, isEnum, enums);
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

    private static boolean excludeProperty(String excludeProperties, String name) {
        String[] excludes = excludeProperties.split(",");
        for (String exclude : excludes) {
            if (name.equals(exclude)) {
                return true;
            }
        }
        return false;
    }

    protected static Map<String, String> parseAsMap(String data) {
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

    // CHECKSTYLE:ON

}
