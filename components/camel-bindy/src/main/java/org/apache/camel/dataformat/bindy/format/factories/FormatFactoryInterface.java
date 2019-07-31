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
package org.apache.camel.dataformat.bindy.format.factories;

import java.util.Collection;

import org.apache.camel.dataformat.bindy.Format;
import org.apache.camel.dataformat.bindy.FormattingOptions;

public interface FormatFactoryInterface {
    /**
     * Returns the list of supported classes.
     * When the list doesn't contain elements the factory is supposed
     * to support all kinds of classes. The factory must decide on other
     * criteria whether it can build a {@link Format}.
     * @return the list of supported classes
     */
    Collection<Class<?>> supportedClasses();

    /**
     * Can it build a {@link Format}.
     * Answers the question about whether it can
     * build a {@link Format}.
     * @param formattingOptions
     * @return can build
     */
    boolean canBuild(FormattingOptions formattingOptions);

    /**
     * Builds the {@link Format}.
     * @param formattingOptions
     * @return the format
     */
    Format<?> build(FormattingOptions formattingOptions);
}
