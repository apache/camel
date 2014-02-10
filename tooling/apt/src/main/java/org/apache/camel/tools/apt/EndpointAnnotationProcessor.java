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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
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
import org.apache.camel.tools.apt.util.Func1;
import org.apache.camel.tools.apt.util.Strings;

import static org.apache.camel.tools.apt.util.Strings.canonicalClassName;

/**
 * Processes all Camel endpoints
 */
//@SupportedOptions({"foo"})
@SupportedAnnotationTypes({"org.apache.camel.spi.*"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
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
            }
        }
    }

    protected void writeHtmlDocumentation(PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement, UriEndpoint uriEndpoint) {
        writer.println("<html>");
        writer.println("<header>");
        String scheme = uriEndpoint.scheme();
        String title = scheme + " endpoint";
        writer.println("<title>" + "</title>");
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


    protected void showDocumentationAndFieldInjections(PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement, String prefix) {
        String classDoc = processingEnv.getElementUtils().getDocComment(classElement);
        if (!Strings.isNullOrEmpty(classDoc)) {
            writer.println("<p>" + classDoc.trim() + "</p>");
        }

        SortedMap<String, List<String>> sortedMap = new TreeMap<String, List<String>>();
        findClassProperties(roundEnv, sortedMap, classElement, prefix);
        if (!sortedMap.isEmpty()) {
            writer.println("<table class='table'>");
            writer.println("  <tr>");
            writer.println("    <th>Name</th>");
            writer.println("    <th>Type</th>");
            writer.println("    <th>Description</th>");
            // see defaultValue above
            // writer.println("    <th>Default Value</th>");
            writer.println("  </tr>");
            Set<Map.Entry<String, List<String>>> entries = sortedMap.entrySet();
            for (Map.Entry<String, List<String>> entry : entries) {
                String name = entry.getKey();
                List<String> values = entry.getValue();
                writer.println("  <tr>");
                writer.println("    <td>" + name + "</td>");
                for (String value : values) {
                    writer.println(value);
                }
                writer.println("  </tr>");
            }
            writer.println("</table>");
        }
    }

    protected void findClassProperties(RoundEnvironment roundEnv, SortedMap<String, List<String>> sortedMap, TypeElement classElement, String prefix) {
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
                        findClassProperties(roundEnv, sortedMap, fieldTypeElement, nestedPrefix);
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
                        List<String> values = new ArrayList<String>();
                        values.add("    <td>" + fieldTypeName + "</td>");
                        values.add("    <td>" + docComment.trim() + "</td>");

                        // TODO would be nice here to create a default endpoint/consumer object
                        // and return the default value of the field so we can put it into the docs
                        Object defaultValue = null;
                        if (defaultValue != null) {
                            values.add("    <td>" + defaultValue + "</td>");
                        }
                        if (sortedMap.containsKey(name)) {
                            error("Duplicate parameter annotation named '" + name + "' on class " + classElement.getQualifiedName());
                        } else {
                            sortedMap.put(name, values);
                        }
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
            Writer out = null;
            Filer filer = processingEnv.getFiler();
            FileObject resource;
            try {
                resource = filer.getResource(StandardLocation.CLASS_OUTPUT, packageName, fileName);
            } catch (Throwable e) {
                //resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "org.apache.camel", "CamelAPT2.txt", rootElements.toArray(new Element[rootElements.size()]));
                resource = filer.createResource(StandardLocation.CLASS_OUTPUT, packageName, fileName, new Element[0]);
            }
            URI uri = resource.toUri();
            File file = null;
            if (uri != null) {
                try {
                    file = new File(uri);
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
}
