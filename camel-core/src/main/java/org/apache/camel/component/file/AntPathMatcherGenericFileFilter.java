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
package org.apache.camel.component.file;

import java.lang.reflect.Method;

import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.CollectionHelper.collectionAsCommaDelimitedString;

/**
 * File filter using Spring's AntPathMatcher.
 * <p/>
 * Exclude take precedence over includes. If a file match both exclude and include it will be regarded as excluded.
 */
public class AntPathMatcherGenericFileFilter implements GenericFileFilter {
    private static final String ANTPATHMATCHER_CLASSNAME = "org.apache.camel.spring.util.SpringAntPathMatcherFileFilter";

    private String[] excludes;
    private String[] includes;

    private Object filter;
    private Method includesMethod;
    private Method excludesMethod;
    private Method acceptsMethod;

    public boolean accept(GenericFile file) {
        try {
            synchronized (this) {
                if (filter == null) {
                    init();
                }
            }

            // invoke setIncludes(String), must using string type as invoking with string[] does not work
            ObjectHelper.invokeMethod(includesMethod, filter, collectionAsCommaDelimitedString(includes));

            // invoke setExcludes(String), must using string type as invoking with string[] does not work
            ObjectHelper.invokeMethod(excludesMethod, filter, collectionAsCommaDelimitedString(excludes));

            // invoke acceptPathName(String)
            String path = file.getRelativeFilePath();
            Boolean result = (Boolean) ObjectHelper.invokeMethod(acceptsMethod, filter, path);
            return result;

        } catch (NoSuchMethodException e) {
            throw new TypeNotPresentException(ANTPATHMATCHER_CLASSNAME, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void init() throws NoSuchMethodException {
        // we must use reflection to invoke the AntPathMatcherFileFilter that reside in camel-spring.jar
        // and we don't want camel-ftp to have runtime dependency on camel-spring.jar
        Class clazz = ObjectHelper.loadClass(ANTPATHMATCHER_CLASSNAME);
        ObjectHelper.notNull(clazz, ANTPATHMATCHER_CLASSNAME + " not found in classpath. camel-spring.jar is required in the classpath.");

        filter = ObjectHelper.newInstance(clazz);

        includesMethod = filter.getClass().getMethod("setIncludes", String.class);
        excludesMethod = filter.getClass().getMethod("setExcludes", String.class);
        acceptsMethod = filter.getClass().getMethod("acceptPathName", String.class);
    }

    public String[] getExcludes() {
        return excludes;
    }

    public void setExcludes(String[] excludes) {
        this.excludes = excludes;
    }

    public String[] getIncludes() {
        return includes;
    }

    public void setIncludes(String[] includes) {
        this.includes = includes;
    }

    /**
     * Sets excludes using a single string where each element can be separated with comma
     */
    public void setExcludes(String excludes) {
        setExcludes(excludes.split(","));
    }

    /**
     * Sets includes using a single string where each element can be separated with comma
     */
    public void setIncludes(String includes) {
        setIncludes(includes.split(","));
    }

}
