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
package org.apache.camel.impl.converter;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Uses the {@link java.beans.PropertyEditor} conversion system to convert Objects to
 * and from String values.
 *
 * @version $Revision$
 */
public class PropertyEditorTypeConverter implements TypeConverter {

    private static final Log LOG = LogFactory.getLog(PropertyEditorTypeConverter.class);

    public <T> T convertTo(Class<T> type, Object value) {
        // We can't convert null values since we can't figure out a property
        // editor for it.
        if (value == null) {
            return null;
        }

        if (value.getClass() == String.class) {
            // No conversion needed.
            if (type == String.class) {
                return ObjectHelper.cast(type, value);
            }

            // TODO: findEditor is synchronized so we want to avoid calling it
            // we should have a local hit cache
            PropertyEditor editor = PropertyEditorManager.findEditor(type);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Finding property editor for type: " + type + " -> " + editor);
            }
            if (editor != null) {
                editor.setAsText(value.toString());
                return ObjectHelper.cast(type, editor.getValue());
            }
        } else if (type == String.class) {
            PropertyEditor editor = PropertyEditorManager.findEditor(value.getClass());
            if (LOG.isTraceEnabled()) {
                LOG.trace("Finding property editor for type: " + type + " -> " + editor);
            }
            if (editor != null) {
                editor.setValue(value);
                return ObjectHelper.cast(type, editor.getAsText());
            }
        }

        return null;
    }

    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        return convertTo(type, value);
    }

    public <T> T mandatoryConvertTo(Class<T> type, Object value) {
        return convertTo(type, value);
    }

    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) {
        return convertTo(type, value);
    }

}
