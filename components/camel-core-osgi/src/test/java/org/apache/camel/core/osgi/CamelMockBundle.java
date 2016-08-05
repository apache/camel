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
package org.apache.camel.core.osgi;

import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.CastUtils;
import org.osgi.framework.Version;
import org.springframework.osgi.mock.MockBundle;

/**
 *  The mock bundle will make up a normal camel-components bundle
 */
public class CamelMockBundle extends MockBundle {
    
    public static final String META_INF_COMPONENT = "META-INF/services/org/apache/camel/component/";
    public static final String META_INF_LANGUAGE = "META-INF/services/org/apache/camel/language/";
    public static final String META_INF_LANGUAGE_RESOLVER = "META-INF/services/org/apache/camel/language/resolver/";
    public static final String META_INF_DATAFORMAT = "META-INF/services/org/apache/camel/dataformat/";

    private static class ListEnumeration<E> implements Enumeration<E> {
        private final List<E> list;                    
        private int index;
        
        ListEnumeration(List<E> list) {
            this.list = list;
        }

        public boolean hasMoreElements() {
            return list != null && index < list.size();
        }

        public E nextElement() {
            E result = null;
            if (list != null) { 
                result =  list.get(index);
                index++;
            } 
            return result;         
        }
        
    }
    
    public CamelMockBundle() {
        setClassLoader(getClass().getClassLoader());
    }

    private Enumeration<String> getListEnumeration(String prefix, String entrys[]) {
        List<String> list = new ArrayList<String>();
        for (String entry : entrys) {            
            list.add(prefix + entry);
        }
        return new ListEnumeration<String>(list);
    }

    public Enumeration<String> getEntryPaths(String path) {
        Enumeration<String> result = null;
        if (META_INF_COMPONENT.equals(path)) {
            String[] entries = new String[] {"timer_test", "file_test"};
            result = getListEnumeration(META_INF_COMPONENT, entries);
        }
        if (META_INF_LANGUAGE.equals(path)) {
            String[] entries = new String[] {"bean_test", "file_test"};
            result = getListEnumeration(META_INF_LANGUAGE, entries);
        }
        if (META_INF_LANGUAGE_RESOLVER.equals(path)) {
            String[] entries = new String[] {"default"};
            result = getListEnumeration(META_INF_LANGUAGE_RESOLVER, entries);
        }

        return result;
    }
    
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        if (path.equals("/org/apache/camel/core/osgi/test") && filePattern.equals("*.class")) {
            List<URL> urls = new ArrayList<URL>();
            URL url = getClass().getClassLoader().getResource("org/apache/camel/core/osgi/test/MyTypeConverter.class");
            urls.add(url);
            url = getClass().getClassLoader().getResource("org/apache/camel/core/osgi/test/MyRouteBuilder.class");
            urls.add(url);
            return new ListEnumeration<URL>(urls);
        } else {
            return CastUtils.cast(super.findEntries(path, filePattern, recurse));
        }
    }

    @Override
    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
        return null;
    }

    @Override
    public Version getVersion() {
        return Version.parseVersion("1.0.0");
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (isLoadableClass(name)) {
            return super.loadClass(name);
        } else {
            throw new ClassNotFoundException(name);
        }
    }

    protected boolean isLoadableClass(String name) {
        return !name.startsWith("org.apache.camel.core.osgi.other");
    }
}
