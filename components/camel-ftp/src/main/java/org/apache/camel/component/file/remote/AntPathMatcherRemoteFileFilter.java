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
package org.apache.camel.component.file.remote;

import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.CollectionHelper.collectionAsCommaDelimitedString;

/**
 * File filter using Spring's AntPathMatcher.
 * <p/>
 * Exclude take precedence over includes. If a file match both exclude and include it will be regarded as excluded.
 */
public class AntPathMatcherRemoteFileFilter implements RemoteFileFilter {
    private static final String ANTPATHMATCHER_CLASSNAME = "org.apache.camel.component.file.AntPathMatcherFileFilter";
    private String[] excludes;
    private String[] includes;

    public boolean accept(RemoteFile file) {
        // we must use reflection to invoke the AntPathMatcherFileFilter that reside in camel-spring.jar
        // and we don't want camel-ftp to have runtime dependency on camel-spring.jar
        Class clazz = ObjectHelper.loadClass(ANTPATHMATCHER_CLASSNAME);
        ObjectHelper.notNull(clazz, ANTPATHMATCHER_CLASSNAME + " not found in classpath. camel-spring.jar is required in the classpath.");

        try {
            Object filter = ObjectHelper.newInstance(clazz);

            // invoke setIncludes(String), must using string type as invoking with string[] does not work
            ObjectHelper.invokeMethod(filter.getClass().getMethod("setIncludes", String.class), filter, collectionAsCommaDelimitedString(includes));

            // invoke setExcludes(String), must using string type as invoking with string[] does not work
            ObjectHelper.invokeMethod(filter.getClass().getMethod("setExcludes", String.class), filter, collectionAsCommaDelimitedString(excludes));

            // invoke acceptPathName(String)
            String path = file.getRelativeFileName();
            Boolean result = (Boolean) ObjectHelper.invokeMethod(filter.getClass().getMethod("acceptPathName", String.class), filter, path);
            return result;

        } catch (NoSuchMethodException e) {
            throw new TypeNotPresentException(ANTPATHMATCHER_CLASSNAME, e);
        }
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
