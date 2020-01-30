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
import java.util.ArrayList;
import java.util.Comparator;
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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.EipModel.EipOptionModel;
import org.apache.camel.tooling.util.JavadocHelper;

/**
 * Process camel-spring's <camelContext> and generate json schema documentation
 */
public class SpringAnnotationProcessorHelper {

    protected void processModelClass(final ProcessingEnvironment processingEnv, final RoundEnvironment roundEnv,
                                     final TypeElement classElement) {
        final String javaTypeName = Strings.canonicalClassName(classElement.getQualifiedName().toString());
        String packageName = javaTypeName.substring(0, javaTypeName.lastIndexOf("."));

        // skip abstract classes
        if (classElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return;
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

        // write json schema
        AnnotationProcessorHelper.processFile(processingEnv, packageName, fileName, writer -> writeJSonSchemeDocumentation(processingEnv, writer, roundEnv, classElement, rootElement, javaTypeName, name));
    }

    protected void writeJSonSchemeDocumentation(ProcessingEnvironment processingEnv, PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement,
                                                XmlRootElement rootElement, String javaTypeName, String modelName) {
        // gather eip information
        EipModel eipModel = findEipModelProperties(processingEnv, roundEnv, classElement, javaTypeName, modelName);

        // collect eip information
        Set<EipOptionModel> eipOptions = new TreeSet<>(new EipOptionComparator(eipModel));
        findClassProperties(processingEnv, writer, roundEnv, eipOptions, classElement, classElement, "", modelName);

        eipOptions.forEach(eipModel::addOption);
        String json = JsonMapper.createParameterJsonSchema(eipModel);
        writer.println(json);
    }

    protected EipModel findEipModelProperties(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement classElement, String javaTypeName, String name) {
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

        if (Strings.isNullOrEmpty(model.getTitle())) {
            model.setTitle(Strings.asTitle(model.getName()));
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

    protected void findClassProperties(ProcessingEnvironment processingEnv, PrintWriter writer, RoundEnvironment roundEnv, Set<EipOptionModel> eipOptions, TypeElement originalClassType,
                                       TypeElement classElement, String prefix, String modelName) {
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

                XmlElements elements = fieldElement.getAnnotation(XmlElements.class);
                if (elements != null) {
                    processElements(processingEnv, roundEnv, classElement, elements, fieldElement, eipOptions, prefix);
                }

                XmlElementRef elementRef = fieldElement.getAnnotation(XmlElementRef.class);
                if (elementRef != null) {
                    processElement(processingEnv, roundEnv, classElement, null, elementRef, fieldElement, eipOptions, prefix);
                }

                XmlElement element = fieldElement.getAnnotation(XmlElement.class);
                if (element != null) {
                    if ("rests".equals(fieldName)) {
                        processRests(roundEnv, classElement, element, fieldElement, fieldName, eipOptions, prefix);
                    } else if ("routes".equals(fieldName)) {
                        processRoutes(roundEnv, classElement, element, fieldElement, fieldName, eipOptions, prefix);
                    } else {
                        processElement(processingEnv, roundEnv, classElement, element, null, fieldElement, eipOptions, prefix);
                    }
                }
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

        name = prefix + name;
        TypeMirror fieldType = fieldElement.asType();
        String fieldTypeName = fieldType.toString();
        TypeElement fieldTypeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeName);

        String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
        String docComment;
        if ("mdcLoggingKeysPattern".equals(fieldName)) {
            docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, fieldElement, "MDCLoggingKeysPattern", name, classElement, true);
        } else {
            docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, true);
        }
        if (Strings.isNullOrEmpty(docComment)) {
            Metadata metadata = fieldElement.getAnnotation(Metadata.class);
            docComment = metadata != null ? metadata.description() : null;
        }
        boolean required = attribute.required();
        // metadata may overrule element required
        required = findRequired(fieldElement, required);

        // gather enums
        Set<String> enums = new TreeSet<>();
        boolean isEnum = fieldTypeElement != null && fieldTypeElement.getKind() == ElementKind.ENUM;
        if (isEnum) {
            TypeElement enumClass = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeElement.asType().toString());
            // find all the enum constants which has the possible enum value
            // that can be used
            List<VariableElement> fields = ElementFilter.fieldsIn(enumClass.getEnclosedElements());
            for (VariableElement var : fields) {
                if (var.getKind() == ElementKind.ENUM_CONSTANT) {
                    String val = var.toString();
                    enums.add(val);
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

        // special for id as its inherited from camel-core
        if ("id".equals(name) && Strings.isNullOrEmpty(docComment)) {
            if ("CamelContextFactoryBean".equals(originalClassType.getSimpleName().toString())) {
                docComment = "Sets the id (name) of this CamelContext";
            } else {
                docComment = "Sets the id of this node";
            }
        }

        EipOptionModel ep = createOption(name, displayName, "attribute", fieldTypeName, required, defaultValue, docComment, deprecated, deprecationNote, isEnum, enums, false, null,
                                     false);
        eipOptions.add(ep);

        return false;
    }

    /**
     * Special for processing an @XmlElement routes field
     */
    private void processRoutes(RoundEnvironment roundEnv, TypeElement originalClassType, XmlElement element, VariableElement fieldElement, String fieldName,
                               Set<EipOptionModel> eipOptions, String prefix) {

        TypeMirror fieldType = fieldElement.asType();
        String fieldTypeName = fieldType.toString();

        Set<String> oneOfTypes = new TreeSet<>();
        oneOfTypes.add("route");

        EipOptionModel ep = createOption("route", "Route", "element", fieldTypeName, false, "", "Contains the Camel routes", false, null, false, null, true, oneOfTypes, false);
        eipOptions.add(ep);
    }

    /**
     * Special for processing an @XmlElement rests field
     */
    private void processRests(RoundEnvironment roundEnv, TypeElement originalClassType, XmlElement element, VariableElement fieldElement, String fieldName,
                              Set<EipOptionModel> eipOptions, String prefix) {

        TypeMirror fieldType = fieldElement.asType();
        String fieldTypeName = fieldType.toString();

        Set<String> oneOfTypes = new TreeSet<>();
        oneOfTypes.add("rest");

        EipOptionModel ep = createOption("rest", "Rest", "element", fieldTypeName, false, "", "Contains the rest services defined using the rest-dsl", false, null, false, null, true,
                                     oneOfTypes, false);
        eipOptions.add(ep);
    }

    private void processElement(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv, TypeElement classElement, XmlElement element, XmlElementRef elementRef,
                                VariableElement fieldElement, Set<EipOptionModel> eipOptions, String prefix) {
        Elements elementUtils = processingEnv.getElementUtils();

        String fieldName;
        fieldName = fieldElement.getSimpleName().toString();
        if (element != null || elementRef != null) {

            String kind = "element";
            String name = element != null ? element.name() : elementRef.name();
            if (Strings.isNullOrEmpty(name) || "##default".equals(name)) {
                name = fieldName;
            }
            name = prefix + name;
            TypeMirror fieldType = fieldElement.asType();
            String fieldTypeName = fieldType.toString();
            TypeElement fieldTypeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeName);

            String defaultValue = findDefaultValue(fieldElement, fieldTypeName);
            String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, true);
            if (Strings.isNullOrEmpty(docComment)) {
                Metadata metadata = fieldElement.getAnnotation(Metadata.class);
                docComment = metadata != null ? metadata.description() : null;
            }
            boolean required = element != null ? element.required() : elementRef.required();
            // metadata may overrule element required
            required = findRequired(fieldElement, required);

            // gather enums
            Set<String> enums = new LinkedHashSet<>();
            boolean isEnum = fieldTypeElement != null && fieldTypeElement.getKind() == ElementKind.ENUM;
            if (isEnum) {
                TypeElement enumClass = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeElement.asType().toString());
                // find all the enum constants which has the possible enum value
                // that can be used
                List<VariableElement> fields = ElementFilter.fieldsIn(enumClass.getEnclosedElements());
                for (VariableElement var : fields) {
                    if (var.getKind() == ElementKind.ENUM_CONSTANT) {
                        String val = var.toString();
                        enums.add(val);
                    }
                }
            }

            // is it a definition/factory-bean type then its a oneOf
            TreeSet oneOfTypes = new TreeSet<String>();
            if (fieldTypeName.endsWith("Definition") || fieldTypeName.endsWith("FactoryBean")) {
                TypeElement definitionClass = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeElement.asType().toString());
                if (definitionClass != null) {
                    XmlRootElement rootElement = definitionClass.getAnnotation(XmlRootElement.class);
                    if (rootElement != null) {
                        String childName = rootElement.name();
                        if (childName != null) {
                            oneOfTypes.add(childName);
                        }
                    }
                }
            } else if (fieldTypeName.endsWith("Definition>") || fieldTypeName.endsWith("FactoryBean>")) {
                // its a list so we need to load the generic type
                String typeName = Strings.between(fieldTypeName, "<", ">");
                TypeElement definitionClass = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, typeName);
                if (definitionClass != null) {
                    XmlRootElement rootElement = definitionClass.getAnnotation(XmlRootElement.class);
                    if (rootElement != null) {
                        String childName = rootElement.name();
                        if (childName != null) {
                            oneOfTypes.add(childName);
                        }
                    }
                }
            }
            boolean oneOf = !oneOfTypes.isEmpty();

            boolean asPredicate = false;
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

            EipOptionModel ep = createOption(name, displayName, kind, fieldTypeName, required, defaultValue, docComment, deprecated, deprecationNote, isEnum, enums, oneOf, oneOfTypes,
                                         asPredicate);
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
            if (Strings.isNullOrEmpty(docComment)) {
                Metadata metadata = fieldElement.getAnnotation(Metadata.class);
                docComment = metadata != null ? metadata.description() : null;
            }
            boolean required = false;
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

            EipOptionModel ep = createOption(name, kind, displayName, fieldTypeName, required, defaultValue, docComment, deprecated, deprecationNote, false, null, true, oneOfTypes,
                                         false);
            eipOptions.add(ep);
        }
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

    private EipOptionModel createOption(String name, String displayName, String kind, String type, boolean required, String defaultValue, String description, boolean deprecated,
                                        String deprecationNote, boolean enumType, Set<String> enums, boolean oneOf, Set<String> oneOfs, boolean asPredicate) {
        EipOptionModel option = new EipOptionModel();
        option.setName(name);
        option.setDisplayName(Strings.isNullOrEmpty(displayName) ? Strings.asTitle(name) : displayName);
        option.setKind(kind);
        option.setRequired(required);
        option.setDefaultValue(defaultValue);
        option.setDescription(JavadocHelper.sanitizeDescription(description, false));
        option.setDeprecated(deprecated);
        option.setDeprecationNote(deprecationNote);
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

        private int weigth(EipOptionModel o) {
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
