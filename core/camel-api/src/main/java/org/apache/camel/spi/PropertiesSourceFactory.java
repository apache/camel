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
package org.apache.camel.spi;

/**
 * Factory for creating the built-in {@link PropertiesSource} implementations used by the {@link PropertiesComponent}.
 * <p/>
 * Given a property location, the factory produces the appropriate source: file-based, classpath-based, or one backed by
 * a {@link java.util.Properties} bean looked up by reference in the {@link Registry}. The properties component uses
 * this to turn configured locations into active sources during initialization.
 * <p/>
 * See <a href="https://camel.apache.org/manual/using-propertyplaceholder.html">Using PropertyPlaceholder</a> in the
 * Camel user manual.
 *
 * @see   PropertiesSource
 * @see   PropertiesComponent
 * @since 4.0
 */
public interface PropertiesSourceFactory {

    /**
     * Creates a new file based {@link PropertiesSource}.
     *
     * @param  location location of the file
     * @return          the properties source
     */
    PropertiesSource newFilePropertiesSource(String location);

    /**
     * Creates a new classpath based {@link PropertiesSource}.
     *
     * @param  location location of the file in the classpath
     * @return          the properties source
     */
    PropertiesSource newClasspathPropertiesSource(String location);

    /**
     * Creates a new ref based {@link PropertiesSource}.
     *
     * @param  ref id for the {@link java.util.Properties} bean
     * @return     the properties source
     */
    PropertiesSource newRefPropertiesSource(String ref);
}
