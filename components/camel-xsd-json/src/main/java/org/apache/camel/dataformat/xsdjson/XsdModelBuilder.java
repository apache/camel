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
package org.apache.camel.dataformat.xsdjson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.camel.dataformat.xsdjson.XsdModel.Attribute;
import org.apache.camel.dataformat.xsdjson.XsdModel.Element;
import org.apache.camel.dataformat.xsdjson.XsdModel.Group;
import org.apache.camel.dataformat.xsdjson.XsdModel.GroupKind;
import org.apache.camel.dataformat.xsdjson.XsdModel.SimpleKind;
import org.apache.camel.dataformat.xsdjson.XsdModel.SimpleType;
import org.apache.camel.dataformat.xsdjson.XsdModel.Type;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaAll;
import org.apache.ws.commons.schema.XmlSchemaAllMember;
import org.apache.ws.commons.schema.XmlSchemaAny;
import org.apache.ws.commons.schema.XmlSchemaAnyAttribute;
import org.apache.ws.commons.schema.XmlSchemaAttribute;
import org.apache.ws.commons.schema.XmlSchemaAttributeGroup;
import org.apache.ws.commons.schema.XmlSchemaAttributeGroupRef;
import org.apache.ws.commons.schema.XmlSchemaChoice;
import org.apache.ws.commons.schema.XmlSchemaChoiceMember;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaComplexContent;
import org.apache.ws.commons.schema.XmlSchemaComplexContentExtension;
import org.apache.ws.commons.schema.XmlSchemaComplexContentRestriction;
import org.apache.ws.commons.schema.XmlSchemaComplexType;
import org.apache.ws.commons.schema.XmlSchemaContent;
import org.apache.ws.commons.schema.XmlSchemaContentModel;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaEnumerationFacet;
import org.apache.ws.commons.schema.XmlSchemaFacet;
import org.apache.ws.commons.schema.XmlSchemaForm;
import org.apache.ws.commons.schema.XmlSchemaGroup;
import org.apache.ws.commons.schema.XmlSchemaGroupParticle;
import org.apache.ws.commons.schema.XmlSchemaGroupRef;
import org.apache.ws.commons.schema.XmlSchemaParticle;
import org.apache.ws.commons.schema.XmlSchemaSequence;
import org.apache.ws.commons.schema.XmlSchemaSequenceMember;
import org.apache.ws.commons.schema.XmlSchemaSimpleContent;
import org.apache.ws.commons.schema.XmlSchemaSimpleContentExtension;
import org.apache.ws.commons.schema.XmlSchemaSimpleContentRestriction;
import org.apache.ws.commons.schema.XmlSchemaSimpleType;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeContent;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeList;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeRestriction;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeUnion;
import org.apache.ws.commons.schema.XmlSchemaType;
import org.apache.ws.commons.schema.XmlSchemaUse;
import org.apache.ws.commons.schema.utils.NamespacePrefixList;

import static org.apache.camel.dataformat.xsdjson.XsdSchemaLoader.XSD_NS;

/**
 * Compiles an {@link XmlSchemaCollection} into an {@link XsdModel}.
 * <p/>
 * A construct that phase 1 does not map (xs:any content, xs:anyType, mixed content, abstract types and elements) does
 * not fail the build: it is recorded on the element or type, and the mapping fails with a message naming it when a
 * message reaches it. So a large WSDL can be used for the elements that are supported.
 */
final class XsdModelBuilder {

    private static final SimpleType STRING = new SimpleType(SimpleKind.STRING, false, "string", null);
    private static final int MAX_TYPE_DEPTH = 64;

    private final XmlSchemaCollection collection;
    private final String attributePrefix;
    private final String textName;
    private final Map<XmlSchemaType, Type> types = new IdentityHashMap<>();
    private final List<Type> compiled = new ArrayList<>();

    XsdModelBuilder(XmlSchemaCollection collection, String attributePrefix, String textName) {
        this.collection = collection;
        this.attributePrefix = attributePrefix != null ? attributePrefix : "";
        this.textName = textName;
    }

    XsdModel build() {
        Map<QName, Element> globals = new LinkedHashMap<>();
        Map<QName, Element> substitutes = new LinkedHashMap<>();
        Map<String, String> prefixes = new LinkedHashMap<>();
        for (XmlSchema schema : collection.getXmlSchemas()) {
            String tns = schema.getTargetNamespace();
            if (XSD_NS.equals(tns)) {
                // the built-in types
                continue;
            }
            assignPrefix(schema, prefixes);
            for (XmlSchemaElement e : schema.getElements().values()) {
                Element element = element(e, false, false);
                globals.putIfAbsent(element.name, element);
                if (e.getSubstitutionGroup() != null) {
                    substitutes.put(element.name, element);
                }
            }
        }
        // the children and attributes are known now, also of types that were in progress when first referenced
        for (Type type : compiled) {
            index(type);
        }
        return new XsdModel(globals, substitutes, prefixes);
    }

    /**
     * The prefix for the target namespace when writing XML: the one the schema declares for it, else ns1, ns2 ...
     */
    private static void assignPrefix(XmlSchema schema, Map<String, String> prefixes) {
        String tns = schema.getTargetNamespace();
        if (tns == null || tns.isEmpty() || prefixes.containsKey(tns)) {
            return;
        }
        String prefix = null;
        NamespacePrefixList context = schema.getNamespaceContext();
        if (context != null) {
            for (String p : context.getDeclaredPrefixes()) {
                if (!p.isEmpty() && !p.toLowerCase(Locale.ROOT).startsWith("xml") && tns.equals(context.getNamespaceURI(p))
                        && !prefixes.containsValue(p)) {
                    prefix = p;
                    break;
                }
            }
        }
        if (prefix == null) {
            int i = prefixes.size() + 1;
            while (prefixes.containsValue("ns" + i)) {
                i++;
            }
            prefix = "ns" + i;
        }
        prefixes.put(tns, prefix);
    }

    private Element element(XmlSchemaElement particle, boolean inRepeat, boolean inOptional) {
        boolean array = inRepeat || particle.getMaxOccurs() > 1;
        boolean optional = inOptional || particle.getMinOccurs() == 0;
        XmlSchemaElement target = particle;
        if (particle.isRef()) {
            target = particle.getRef().getTarget();
            if (target == null) {
                QName name = particle.getRef().getTargetQName();
                return new Element(
                        name, null, array, optional, false,
                        "the element reference " + name + " cannot be resolved (is an xs:import missing?)");
            }
        }
        QName name;
        if (particle.isRef() || target.isTopLevel()) {
            name = target.getQName();
        } else {
            String tns = namespace(target.getParent());
            boolean qualified = target.getForm() == XmlSchemaForm.QUALIFIED
                    || target.getForm() != XmlSchemaForm.UNQUALIFIED
                            && target.getParent().getElementFormDefault() == XmlSchemaForm.QUALIFIED;
            name = new QName(qualified ? tns : "", target.getName());
        }
        String unsupported = null;
        if (target.isAbstract()) {
            unsupported = "the element " + name + " is abstract, and substitution groups are not supported";
        }
        Type type = null;
        XmlSchemaType schemaType = target.getSchemaType();
        if (schemaType == null && target.getSchemaTypeName() != null) {
            schemaType = collection.getTypeByQName(target.getSchemaTypeName());
        }
        if (schemaType != null) {
            type = type(schemaType);
        } else if (target.getSchemaTypeName() != null) {
            unsupported = "the type " + target.getSchemaTypeName() + " of the element " + name
                          + " cannot be resolved (is an xs:import missing?)";
        } else {
            unsupported = "the element " + name + " has no type, and xs:anyType content is not supported";
        }
        return new Element(name, type, array, optional, target.isNillable(), unsupported);
    }

    private Type type(XmlSchemaType schemaType) {
        Type type = types.get(schemaType);
        if (type != null) {
            return type;
        }
        type = new Type(schemaType.getQName());
        // registered before its content is compiled, so recursive types end
        types.put(schemaType, type);
        compiled.add(type);
        QName name = schemaType.getQName();
        if (name != null && XSD_NS.equals(name.getNamespaceURI()) && "anyType".equals(name.getLocalPart())) {
            type.complex = true;
            type.unsupported = "xs:anyType content is not supported";
        } else if (schemaType instanceof XmlSchemaSimpleType simpleType) {
            type.simple = simpleType(simpleType, 0);
        } else if (schemaType instanceof XmlSchemaComplexType complexType) {
            complexType(complexType, type);
        } else {
            type.unsupported = "the type " + type.displayName() + " is not supported";
        }
        return type;
    }

    private void complexType(XmlSchemaComplexType ct, Type type) {
        type.complex = true;
        if (ct.isAbstract()) {
            type.unsupported = "the type " + type.displayName() + " is abstract, and xsi:type is not supported";
        }
        if (ct.isMixed()) {
            type.unsupported = "the type " + type.displayName() + " has mixed content, which is not supported";
        }
        Map<String, Attribute> attributes = new LinkedHashMap<>();
        XmlSchemaContentModel contentModel = ct.getContentModel();
        if (contentModel == null) {
            type.content = group(ct.getParticle(), type, false, false);
            addAttributes(type, attributes, ct.getAttributes(), ct.getAnyAttribute(), 0);
        } else if (contentModel instanceof XmlSchemaSimpleContent simpleContent) {
            XmlSchemaContent content = simpleContent.getContent();
            if (content instanceof XmlSchemaSimpleContentExtension ext) {
                Type base = baseType(ext.getBaseTypeName(), type);
                inherit(type, base, attributes);
                type.simple = base.simple != null ? base.simple : STRING;
                addAttributes(type, attributes, ext.getAttributes(), ext.getAnyAttribute(), 0);
            } else if (content instanceof XmlSchemaSimpleContentRestriction r) {
                Type base = baseType(r.getBaseTypeName(), type);
                inherit(type, base, attributes);
                if (r.getBaseType() != null) {
                    type.simple = simpleType(r.getBaseType(), 0);
                } else {
                    type.simple = base.simple != null ? base.simple : STRING;
                }
                addAttributes(type, attributes, r.getAttributes(), r.getAnyAttribute(), 0);
            }
        } else if (contentModel instanceof XmlSchemaComplexContent complexContent) {
            if (complexContent.isMixed()) {
                type.unsupported = "the type " + type.displayName() + " has mixed content, which is not supported";
            }
            XmlSchemaContent content = complexContent.getContent();
            if (content instanceof XmlSchemaComplexContentExtension ext) {
                Type base = baseType(ext.getBaseTypeName(), type);
                inherit(type, base, attributes);
                Group own = group(ext.getParticle(), type, false, false);
                if (base.content == null) {
                    type.content = own;
                } else if (own == null) {
                    type.content = base.content;
                } else {
                    // an extension is the content of the base type followed by its own
                    Group sequence = new Group(GroupKind.SEQUENCE, false);
                    sequence.members.add(base.content);
                    sequence.members.add(own);
                    type.content = sequence;
                }
                addAttributes(type, attributes, ext.getAttributes(), ext.getAnyAttribute(), 0);
            } else if (content instanceof XmlSchemaComplexContentRestriction r) {
                Type base = baseType(r.getBaseTypeName(), type);
                // a restriction restates the content; the attributes of the base apply unless prohibited
                inherit(type, base, attributes);
                type.wildcard = false;
                type.content = group(r.getParticle(), type, false, false);
                addAttributes(type, attributes, r.getAttributes(), r.getAnyAttribute(), 0);
            }
        }
        type.attributes.addAll(attributes.values());
    }

    private static void inherit(Type type, Type base, Map<String, Attribute> attributes) {
        for (Attribute a : base.attributes) {
            attributes.put(a.name.getLocalPart(), a);
        }
        type.wildcard |= base.wildcard;
        type.anyAttribute |= base.anyAttribute;
        if (type.unsupported == null) {
            type.unsupported = base.unsupported;
        }
    }

    private Type baseType(QName name, Type type) {
        if (name == null || XSD_NS.equals(name.getNamespaceURI()) && "anyType".equals(name.getLocalPart())) {
            Type empty = new Type(name);
            empty.complex = true;
            return empty;
        }
        XmlSchemaType schemaType = collection.getTypeByQName(name);
        if (schemaType == null) {
            Type missing = new Type(name);
            missing.unsupported = "the base type " + name + " of the type " + type.displayName()
                                  + " cannot be resolved (is an xs:import missing?)";
            return missing;
        }
        return type(schemaType);
    }

    private Group group(XmlSchemaParticle particle, Type owner, boolean inRepeat, boolean inOptional) {
        if (particle == null) {
            return null;
        }
        if (particle instanceof XmlSchemaElement e) {
            Group group = new Group(GroupKind.SEQUENCE, false);
            group.members.add(element(e, inRepeat, inOptional));
            return group;
        }
        if (particle instanceof XmlSchemaAny) {
            owner.wildcard = true;
            return null;
        }
        boolean repeat = inRepeat || particle.getMaxOccurs() > 1;
        boolean optional = inOptional || particle.getMinOccurs() == 0;
        if (particle instanceof XmlSchemaGroupRef ref) {
            XmlSchemaGroupParticle target = ref.getParticle();
            if (target == null && ref.getRefName() != null) {
                XmlSchemaGroup g = collection.getGroupByQName(ref.getRefName());
                target = g != null ? g.getParticle() : null;
            }
            if (target == null) {
                owner.unsupported = "the group " + ref.getRefName() + " cannot be resolved (is an xs:import missing?)";
                return null;
            }
            return group(target, owner, repeat, optional);
        }
        if (particle instanceof XmlSchemaSequence sequence) {
            Group group = new Group(GroupKind.SEQUENCE, repeat);
            for (XmlSchemaSequenceMember m : sequence.getItems()) {
                member(group, m, owner, repeat, optional);
            }
            return group;
        }
        if (particle instanceof XmlSchemaChoice choice) {
            Group group = new Group(GroupKind.CHOICE, repeat);
            for (XmlSchemaChoiceMember m : choice.getItems()) {
                // only one branch of a choice is present
                member(group, m, owner, repeat, true);
            }
            return group;
        }
        if (particle instanceof XmlSchemaAll all) {
            Group group = new Group(GroupKind.ALL, repeat);
            for (XmlSchemaAllMember m : all.getItems()) {
                member(group, m, owner, repeat, optional);
            }
            return group;
        }
        return null;
    }

    private void member(Group group, Object member, Type owner, boolean repeat, boolean optional) {
        if (member instanceof XmlSchemaElement e) {
            group.members.add(element(e, repeat, optional));
        } else if (member instanceof XmlSchemaAny) {
            owner.wildcard = true;
        } else if (member instanceof XmlSchemaParticle p) {
            Group nested = group(p, owner, repeat, optional);
            if (nested != null) {
                group.members.add(nested);
            }
        }
    }

    private void addAttributes(
            Type type, Map<String, Attribute> attributes, List<?> members, XmlSchemaAnyAttribute anyAttribute, int depth) {
        if (anyAttribute != null) {
            type.anyAttribute = true;
        }
        if (members == null || depth > MAX_TYPE_DEPTH) {
            return;
        }
        for (Object member : members) {
            if (member instanceof XmlSchemaAttribute a) {
                XmlSchemaAttribute target = a.isRef() ? a.getRef().getTarget() : a;
                QName name;
                if (a.isRef()) {
                    name = target != null ? target.getQName() : a.getRef().getTargetQName();
                } else if (a.isTopLevel()) {
                    name = a.getQName();
                } else {
                    String tns = namespace(a.getParent());
                    boolean qualified = a.getForm() == XmlSchemaForm.QUALIFIED
                            || a.getForm() != XmlSchemaForm.UNQUALIFIED
                                    && a.getParent().getAttributeFormDefault() == XmlSchemaForm.QUALIFIED;
                    name = new QName(qualified ? tns : "", a.getName());
                }
                if (a.getUse() == XmlSchemaUse.PROHIBITED) {
                    attributes.remove(name.getLocalPart());
                    continue;
                }
                SimpleType simple = STRING;
                if (target != null && target.getSchemaType() != null) {
                    simple = simpleType(target.getSchemaType(), 0);
                } else if (target != null && target.getSchemaTypeName() != null) {
                    simple = simpleType(target.getSchemaTypeName(), 0);
                }
                Attribute previous = attributes.put(name.getLocalPart(),
                        new Attribute(name, attributePrefix + name.getLocalPart(), simple));
                if (previous != null && !previous.name.equals(name)) {
                    type.conflict = "The type " + type.displayName() + " has two attributes with the local name "
                                    + name.getLocalPart() + " (" + previous.name + " and " + name
                                    + "), which JSON cannot tell apart";
                }
            } else if (member instanceof XmlSchemaAttributeGroupRef ref) {
                XmlSchemaAttributeGroup target = ref.getRef().getTarget();
                if (target == null) {
                    type.unsupported = "the attribute group " + ref.getRef().getTargetQName()
                                       + " cannot be resolved (is an xs:import missing?)";
                } else {
                    addAttributes(type, attributes, target.getAttributes(), target.getAnyAttribute(), depth + 1);
                }
            } else if (member instanceof XmlSchemaAttributeGroup group) {
                addAttributes(type, attributes, group.getAttributes(), group.getAnyAttribute(), depth + 1);
            }
        }
    }

    private SimpleType simpleType(QName name, int depth) {
        if (name == null) {
            return STRING;
        }
        if (XSD_NS.equals(name.getNamespaceURI())) {
            return builtin(name.getLocalPart());
        }
        XmlSchemaType t = collection.getTypeByQName(name);
        return t instanceof XmlSchemaSimpleType st ? simpleType(st, depth + 1) : STRING;
    }

    private SimpleType simpleType(XmlSchemaSimpleType st, int depth) {
        QName name = st.getQName();
        if (name != null && XSD_NS.equals(name.getNamespaceURI())) {
            return builtin(name.getLocalPart());
        }
        if (depth > MAX_TYPE_DEPTH) {
            return STRING;
        }
        XmlSchemaSimpleTypeContent content = st.getContent();
        if (content instanceof XmlSchemaSimpleTypeRestriction r) {
            SimpleType base = r.getBaseType() != null
                    ? simpleType(r.getBaseType(), depth + 1) : simpleType(r.getBaseTypeName(), depth + 1);
            // the enumeration of the most derived type that has one
            for (XmlSchemaFacet facet : r.getFacets()) {
                if (facet instanceof XmlSchemaEnumerationFacet e && e.getValue() != null) {
                    return base.withEnumeration(e.getValue().toString());
                }
            }
            return base;
        } else if (content instanceof XmlSchemaSimpleTypeList list) {
            SimpleType item = list.getItemType() != null
                    ? simpleType(list.getItemType(), depth + 1) : simpleType(list.getItemTypeName(), depth + 1);
            return new SimpleType(item.kind(), true, item.builtin(), item.enumeration());
        } else if (content instanceof XmlSchemaSimpleTypeUnion) {
            return STRING;
        }
        return STRING;
    }

    private static SimpleType builtin(String name) {
        return switch (name) {
            case "boolean" -> new SimpleType(SimpleKind.BOOLEAN, false, name, null);
            case "decimal" -> new SimpleType(SimpleKind.DECIMAL, false, name, null);
            case "float", "double" -> new SimpleType(SimpleKind.FLOAT, false, name, null);
            case "integer", "nonPositiveInteger", "negativeInteger", "long", "int", "short", "byte",
                    "nonNegativeInteger", "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte",
                    "positiveInteger" ->
                new SimpleType(SimpleKind.INTEGER, false, name, null);
            case "NMTOKENS" -> new SimpleType(SimpleKind.STRING, true, "NMTOKEN", null);
            case "IDREFS" -> new SimpleType(SimpleKind.STRING, true, "IDREF", null);
            case "ENTITIES" -> new SimpleType(SimpleKind.STRING, true, "ENTITY", null);
            default -> new SimpleType(SimpleKind.STRING, false, name, null);
        };
    }

    /**
     * Indexes the children and attributes of a type by their JSON names, and records a conflict when two of them have
     * the same name.
     */
    private void index(Type type) {
        if (type.content != null) {
            List<Element> elements = new ArrayList<>();
            type.content.collectElements(elements);
            Set<Element> seen = new HashSet<>();
            for (Element e : elements) {
                if (!seen.add(e)) {
                    continue;
                }
                Element previous = type.children.putIfAbsent(e.jsonName(), e);
                if (previous != null && type.conflict == null) {
                    type.conflict = "The type " + type.displayName() + " has two child elements with the local name "
                                    + e.jsonName() + " (" + previous.name + " and " + e.name
                                    + "), which JSON cannot tell apart";
                }
            }
        }
        for (Attribute a : type.attributes) {
            type.attributesByLocalName.put(a.name.getLocalPart(), a);
            type.attributesByJsonName.put(a.jsonName, a);
            if (type.conflict == null && type.children.containsKey(a.jsonName)) {
                type.conflict = "The type " + type.displayName() + " has an attribute and a child element named "
                                + a.jsonName + ", which JSON cannot tell apart: set the attributePrefix option, such as @";
            }
            if (type.conflict == null && type.hasSimpleContent() && a.jsonName.equals(textName)) {
                type.conflict = "The type " + type.displayName() + " has an attribute named " + a.jsonName
                                + ", the same as the textName option: set the textName or attributePrefix option";
            }
        }
    }

    private static String namespace(XmlSchema schema) {
        String tns = schema != null ? schema.getTargetNamespace() : null;
        return tns != null ? tns : "";
    }
}
