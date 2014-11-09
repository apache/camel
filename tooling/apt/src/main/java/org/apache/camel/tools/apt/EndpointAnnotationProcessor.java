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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
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
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

import static org.apache.camel.tools.apt.Strings.canonicalClassName;

/**
 * Processes all Camel {@link UriEndpoint}s and generate json schema and html documentation for the endpoint/component.
 */
@SupportedAnnotationTypes({"org.apache.camel.spi.*"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class EndpointAnnotationProcessor extends AbstractProcessor {
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
            if (!Strings.isNullOrEmpty(scheme)) {
                // write html documentation
                String name = canonicalClassName(classElement.getQualifiedName().toString());
                String packageName = name.substring(0, name.lastIndexOf("."));
                String fileName = scheme + ".html";
                Func1<PrintWriter, Void> handler = new Func1<PrintWriter, Void>() {
                    @Override
                    public Void call(PrintWriter writer) {
                        writeHtmlDocumentation(writer, roundEnv, classElement, uriEndpoint);
                        return null;
                    }
                };
                processFile(packageName, scheme, fileName, handler);

                // write json schema
                fileName = scheme + ".json";
                handler = new Func1<PrintWriter, Void>() {
                    @Override
                    public Void call(PrintWriter writer) {
                        writeJSonSchemeDocumentation(writer, roundEnv, classElement, uriEndpoint);
                        return null;
                    }
                };
                processFile(packageName, scheme, fileName, handler);
            }
        }
    }

    protected void writeHtmlDocumentation(PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement, UriEndpoint uriEndpoint) {
        writer.println("<html>");
        writer.println("<header>");
        String scheme = uriEndpoint.scheme();
        String title = scheme + " endpoint";
        writer.println("<title>" + title  + "</title>");
        writer.println("</header>");
        writer.println("<body>");
        writer.println("<h1>" + title + "</h1>");

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
        String consumerPrefix = Strings.getOrElse(uriEndpoint.consumerPrefix(), "");
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

    protected void writeJSonSchemeDocumentation(PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement, UriEndpoint uriEndpoint) {
        String scheme = uriEndpoint.scheme();

        String classDoc = processingEnv.getElementUtils().getDocComment(classElement);
        if (!Strings.isNullOrEmpty(classDoc)) {
            classDoc = JsonSchemaHelper.sanitizeDescription(classDoc);
        }

        // TODO: add some top details about component scheme name, and documentation

        Set<EndpointOption> endpointOptions = new LinkedHashSet<>();
        findClassProperties(roundEnv, endpointOptions, classElement, "");
        if (!endpointOptions.isEmpty()) {
            String json = createParameterJsonSchema(endpointOptions);
            writer.println(json);
        }
    }


    public String createParameterJsonSchema(Set<EndpointOption> options) {
        StringBuilder buffer = new StringBuilder("{\n  \"properties\": {");
        boolean first = true;
        for (EndpointOption entry : options) {
            if (first) {
                first = false;
            } else {
                buffer.append(",");
            }
            buffer.append("\n    ");
            buffer.append(JsonSchemaHelper.toJson(entry.getName(), entry.getType(), entry.getDocumentation(), entry.isEnumType(), entry.getEnums()));
        }
        buffer.append("\n  }\n}\n");
        return buffer.toString();
    }

    protected void showDocumentationAndFieldInjections(PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement, String prefix) {
        String classDoc = processingEnv.getElementUtils().getDocComment(classElement);
        if (!Strings.isNullOrEmpty(classDoc)) {
            // remove dodgy @version that we may have in class javadoc
            classDoc = classDoc.replaceFirst("\\@version", "");
            classDoc = classDoc.trim();
            writer.println("<p>" + classDoc + "</p>");
        }

        Set<EndpointOption> endpointOptions = new LinkedHashSet<>();
        findClassProperties(roundEnv, endpointOptions, classElement, prefix);
        if (!endpointOptions.isEmpty()) {
            writer.println("<table class='table'>");
            writer.println("  <tr>");
            writer.println("    <th>Name</th>");
            writer.println("    <th>Type</th>");
            writer.println("    <th>Default Value</th>");
            writer.println("    <th>Description</th>");
            writer.println("  </tr>");
            for (EndpointOption option : endpointOptions) {
                writer.println("  <tr>");
                writer.println("    <td>" + option.getName() + "</td>");
                writer.println("    <td>" + option.getType() + "</td>");
                writer.println("    <td>" + option.getDefaultValue() + "</td>");
                writer.println("    <td>" + option.getDocumentationWithNotes() + "</td>");
                writer.println("  </tr>");
            }
            writer.println("</table>");
        }
    }

    protected void findClassProperties(RoundEnvironment roundEnv, Set<EndpointOption> endpointOptions, TypeElement classElement, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();
        while (true) {
            List<VariableElement> fieldElements = ElementFilter.fieldsIn(classElement.getEnclosedElements());
            if (fieldElements.isEmpty()) {
                break;
            }
            for (VariableElement fieldElement : fieldElements) {
                UriParam param = fieldElement.getAnnotation(UriParam.class);
                String fieldName = fieldElement.getSimpleName().toString();
                if (param != null) {
                    String name = param.name();
                    if (Strings.isNullOrEmpty(name)) {
                        name = fieldName;
                    }
                    name = prefix + name;

                    String defaultValue = param.defaultValue();
                    String defaultValueNote = param.defaultValueNote();

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
                        if (!Strings.isNullOrEmpty(extraPrefix)) {
                            nestedPrefix += extraPrefix;
                        }
                        findClassProperties(roundEnv, endpointOptions, fieldTypeElement, nestedPrefix);
                    } else {
                        String docComment = elementUtils.getDocComment(fieldElement);
                        if (Strings.isNullOrEmpty(docComment)) {
                            String setter = "set" + fieldName.substring(0, 1).toUpperCase();
                            if (fieldName.length() > 1) {
                                setter += fieldName.substring(1);
                            }
                            //  lets find the setter
                            List<ExecutableElement> methods = ElementFilter.methodsIn(classElement.getEnclosedElements());
                            for (ExecutableElement method : methods) {
                                String methodName = method.getSimpleName().toString();
                                if (setter.equals(methodName) && method.getParameters().size() == 1) {
                                    String doc = elementUtils.getDocComment(method);
                                    if (!Strings.isNullOrEmpty(doc)) {
                                        docComment = doc;
                                        break;
                                    }
                                }
                            }
                        }
                        if (docComment == null) {
                            docComment = "";
                        }

                        // gather enums
                        Set<String> enums = new LinkedHashSet<>();
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

                        EndpointOption option = new EndpointOption(name, fieldTypeName, defaultValue, defaultValueNote,  docComment.trim(), isEnum, enums);
                        endpointOptions.add(option);
                    }
                }
            }
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

    protected TypeElement findTypeElement(RoundEnvironment roundEnv, String className) {
        if (!Strings.isNullOrEmpty(className) && !"java.lang.Object".equals(className)) {
            Set<? extends Element> rootElements = roundEnv.getRootElements();
            for (Element rootElement : rootElements) {
                if (rootElement instanceof TypeElement) {
                    TypeElement typeElement = (TypeElement) rootElement;
                    String aRootName = canonicalClassName(typeElement.getQualifiedName().toString());
                    if (className.equals(aRootName)) {
                        return typeElement;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Helper method to produce class output text file using the given handler
     */
    protected void processFile(String packageName, String scheme, String fileName, Func1<PrintWriter, Void> handler) {
        PrintWriter writer = null;
        try {
            Writer out;
            Filer filer = processingEnv.getFiler();
            FileObject resource;
            try {
                resource = filer.getResource(StandardLocation.CLASS_OUTPUT, packageName, fileName);
            } catch (Throwable e) {
                resource = filer.createResource(StandardLocation.CLASS_OUTPUT, packageName, fileName);
            }
            URI uri = resource.toUri();
            File file = null;
            if (uri != null) {
                try {
                    file = new File(uri.getPath());
                } catch (Exception e) {
                    warning("Could not convert output directory resource URI to a file " + e);
                }
            }
            if (file == null) {
                warning("No class output directory could be found!");
            } else {
                file.getParentFile().mkdirs();
                out = new FileWriter(file);
                writer = new PrintWriter(out);
                handler.call(writer);
            }
        } catch (IOException e) {
            log(e);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    protected void log(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
    }

    protected void warning(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message);
    }

    protected void error(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
    }

    protected void log(Throwable e) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        e.printStackTrace(writer);
        writer.close();
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, buffer.toString());
    }

    private static final class EndpointOption {

        private String name;
        private String type;
        private String defaultValue;
        private String defaultValueNote;
        private String documentation;
        private boolean enumType;
        private Set<String> enums;

        private EndpointOption(String name, String type, String defaultValue, String defaultValueNote,
                               String documentation, boolean enumType, Set<String> enums) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            this.defaultValueNote = defaultValueNote;
            this.documentation = documentation;
            this.enumType = enumType;
            this.enums = enums;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public String getDocumentation() {
            return documentation;
        }

        public String getDocumentationWithNotes() {
            if (defaultValueNote != null) {
                return documentation + ". Default value notice: " + defaultValueNote;
            }
            return documentation;
        }

        public boolean isEnumType() {
            return enumType;
        }

        public Set<String> getEnums() {
            return enums;
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
}
