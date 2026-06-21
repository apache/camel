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

import org.jspecify.annotations.Nullable;

/**
 * Captures how a single {@link PropertiesComponent} property placeholder was resolved, for inspection and tooling.
 * <p/>
 * Each instance records the placeholder {@code name}, the {@code originalValue} (the raw placeholder expression), the
 * resolved {@code value}, any {@code defaultValue} that applied, and the {@code source} that provided the value. Camel
 * tracks these so the resolved configuration can be reviewed, for example by the developer console or diagnostics.
 * <p/>
 * See <a href="https://camel.apache.org/manual/using-propertyplaceholder.html">Using PropertyPlaceholder</a> in the
 * Camel user manual.
 *
 * @see   PropertiesComponent
 * @since 4.9
 */
public record PropertiesResolvedValue(String name, String originalValue, String value, @Nullable String defaultValue,
        @Nullable String source) {

}
