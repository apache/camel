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
package org.apache.camel.util;

import org.apache.camel.converter.ObjectConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collection;
import java.util.Iterator;

/**
 * @version $Revision$
 */
public class ObjectHelper {
    private static final transient Log log = LogFactory.getLog(ObjectHelper.class);

    /**
     * A helper method for comparing objects for equality while handling nulls
     */
    public static boolean equals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        return a != null && b != null && a.equals(b);
    }

    /**
     * A helper method for performing an ordered comparsion on the objects
     * handling nulls and objects which do not
     * handle sorting gracefully
     */
    public static int compare(Object a, Object b) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        if (a instanceof Comparable) {
            Comparable comparable = (Comparable) a;
            return comparable.compareTo(b);
        }
        else {
            int answer = a.getClass().getName().compareTo(b.getClass().getName());
            if (answer == 0) {
                answer = a.hashCode() - b.hashCode();
            }
            return answer;
        }
    }


    public static void notNull(Object value, String name) {
        if (value == null) {
            throw new NullPointerException("No " + name + " specified");
        }
    }
    
    public static String[] splitOnCharacter(String value, String needle, int count) {
    	String rc [] = new String[count];
    	rc[0] = value;
    	for ( int i=1; i < count; i++ ) {
    		String v = rc[i-1];
			int p = v.indexOf(needle);
			if( p < 0 ) {
				return rc;
			} 
			rc[i-1] = v.substring(0,p);
			rc[i] = v.substring(p+1);
    	}
    	return rc;
	}

    /**
     * Returns true if the collection contains the specified value
     */
    public static boolean contains(Object collectionOrArray, Object value) {
        if (collectionOrArray instanceof Collection) {
            Collection collection = (Collection) collectionOrArray;
            return collection.contains(value);
        }
        else {
            Iterator iter = ObjectConverter.iterator(value);
            while (iter.hasNext()) {
                if (equals(value, iter.next())) {
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean isNotNullOrBlank(String text) {
        return text != null && text.trim().length() > 0;
    }

    /**
     * A helper method to access a system property, catching any security exceptions
     *
     * @param name the name of the system property required
     * @param defaultValue the default value to use if the property is not available or a security exception prevents access
     * @return the system property value or the default value if the property is not available or security does not allow its access
     */
    public static String getSystemProperty(String name, String defaultValue) {
        try {
            return System.getProperty(name, defaultValue);
        }
        catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Caught security exception accessing system property: " + name + ". Reason: " + e, e);
            }
            return defaultValue;
        }
    }

    /**
     * Returns the type name of the given type or null if the type variable is null
     */
    public static String name(Class type) {
        return type != null ? type.getName() : null;
    }
}
