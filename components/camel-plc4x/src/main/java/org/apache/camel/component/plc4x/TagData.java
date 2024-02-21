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
package org.apache.camel.component.plc4x;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.slf4j.LoggerFactory;

public class TagData {
    private String tagName;
    private String query;
    private Object value;

    private Map<Class<?>, Predicate<String>> canParse = new HashMap<>();
    {
        canParse.put(Integer.TYPE, s -> {
            try {
                Integer.parseInt(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        canParse.put(Long.TYPE, s -> {
            try {
                Long.parseLong(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        canParse.put(Short.TYPE, s -> {
            try {
                Short.parseShort(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        canParse.put(Boolean.TYPE, s -> {
            try {
                Boolean.parseBoolean(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        canParse.put(Double.TYPE, s -> {
            try {
                Double.parseDouble(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        canParse.put(Float.TYPE, s -> {
            try {
                Float.parseFloat(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    };

    public TagData(String alias, String query, Object value) {
        this.tagName = alias;
        this.query = query;
        this.value = value;
        setType();
    }

    public TagData(String tagName, String query) {
        this.tagName = tagName;
        this.query = query;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    private void setType() {
        if (value != null && value instanceof String) {
            String val = (String) value;
            if (canParse.get(Boolean.TYPE).test(val)) {
                value = Boolean.parseBoolean(val);
            }
            if (canParse.get(Short.TYPE).test(val)) {
                value = Short.parseShort(val);
            } else if (canParse.get(Integer.TYPE).test(val)) {
                value = Integer.parseInt(val);
            } else if (canParse.get(Long.TYPE).test(val)) {
                value = Long.parseLong(val);
            } else if (canParse.get(Double.TYPE).test(val)) {
                value = Double.parseDouble(val);
            } else if (canParse.get(Float.TYPE).test(val)) {
                value = Float.parseFloat(val);
            }

        }
    }

    @Override
    public String toString() {
        return "(" + tagName + ") : " + value;
    }

    @Override
    public boolean equals(Object tag) {
        return value != null
                ? ((TagData) tag).getValue().equals(value)
                        && ((TagData) tag).getTagName().equals(tagName)
                        && ((TagData) tag).getQuery().equals(query)
                : ((TagData) tag).getTagName().equals(tagName)
                        && ((TagData) tag).getQuery().equals(query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tagName, query, value, canParse);
    }

    public static Map<String, String> toMap(List<TagData> tags) {
        Map<String, String> map = new HashMap<>();
        LoggerFactory.getLogger(TagData.class).info("Classloader {} ", Thread.currentThread().getContextClassLoader());
        for (TagData tag : tags) {
            map.put(tag.getTagName(), tag.getQuery());
        }
        return map;
    }
}
