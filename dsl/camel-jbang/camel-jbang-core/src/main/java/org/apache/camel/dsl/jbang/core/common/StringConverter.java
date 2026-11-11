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
package org.apache.camel.dsl.jbang.core.common;

import org.aesh.command.converter.Converter;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.validator.OptionValidatorException;

/**
 * Converts the value of an option from its text: a failure is an invalid value of the option.
 */
public abstract class StringConverter<T> implements Converter<T, ConverterInvocation> {

    @Override
    public T convert(ConverterInvocation invocation) throws OptionValidatorException {
        try {
            return convert(invocation.getInput());
        } catch (OptionValidatorException e) {
            throw e;
        } catch (Exception e) {
            throw new OptionValidatorException(e.getMessage());
        }
    }

    public abstract T convert(String value) throws Exception;
}
