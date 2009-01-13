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

package org.apache.camel.osgi;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.springframework.osgi.mock.MockBundle;


/**
 *  The mock bundle will make up a normal camel-components bundle
 */
public class CamelMockBundle extends MockBundle {
    
    private class ListEnumeration implements Enumeration {
        private final List list;                    
        private int index;
        
        public ListEnumeration(List list) {
            this.list = list;
        }

        public boolean hasMoreElements() {
            return list == null ? false : index < list.size();
        }

        public Object nextElement() {
            Object result = null;
            if (list != null) { 
                result =  list.get(index);
                index++;
            } 
            return result;         
        }
        
    }
    
    private Enumeration getListEnumeration(String prefix, String entrys[]) {
        List<String> list = new ArrayList<String>();
        for (String entry : entrys) {            
            list.add(prefix + entry);
        }
        return new ListEnumeration(list);
    }

    public Enumeration getEntryPaths(String path) {
        Enumeration result = null;
        if (Activator.META_INF_COMPONENT.equals(path)) {
            String[] entries = new String[] {"timer_test", "file_test"};
            result = getListEnumeration(Activator.META_INF_COMPONENT, entries);
        }
        if (Activator.META_INF_LANGUAGE.equals(path)) {
            String[] entries = new String[] {"bean_test", "file_test"};
            result = getListEnumeration(Activator.META_INF_LANGUAGE, entries);
        }

        return result;
    }
    
    public Enumeration findEntries(String path, String filePattern, boolean recurse) {
        if (path.equals("/org/apache/camel/osgi/test") && filePattern.equals("*.class")) {
            List<URL> urls = new ArrayList<URL>();
            URL url = getClass().getClassLoader().getResource("org/apache/camel/osgi/test/MyTypeConverter.class");
            urls.add(url);
            url = getClass().getClassLoader().getResource("org/apache/camel/osgi/test/MyRouteBuilder.class");
            urls.add(url);
            return new ListEnumeration(urls);
        } else {
            return super.findEntries(path, filePattern, recurse);
        }
    }
}
