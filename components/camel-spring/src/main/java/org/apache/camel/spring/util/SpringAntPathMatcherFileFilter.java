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
package org.apache.camel.spring.util;

import java.io.File;
import java.io.FileFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

/**
 * File filter using Spring's {@link AntPathMatcher}.
 * <p/>
 * Exclude take precedence over includes. If a file match both exclude and include it will be regarded as excluded.
 */
public class SpringAntPathMatcherFileFilter implements FileFilter {
    private static final transient Logger LOG = LoggerFactory.getLogger(SpringAntPathMatcherFileFilter.class);

    private AntPathMatcher matcher = new AntPathMatcher();
    private String[] excludes;
    private String[] includes;

    public boolean accept(File pathname) {
        return acceptPathName(pathname.getPath());
    }

    /**
     * Accepts the given file by the path name
     *
     * @param path the path
     * @return <tt>true</tt> if accepted, <tt>false</tt> if not
     */
    public boolean acceptPathName(String path) {
        // must use single / as path separators
        path = StringUtils.replace(path, File.separator, "/");

        LOG.trace("Filtering file: {}", path);

        // excludes take precedence
        if (excludes != null) {
            for (String exclude : excludes) {
                if (matcher.match(exclude, path)) {
                    // something to exclude so we cant accept it
                    LOG.trace("File is excluded: {}", path);
                    return false;
                }
            }
        }

        if (includes != null) {
            for (String include : includes) {
                if (matcher.match(include, path)) {
                    // something to include so we accept it
                    LOG.trace("File is included: {}", path);
                    return true;
                }
            }
        }

        // nothing to include so we cant accept it
        return false;
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
