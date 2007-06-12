/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import org.apache.camel.TypeConverter;

/**
 * Uses the java.beans.PropertyEditor conversion system to convert Objects to and from String values.
 * 
 * @version $Revision: 523731 $
 */
public class PropertyEditorTypeConverter implements TypeConverter {
	
	
	public <T> T convertTo(Class<T> toType, Object value) {
		
		// We can't convert null values since we can't figure out a property editor for it.
		if( value == null )
			return null;
		
		if( value.getClass() == String.class ) {
			
			// No conversion needed.
			if( toType == String.class ) {
				return toType.cast(value);
			}
			
	        PropertyEditor editor = PropertyEditorManager.findEditor(toType);
	        if( editor != null ) { 
	            editor.setAsText(value.toString());
	            return toType.cast(editor.getValue());
	        }
	        
		} else  if( toType == String.class ) {
			
	        PropertyEditor editor = PropertyEditorManager.findEditor(value.getClass());
	        if( editor != null ) { 
	            editor.setValue(value);
	            return toType.cast(editor.getAsText());
	        }
	        
		}
        return null;
	}

}
