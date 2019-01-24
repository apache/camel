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

import java.io.File;
import java.io.FileFilter;

import org.apache.camel.util.AntPathMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File filter using {@link AntPathMatcher}.
 * <p/>
 * Exclude take precedence over includes. If a file match both exclude and include it will be regarded as excluded.
 */
public class AntPathMatcherFileFilter implements FileFilter {
    private static final Logger LOG = LoggerFactory.getLogger(AntPathMatcherFileFilter.class);

    private AntPathMatcher matcher = new AntPathMatcher();
    private String[] excludes;
    private String[] includes;
    private boolean caseSensitive = true;

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
        path = path.replace(File.separatorChar, '/');

        LOG.trace("Filtering file: {}", path);

        // excludes take precedence
        if (excludes != null) {
            for (String exclude : excludes) {
                if (matcher.match(exclude, path, caseSensitive)) {
                    // something to exclude so we cant accept it
                    LOG.trace("File is excluded: {}", path);
                    return false;
                }
            }
        }

        if (includes != null) {
            for (String include : includes) {
                if (matcher.match(include, path, caseSensitive)) {
                    // something to include so we accept it
                    LOG.trace("File is included: {}", path);
                    return true;
                }
            }
        }

        if (excludes != null && includes == null) {
            // if the user specified excludes but no includes, presumably we should include by default
            return true;
        }

        // nothing to include so we can't accept it
        return false;
    }

    /**
     *
     * @return <tt>true</tt> if case sensitive pattern matching is on,
     * <tt>false</tt> if case sensitive pattern matching is off.
     */
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    /**
     * Sets Whether or not pattern matching should be case sensitive
     * <p/>
     * Is by default turned on <tt>true</tt>.
     * @param caseSensitive <tt>false</tt> to disable case sensitive pattern matching
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
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
