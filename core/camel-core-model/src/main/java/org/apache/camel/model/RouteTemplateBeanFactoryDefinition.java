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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlValue;

import org.apache.camel.spi.Metadata;

/**
 * A route template bean factory (local bean)
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "templateBeanFactory")
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteTemplateBeanFactoryDefinition {
    @XmlTransient
    private RouteTemplateDefinition parent;
    // it only makes sense to use the languages that are general purpose scripting languages
    @XmlAttribute(required = true)
    @Metadata(enums = "bean,groovy,joor,mvel,ognl,spel")
    private String language;
    @XmlValue
    private String script;

    public RouteTemplateBeanFactoryDefinition() {
    }

    public RouteTemplateBeanFactoryDefinition(RouteTemplateDefinition parent) {
        this.parent = parent;
    }

    public String getLanguage() {
        return language;
    }

    /**
     * The language to use for creating the bean (such as groovy, joor)
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    public String getScript() {
        return script;
    }

    /**
     * The script to execute that creates the bean.
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     */
    public void setScript(String script) {
        this.script = script;
    }

    // fluent builders
    // ----------------------------------------------------

    public RouteTemplateDefinition bean(Class<?> type) {
        return bean(type, null);
    }

    public RouteTemplateDefinition bean(Class<?> type, String method) {
        setLanguage("bean");
        if (method != null) {
            setScript(type.getName() + "?method=" + method);
        } else {
            setScript(type.getName());
        }
        return parent;
    }

    public RouteTemplateDefinition groovy(String script) {
        setLanguage("groovy");
        setScript(script);
        return parent;
    }

    public RouteTemplateDefinition joor(String script) {
        setLanguage("joor");
        setScript(script);
        return parent;
    }

    public RouteTemplateDefinition mvel(String script) {
        setLanguage("mvel");
        setScript(script);
        return parent;
    }

    public RouteTemplateDefinition ognl(String script) {
        setLanguage("ognl");
        setScript(script);
        return parent;
    }

    public RouteTemplateDefinition spel(String script) {
        setLanguage("spel");
        setScript(script);
        return parent;
    }

}
