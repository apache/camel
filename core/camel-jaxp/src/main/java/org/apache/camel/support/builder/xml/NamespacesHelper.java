package org.apache.camel.support.builder.xml;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.apache.camel.support.builder.Namespaces;

public class NamespacesHelper {

    public static Namespaces namespaces(Element element) {
        Namespaces namespaces = new Namespaces();
        add(namespaces, element);
        return namespaces;
    }

    private static void add(Namespaces namespaces, Element element) {
        // let's set the parent first in case we overload a prefix here
        Node parentNode = element.getParentNode();
        if (parentNode instanceof org.w3c.dom.Element) {
            add(namespaces, (Element) parentNode);
        }
        NamedNodeMap attributes = element.getAttributes();
        int size = attributes.getLength();
        for (int i = 0; i < size; i++) {
            Attr node = (Attr) attributes.item(i);
            String name = node.getName();
            if (name.startsWith("xmlns:")) {
                String prefix = name.substring("xmlns:".length());
                String uri = node.getValue();
                namespaces.add(prefix, uri);
            }
        }
    }

}
