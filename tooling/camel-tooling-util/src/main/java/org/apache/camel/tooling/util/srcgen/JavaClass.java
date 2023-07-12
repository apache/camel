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
package org.apache.camel.tooling.util.srcgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class JavaClass {

    ClassLoader classLoader;
    JavaClass parent;
    String packageName;
    String name;
    String extendsName = "java.lang.Object";
    List<String> implementNames = new ArrayList<>();
    List<String> imports = new ArrayList<>();
    List<Annotation> annotations = new ArrayList<>();
    List<Property> properties = new ArrayList<>();
    List<Field> fields = new ArrayList<>();
    List<Method> methods = new ArrayList<>();
    List<JavaClass> nested = new ArrayList<>();
    List<String> values = new ArrayList<>();
    Javadoc javadoc = new Javadoc();
    boolean isStatic;
    boolean isPublic = true;
    boolean isPackagePrivate;
    boolean isAbstract;
    boolean isClass = true;
    boolean isEnum;
    int maxImportPerPackage = 10;

    public JavaClass() {
    }

    public JavaClass(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    protected JavaClass(JavaClass parent) {
        this.parent = parent;
    }

    protected ClassLoader getClassLoader() {
        if (classLoader == null && parent != null) {
            return parent.getClassLoader();
        } else {
            return classLoader;
        }
    }

    public void setMaxImportPerPackage(int maxImportPerPackage) {
        this.maxImportPerPackage = maxImportPerPackage;
    }

    public JavaClass setStatic(boolean aStatic) {
        isStatic = aStatic;
        return this;
    }

    public JavaClass setPackagePrivate() {
        isPublic = false;
        isPackagePrivate = true;
        return this;
    }

    public JavaClass setPublic() {
        isPublic = true;
        isPackagePrivate = false;
        return this;
    }

    public String getPackage() {
        return packageName;
    }

    public JavaClass setPackage(String packageName) {
        this.packageName = packageName;
        return this;
    }

    public String getName() {
        return name;
    }

    public JavaClass setName(String name) {
        this.name = name;
        return this;
    }

    public String getCanonicalName() {
        if (parent != null) {
            return parent.getCanonicalName() + "$" + name;
        } else {
            return packageName + "." + name;
        }
    }

    public JavaClass extendSuperType(JavaClass extend) {
        return extendSuperType(extend.getName());
    }

    public JavaClass extendSuperType(String extendsName) {
        this.extendsName = extendsName;
        return this;
    }

    public String getSuperType() {
        return extendsName;
    }

    public JavaClass implementInterface(String implementName) {
        this.implementNames.add(implementName);
        return this;
    }

    public List<String> getImports() {
        return imports;
    }

    public void addImport(Class<?> clazz) {
        addImport(clazz.getName());
    }

    public void addImport(String importName) {
        this.imports.add(importName);
    }

    public void removeImport(String importName) {
        this.imports.remove(importName);
    }

    public void removeImport(JavaClass importName) {
        removeImport(importName.getCanonicalName());
    }

    public Annotation addAnnotation(String type) {
        try {
            Class<?> cl = getClassLoader().loadClass(type);
            return addAnnotation((Class<? extends java.lang.annotation.Annotation>) cl);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to parse type", e);
        }
    }

    public <A extends java.lang.annotation.Annotation> Annotation addAnnotation(Class<A> type) {
        if (!java.lang.annotation.Annotation.class.isAssignableFrom(type)) {
            throw new IllegalStateException("Not an annotation: " + type.getName());
        }
        Annotation ann = new Annotation(type);
        annotations.add(ann);
        return ann;
    }

    public Property addProperty(String type, String name) {
        try {
            return addProperty(GenericType.parse(type, getClassLoader()), name);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to parse type " + type + " for property " + name, e);
        }
    }

    public Property addProperty(GenericType type, String name) {
        Property prop = new Property(type, name);
        properties.add(prop);
        return prop;
    }

    public Javadoc getJavaDoc() {
        return javadoc;
    }

    public Field addField() {
        Field field = new Field();
        fields.add(field);
        return field;
    }

    public Method addMethod() {
        return addMethod(new Method());
    }

    public Method addMethod(Method method) {
        methods.add(method);
        return method;
    }

    public JavaClass addNestedType() {
        JavaClass clazz = new JavaClass(this);
        nested.add(clazz);
        return clazz;
    }

    public void addValue(String value) {
        values.add(value);
    }

    public boolean isClass() {
        return isClass;
    }

    public JavaClass setClass(boolean isClass) {
        this.isClass = isClass;
        return this;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public JavaClass setAbstract(boolean isAbstract) {
        this.isAbstract = isAbstract;
        return this;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public JavaClass setEnum(boolean isEnum) {
        this.isEnum = isEnum;
        return this;
    }

    public List<Property> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "JavaClass[" + getCanonicalName() + "]";
    }

    public String printClass() {
        return printClass(true);
    }

    public String printClass(boolean innerClassesLast) {
        StringBuilder sb = new StringBuilder();

        Set<String> imports = new TreeSet<>(Comparator.comparing(JavaClass::importOrder));
        imports.addAll(this.imports);
        addImports(imports);
        nested.forEach(jc -> jc.addImports(imports));
        imports.removeIf(f -> f.startsWith("java.lang.") || f.startsWith(packageName + "."));
        imports.removeIf(GenericType::isPrimitive);

        Map<String, List<String>> importsByPackages = new LinkedHashMap<>();
        for (String imp : imports) {
            String key = imp.substring(0, imp.lastIndexOf('.'));
            importsByPackages.computeIfAbsent(key, k -> new ArrayList<>()).add(imp);
        }
        imports.clear();
        for (Map.Entry<String, List<String>> e : importsByPackages.entrySet()) {
            if (e.getValue().size() < maxImportPerPackage) {
                imports.addAll(e.getValue());
            } else {
                imports.add(e.getKey() + ".*");
            }
        }

        sb.append("package ").append(packageName).append(";\n");
        sb.append("\n");
        if (!imports.isEmpty()) {
            for (String imp : imports) {
                sb.append("import ").append(imp).append(";\n");
            }
            sb.append("\n");
        }

        printClass(innerClassesLast, sb, "");
        return sb.toString();
    }

    private void printClass(boolean innerClassesLast, StringBuilder sb, String indent) {
        printJavadoc(sb, indent, javadoc);
        printAnnotations(sb, indent, annotations);

        if (isEnum) {
            sb.append(indent)
                    .append(isPublic ? "public " : "")
                    .append(isStatic ? "static " : "")
                    .append("enum ").append(name).append(" {\n")
                    .append(indent)
                    .append("    ")
                    .append(String.join(",\n" + indent + "    ", values))
                    .append(";\n")
                    .append(indent)
                    .append("}");
            return;

        }

        StringBuilder sb2 = new StringBuilder();
        sb2.append(indent);
        if (isPublic) {
            sb2.append("public ");
        }
        if (isStatic) {
            sb2.append("static ");
        }
        sb2.append(isClass ? "class " : "interface ").append(name);
        if (extendsName != null && !"java.lang.Object".equals(extendsName)) {
            sb2.append(" extends ").append(extendsName);
        }
        if (!implementNames.isEmpty()) {
            sb2.append(isClass ? " implements " : " extends ")
                    .append(String.join(", ", implementNames));
        }
        sb2.append(" {");
        if (sb2.length() < 80) {
            sb.append(sb2).append("\n");
        } else {
            sb.append(indent);
            if (isPublic) {
                sb.append("public ");
            }
            if (isStatic) {
                sb.append("static ");
            }
            sb.append(isClass ? "class " : "interface ").append(name);
            if (extendsName != null && !"java.lang.Object".equals(extendsName)) {
                sb.append("\n");
                sb.append(indent).append("        extends\n");
                sb.append(indent).append("            ").append(extendsName);
            }
            if (!implementNames.isEmpty()) {
                sb.append("\n");
                sb.append(indent).append(isClass ? "        implements\n" : "        extends\n");
                sb.append(
                        implementNames.stream().map(name -> indent + "            " + name).collect(Collectors.joining(",\n")));
            }
            sb.append(" {\n");
        }
        if (parent == null) {
            sb.append("\n");
        }

        for (Field field : fields) {
            printField(sb, indent + "    ", field);
        }
        for (Property property : properties) {
            if (property.field != null) {
                printField(sb, indent + "    ", property.field);
            }
        }

        if (!innerClassesLast) {
            for (JavaClass nest : nested) {
                sb.append("\n");
                nest.printClass(innerClassesLast, sb, indent + "    ");
                sb.append("\n");
            }
        }

        for (Method method : methods) {
            printMethod(sb, indent + "    ", method);
        }

        for (Property property : properties) {
            if (property.accessor != null) {
                printMethod(sb, indent + "    ", property.accessor);
            }
            if (property.mutator != null) {
                printMethod(sb, indent + "    ", property.mutator);
            }
        }

        if (innerClassesLast) {
            for (JavaClass nest : nested) {
                sb.append("\n");
                nest.printClass(innerClassesLast, sb, indent + "    ");
                sb.append("\n");
            }
        }

        sb.append(indent).append("}");
    }

    private void addImports(Set<String> imports) {
        annotations.forEach(ann -> addImports(imports, ann));
        fields.forEach(f -> addImports(imports, f));
        methods.forEach(m -> addImports(imports, m));
        properties.forEach(p -> addImports(imports, p));
    }

    private void addImports(Set<String> imports, Annotation annotation) {
        addImports(imports, annotation.type);
    }

    private void addImports(Set<String> imports, Property property) {
        addImports(imports, property.field);
        addImports(imports, property.accessor);
        addImports(imports, property.mutator);
    }

    private void addImports(Set<String> imports, Field field) {
        if (field != null) {
            field.annotations.forEach(a -> addImports(imports, a));
            addImports(imports, field.type);
        }
    }

    private void addImports(Set<String> imports, Method method) {
        if (method != null) {
            method.annotations.forEach(a -> addImports(imports, a));
            addImports(imports, method.returnType);
            method.parameters.forEach(p -> addImports(imports, p.type));
        }
    }

    private void addImports(Set<String> imports, GenericType type) {
        if (type != null) {
            addImports(imports, type.getRawClass());
            for (int i = 0; i < type.size(); i++) {
                addImports(imports, type.getActualTypeArgument(i));
            }
        }
    }

    private void addImports(Set<String> imports, Class<?> clazz) {
        if (clazz != null) {
            if (clazz.isArray()) {
                addImports(imports, clazz.getComponentType());
            } else {
                imports.add(clazz.getName().replace('$', '.'));
            }
        }
    }

    private void printMethod(StringBuilder sb, String indent, Method method) {
        if (fields.size() + properties.size() > 0) {
            sb.append("\n");
        }
        if (method.javadoc.text != null) {
            printJavadoc(sb, indent, method.javadoc);
        }
        printAnnotations(sb, indent, method.annotations);

        if (method.signature != null) {
            sb.append(indent);
            sb.append(method.signature);
            if (!method.isAbstract) {
                sb.append(" {");
            }
        } else {
            StringBuilder sb2 = new StringBuilder();
            sb2.append(indent);
            if (method.isPublic) {
                sb2.append("public ");
            } else if (method.isProtected) {
                sb2.append("protected ");
            } else if (method.isPrivate) {
                sb2.append("private ");
            }
            if (method.isDefault) {
                sb2.append("default ");
            }
            if (method.isStatic) {
                sb2.append("static ");
            }
            if (!method.isConstructor) {
                if (method.returnTypeLiteral != null) {
                    sb2.append(method.returnTypeLiteral);
                } else if (method.returnType != null) {
                    sb2.append(shortName(method.returnType));
                } else {
                    sb2.append("void");
                }
                sb2.append(" ");
            }
            sb2.append(method.name);
            sb2.append("(");
            sb2.append(method.parameters.stream()
                    .map(p -> p.vararg
                            ? typeOf(p) + "... " + p.name
                            : typeOf(p) + " " + p.name)
                    .collect(Collectors.joining(", ")));

            sb2.append(") ");
            if (!method.exceptions.isEmpty()) {
                sb2.append("throws ");
                sb2.append(method.exceptions.stream().map(this::shortName).collect(Collectors.joining(", ", "", " ")));
            }
            if (!method.isAbstract) {
                sb2.append("{");
            }
            if (sb2.length() < 84) {
                sb.append(sb2);
            } else {
                sb.append(indent);
                if (method.isPublic) {
                    sb.append("public ");
                } else if (method.isProtected) {
                    sb.append("protected ");
                } else if (method.isPrivate) {
                    sb.append("private ");
                }
                if (method.isStatic) {
                    sb.append("static ");
                }
                if (method.isDefault) {
                    sb.append("default ");
                }
                if (!method.isConstructor) {
                    if (method.returnTypeLiteral != null) {
                        sb.append(method.returnTypeLiteral);
                    } else if (method.returnType != null) {
                        sb.append(shortName(method.returnType));
                    } else {
                        sb.append("void");
                    }
                    sb.append(" ");
                }
                sb.append(method.name);
                if (!method.parameters.isEmpty()) {
                    sb.append("(\n");
                    sb.append(method.parameters.stream()
                            .map(p -> p.vararg
                                    ? indent + "        " + typeOf(p) + "... " + p.name
                                    : indent + "        " + typeOf(p) + " " + p.name)
                            .collect(Collectors.joining(",\n")));
                    sb.append(")");
                } else {
                    sb.append("()");
                }
                if (!method.exceptions.isEmpty()) {
                    sb.append("\n            throws");
                    sb.append(method.exceptions.stream().map(this::shortName).collect(Collectors.joining(", ", " ", "")));
                }
                if (!method.isAbstract) {
                    sb.append(" {");
                }
            }
        }
        if (!method.isAbstract) {
            sb.append("\n");
            for (String l : method.body.split("\n")) {
                sb.append(indent);
                sb.append("    ");
                sb.append(l);
                sb.append("\n");
            }
            sb.append(indent).append("}\n");
        } else {
            sb.append(";\n");
        }
    }

    private void printField(StringBuilder sb, String indent, Field field) {
        if (field.javadoc.text != null) {
            printJavadoc(sb, indent, field.javadoc);
        }
        if (field.comment != null) {
            printComment(sb, indent, field.comment);
        }
        printAnnotations(sb, indent, field.annotations);
        sb.append(indent);
        if (field.isPublic) {
            sb.append("public ");
        } else if (field.isPrivate) {
            sb.append("private ");
        }
        if (field.isStatic) {
            sb.append("static ");
        }
        if (field.isFinal) {
            sb.append("final ");
        }
        sb.append(shortName(field.type));
        sb.append(" ");
        sb.append(field.name);
        if (field.literalInit != null) {
            sb.append(" = ");
            sb.append(field.literalInit);
        }
        sb.append(";\n");
    }

    private void printJavadoc(StringBuilder sb, String indent, Javadoc doc) {
        List<String> lines = formatJavadocOrCommentStringAsList(doc.text, indent);
        if (!lines.isEmpty()) {
            sb.append(indent).append("/**\n");
            for (String line : lines) {
                sb.append(indent).append(" * ").append(line).append("\n");
            }
            sb.append(indent).append(" */\n");
        }
    }

    private void printComment(StringBuilder stringBuilder, String indent, String comment) {
        List<String> lines = formatJavadocOrCommentStringAsList(comment, indent);
        if (!lines.isEmpty()) {
            for (String line : lines) {
                stringBuilder.append(indent).append("// ").append(line).append("\n");
            }
        }
    }

    private List<String> formatJavadocOrCommentStringAsList(String text, String indent) {
        List<String> lines = new ArrayList<>();
        int len = 78 - indent.length();

        String rem = text;

        if (rem != null) {
            while (rem.length() > 0) {
                int idx = rem.length() >= len ? rem.substring(0, len).lastIndexOf(' ') : -1;
                int idx2 = rem.indexOf('\n');
                if (idx2 >= 0 && (idx < 0 || idx2 < idx || idx2 < len)) {
                    idx = idx2;
                }
                if (idx >= 0) {
                    String s = rem.substring(0, idx);
                    while (s.endsWith(" ")) {
                        s = s.substring(0, s.length() - 1);
                    }
                    String l = rem.substring(idx + 1);
                    while (l.startsWith(" ")) {
                        l = l.substring(1);
                    }
                    lines.add(s);
                    rem = l;
                } else {
                    lines.add(rem);
                    rem = "";
                }
            }
        }

        return lines;
    }

    private void printAnnotations(StringBuilder sb, String indent, List<Annotation> anns) {
        if (anns != null) {
            for (Annotation ann : anns) {
                sb.append(indent);
                sb.append("@");
                sb.append(shortName(ann.type.getName()));
                if (!ann.values.isEmpty()) {
                    sb.append("(");
                    int i = 0;
                    for (Map.Entry<String, String> e : ann.values.entrySet()) {
                        if (i++ > 0) {
                            sb.append(", ");
                        }
                        if (Objects.equals(e.getKey(), "value") && ann.values.size() == 1) {
                            sb.append(e.getValue());
                        } else {
                            sb.append(e.getKey()).append(" = ").append(e.getValue());
                        }
                    }
                    sb.append(")");
                }
                sb.append("\n");
            }
        }
    }

    private String typeOf(Param p) {
        return p.typeLiteral != null ? p.typeLiteral : shortName(p.type);
    }

    private String shortName(GenericType name) {
        return shortName(name.toString());
    }

    private String shortName(String name) {
        String s = name.replace('$', '.');
        //        int idx = s.lastIndexOf('.');
        //        return idx > 0 ? s.substring(idx + 1) : s;
        s = s.replaceAll("([a-z][a-z0-9]+\\.([a-z][a-z0-9_]+\\.)*([A-Z][a-zA-Z0-9_]+\\.)?)([A-za-z]+)", "$4");
        if (s.startsWith(this.name + ".")) {
            s = s.substring(this.name.length() + 1);
        }
        return s;
    }

    private static String importOrder(String s1) {
        // java comes first
        if (s1.startsWith("java.")) {
            s1 = "___" + s1;
        }
        // then javax comes next
        if (s1.startsWith("javax.")) {
            s1 = "__" + s1;
        }
        // org.w3c is for some odd reason also before others
        if (s1.startsWith("org.w3c.")) {
            s1 = "_" + s1;
        }
        return s1;
    }

}
