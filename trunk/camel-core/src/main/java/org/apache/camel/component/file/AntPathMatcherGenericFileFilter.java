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

/**
 * File filter using AntPathMatcher.
 * <p/>
 * Exclude take precedence over includes. If a file match both exclude and include it will be regarded as excluded.
 * @param <T>
 */
public class AntPathMatcherGenericFileFilter<T> implements GenericFileFilter<T> {

    private final AntPathMatcherFileFilter filter;

    public AntPathMatcherGenericFileFilter() {
        filter = new AntPathMatcherFileFilter();
    }

    public AntPathMatcherGenericFileFilter(String... includes) {
        filter = new AntPathMatcherFileFilter();
        filter.setIncludes(includes);
    }

    public boolean accept(GenericFile<T> file) {
        // directories should always be accepted by ANT path matcher
        if (file.isDirectory()) {
            return true;
        }

        String path = file.getRelativeFilePath();
        return filter.acceptPathName(path);
    }

    public String[] getExcludes() {
        return filter.getExcludes();
    }

    public void setExcludes(String[] excludes) {
        filter.setExcludes(excludes);
    }

    public String[] getIncludes() {
        return filter.getIncludes();
    }

    public void setIncludes(String[] includes) {
        filter.setIncludes(includes);
    }

    /**
     * Sets excludes using a single string where each element can be separated with comma
     */
    public void setExcludes(String excludes) {
        filter.setExcludes(excludes);
    }

    /**
     * Sets includes using a single string where each element can be separated with comma
     */
    public void setIncludes(String includes) {
        filter.setIncludes(includes);
    }

    /**
     * Sets case sensitive flag on {@link org.apache.camel.component.file.AntPathMatcherFileFilter}
     * <p/>
     * Is by default turned on <tt>true</tt>.
     */
    public void setCaseSensitive(boolean caseSensitive) {
        filter.setCaseSensitive(caseSensitive);
    }

    public boolean isCaseSensitive() {
        return filter.isCaseSensitive();
    }
}
