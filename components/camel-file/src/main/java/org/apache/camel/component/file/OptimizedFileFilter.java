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
package org.apache.camel.component.file;

/**
 * A filter for filtering file that is optimized for matching by name only.
 *
 * @see GenericFileFilter
 */
public interface OptimizedFileFilter extends GenericFileFilter {

    @Override
    default boolean accept(GenericFile file) {
        return false;
    }

    /**
     * Tests whether the specified file should be included (quick test using only file name)
     *
     * @param  name the file name
     * @return      <code>true</code> if and only if <code>file</code> should be included, <tt>null</tt> to use the
     *              {@link #accept(GenericFile)} method.
     */
    Boolean accept(String name);

}
