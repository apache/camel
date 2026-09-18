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
package org.apache.camel.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Looks up the beans that Camel ships out of the box (aggregation strategies, idempotent and aggregation repositories,
 * header filter strategies, ...) from the metadata the build tools generate into every JAR that has such beans:
 * <tt>META-INF/services/org/apache/camel/bean.properties</tt> lists the bean names, and
 * <tt>META-INF/services/org/apache/camel/bean/&lt;Name&gt;.json</tt> has the class, the interface and the artifact.
 * <p/>
 * The metadata is read when a class cannot be found, so the error can say which built-in bean was likely meant. The
 * classpath is scanned on each call; this is an error-path helper and not meant for hot paths.
 */
public final class PojoBeanHelper {

    /** Where the build tools generate the bean names of a JAR (one file per JAR). */
    public static final String BEAN_PROPERTIES = "META-INF/services/org/apache/camel/bean.properties";

    /** Where the build tools generate the metadata of a bean (one file per bean). */
    public static final String BEAN_JSON_PATH = "META-INF/services/org/apache/camel/bean/";

    /** How many built-in beans of an interface a hint lists before saying "and N more". */
    private static final int MAX_LISTED = 6;

    /**
     * A bean Camel ships out of the box, as described by its generated metadata.
     *
     * @param name          the bean name (the simple class name)
     * @param javaType      the fully qualified class name
     * @param interfaceType the fully qualified name of the interface the bean implements (may be null)
     * @param groupId       the Maven groupId of the artifact that ships the bean
     * @param artifactId    the Maven artifactId of the artifact that ships the bean
     */
    public record PojoBean(String name, String javaType, String interfaceType, String groupId, String artifactId) {
    }

    private PojoBeanHelper() {
    }

    /**
     * All the built-in beans whose metadata is on the classpath.
     */
    public static List<PojoBean> findAll(CamelContext camelContext) {
        List<PojoBean> answer = new ArrayList<>();
        for (URL url : findBeanProperties(camelContext)) {
            Properties props = new Properties();
            try (InputStream is = url.openStream()) {
                props.load(is);
            } catch (IOException e) {
                continue;
            }
            String names = props.getProperty("bean", "");
            for (String name : names.split("\\s+")) {
                if (!name.isBlank()) {
                    PojoBean bean = loadBean(camelContext, name.trim(), props);
                    if (bean != null) {
                        answer.add(bean);
                    }
                }
            }
        }
        return answer;
    }

    /**
     * The built-in bean with the given name, matched case-insensitively by the simple class name or by the fully
     * qualified class name, or <tt>null</tt> if there is none.
     */
    public static PojoBean findByName(CamelContext camelContext, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String simple = name.substring(name.lastIndexOf('.') + 1);
        for (PojoBean bean : findAll(camelContext)) {
            if (bean.name().equalsIgnoreCase(simple) || bean.javaType().equalsIgnoreCase(name)) {
                return bean;
            }
        }
        return null;
    }

    /**
     * The built-in beans of an interface, given by its fully qualified or simple name.
     */
    public static List<PojoBean> beansOfInterface(CamelContext camelContext, String interfaceName) {
        List<PojoBean> answer = new ArrayList<>();
        if (interfaceName == null || interfaceName.isBlank()) {
            return answer;
        }
        String simple = interfaceName.substring(interfaceName.lastIndexOf('.') + 1);
        for (PojoBean bean : findAll(camelContext)) {
            String iface = bean.interfaceType();
            if (iface != null && (iface.equals(interfaceName) || iface.endsWith("." + simple))) {
                answer.add(bean);
            }
        }
        return answer;
    }

    /**
     * What to do about a class that was not found, from the built-in bean metadata: the built-in bean with that simple
     * name (a wrong package), or the built-in beans of the interface the class was expected to implement. The metadata
     * is in the same JAR as the bean, so a built-in bean whose JAR is missing gets the generic hint.
     *
     * @param  camelContext the camel context
     * @param  className    the class that was not found (fully qualified or simple name), or a <tt>#class:</tt>
     *                      reference to it; anything else (a bean name) gives no hint
     * @param  expectedType the interface the class was expected to implement, or <tt>null</tt> (or Object) if unknown
     * @return              the hint, in parentheses with a leading space, or an empty string if there is nothing to say
     */
    public static String classNotFoundHint(CamelContext camelContext, String className, Class<?> expectedType) {
        if (className == null || className.isBlank()) {
            return "";
        }
        if (className.startsWith("#")) {
            if (!className.startsWith("#class:")) {
                return "";
            }
            // strip the reference prefix and any factory method or constructor parameters
            className = className.substring(7);
            className = StringHelper.before(className, "(", className);
            className = StringHelper.before(className, "#", className);
        }
        PojoBean bean = findByName(camelContext, className);
        if (bean != null && !bean.javaType().equals(className)) {
            return " (did you mean " + bean.javaType()
                   + (bean.interfaceType() != null ? " (" + bean.interfaceType() + ")" : "") + "?)";
        }
        String hint = "check the package name; a class from another library needs its dependency added";
        if (expectedType != null && expectedType != Object.class) {
            List<PojoBean> beans = beansOfInterface(camelContext, expectedType.getName());
            if (!beans.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < beans.size() && i < MAX_LISTED; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(beans.get(i).name()).append(" (").append(beans.get(i).javaType()).append(")");
                }
                if (beans.size() > MAX_LISTED) {
                    sb.append(" and ").append(beans.size() - MAX_LISTED).append(" more");
                }
                hint += "; the built-in " + expectedType.getSimpleName() + " beans are " + sb;
            }
        }
        return " (" + hint + ")";
    }

    /**
     * The class named by a ClassNotFoundException or NoClassDefFoundError in the cause chain, in dotted form, or
     * <tt>null</tt> if the failure is not a missing class.
     */
    public static String missingClassName(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError) {
                // NoClassDefFoundError names the class in internal form (java/lang/Foo)
                return t.getMessage() != null ? t.getMessage().trim().replace('/', '.') : null;
            }
        }
        return null;
    }

    private static PojoBean loadBean(CamelContext camelContext, String name, Properties props) {
        try (InputStream is = camelContext.getClassResolver().loadResourceAsStream(BEAN_JSON_PATH + name + ".json")) {
            if (is == null) {
                return null;
            }
            JsonObject root = (JsonObject) Jsoner.deserialize(IOHelper.loadText(is));
            JsonObject bean = root.getMap("bean");
            if (bean == null || bean.getString("javaType") == null) {
                return null;
            }
            return new PojoBean(
                    bean.getStringOrDefault("name", name), bean.getString("javaType"), bean.getString("interfaceType"),
                    bean.getStringOrDefault("groupId", props.getProperty("groupId")),
                    bean.getStringOrDefault("artifactId", props.getProperty("artifactId")));
        } catch (Exception e) {
            return null;
        }
    }

    private static List<URL> findBeanProperties(CamelContext camelContext) {
        // dedupe by URI as URL equals/hashCode may resolve host names
        Set<URI> seen = new LinkedHashSet<>();
        List<URL> answer = new ArrayList<>();
        addResources(seen, answer, camelContext.getClassResolver().loadAllResourcesAsURL(BEAN_PROPERTIES));
        ClassLoader acl = camelContext.getApplicationContextClassLoader();
        if (acl != null) {
            try {
                addResources(seen, answer, acl.getResources(BEAN_PROPERTIES));
            } catch (IOException e) {
                // ignore
            }
        }
        return answer;
    }

    private static void addResources(Set<URI> seen, List<URL> answer, Enumeration<URL> resources) {
        while (resources != null && resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try {
                if (seen.add(url.toURI())) {
                    answer.add(url);
                }
            } catch (URISyntaxException e) {
                // ignore
            }
        }
    }

}
