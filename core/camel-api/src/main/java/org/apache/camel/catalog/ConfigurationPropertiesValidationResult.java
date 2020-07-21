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
package org.apache.camel.catalog;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Details result of validating configuration properties (eg application.properties for camel-main).
 */
public class ConfigurationPropertiesValidationResult extends PropertiesValidationResult implements Serializable {

    private String fileName;
    private String text;
    private int lineNumber;
    private boolean accepted;

    public ConfigurationPropertiesValidationResult() {
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    /**
     * A human readable summary of the validation errors.
     *
     * @param includeHeader    whether to include a header
     * @return the summary, or <tt>null</tt> if no validation errors
     */
    public String summaryErrorMessage(boolean includeHeader) {
        return summaryErrorMessage(includeHeader, true, false);
    }

    /**
     * A human readable summary of the validation errors.
     *
     * @param includeHeader    whether to include a header
     * @param ignoreDeprecated whether to ignore deprecated options in use as an error or not
     * @param includeWarnings  whether to include warnings as an error or not
     * @return the summary, or <tt>null</tt> if no validation errors
     */
    public String summaryErrorMessage(boolean includeHeader, boolean ignoreDeprecated, boolean includeWarnings) {
        boolean ok = isSuccess();

        // special check if we should ignore deprecated options being used
        if (ok && !ignoreDeprecated) {
            ok = deprecated == null;
        }

        if (includeWarnings) {
            if (unknownComponent != null) {
                return "\tUnknown component: " + unknownComponent;
            }
        }

        if (ok) {
            return null;
        }

        // for each invalid option build a reason message
        Map<String, String> options = new LinkedHashMap<>();
        if (unknown != null) {
            for (String name : unknown) {
                if (unknownSuggestions != null && unknownSuggestions.containsKey(name)) {
                    String[] suggestions = unknownSuggestions.get(name);
                    if (suggestions != null && suggestions.length > 0) {
                        String str = Arrays.asList(suggestions).toString();
                        options.put(name, "Unknown option. Did you mean: " + str);
                    } else {
                        options.put(name, "Unknown option");
                    }
                } else {
                    options.put(name, "Unknown option");
                }
            }
        }
        if (required != null) {
            for (String name : required) {
                options.put(name, "Missing required option");
            }
        }
        if (deprecated != null) {
            for (String name : deprecated) {
                options.put(name, "Deprecated option");
            }
        }
        if (invalidEnum != null) {
            for (Map.Entry<String, String> entry : invalidEnum.entrySet()) {
                String name = entry.getKey();
                String[] choices = invalidEnumChoices.get(name);
                String defaultValue = defaultValues != null ? defaultValues.get(entry.getKey()) : null;
                String str = Arrays.asList(choices).toString();
                String msg = "Invalid enum value: " + entry.getValue() + ". Possible values: " + str;
                if (invalidEnumSuggestions != null) {
                    String[] suggestions = invalidEnumSuggestions.get(name);
                    if (suggestions != null && suggestions.length > 0) {
                        str = Arrays.asList(suggestions).toString();
                        msg += ". Did you mean: " + str;
                    }
                }
                if (defaultValue != null) {
                    msg += ". Default value: " + defaultValue;
                }

                options.put(entry.getKey(), msg);
            }
        }
        if (invalidReference != null) {
            for (Map.Entry<String, String> entry : invalidReference.entrySet()) {
                boolean empty = isEmpty(entry.getValue());
                if (empty) {
                    options.put(entry.getKey(), "Empty reference value");
                } else if (!entry.getValue().startsWith("#")) {
                    options.put(entry.getKey(), "Invalid reference value: " + entry.getValue() + " must start with #");
                } else {
                    options.put(entry.getKey(), "Invalid reference value: " + entry.getValue());
                }
            }
        }
        if (invalidBoolean != null) {
            for (Map.Entry<String, String> entry : invalidBoolean.entrySet()) {
                boolean empty = isEmpty(entry.getValue());
                if (empty) {
                    options.put(entry.getKey(), "Empty boolean value");
                } else {
                    options.put(entry.getKey(), "Invalid boolean value: " + entry.getValue());
                }
            }
        }
        if (invalidInteger != null) {
            for (Map.Entry<String, String> entry : invalidInteger.entrySet()) {
                boolean empty = isEmpty(entry.getValue());
                if (empty) {
                    options.put(entry.getKey(), "Empty integer value");
                } else {
                    options.put(entry.getKey(), "Invalid integer value: " + entry.getValue());
                }
            }
        }
        if (invalidNumber != null) {
            for (Map.Entry<String, String> entry : invalidNumber.entrySet()) {
                boolean empty = isEmpty(entry.getValue());
                if (empty) {
                    options.put(entry.getKey(), "Empty number value");
                } else {
                    options.put(entry.getKey(), "Invalid number value: " + entry.getValue());
                }
            }
        }
        if (invalidMap != null) {
            for (Map.Entry<String, String> entry : invalidMap.entrySet()) {
                boolean empty = isEmpty(entry.getValue());
                if (empty) {
                    options.put(entry.getKey(), "Empty map key/value pair");
                } else {
                    options.put(entry.getKey(), "Invalid map key/value: " + entry.getValue());
                }
            }
        }
        if (invalidArray != null) {
            for (Map.Entry<String, String> entry : invalidArray.entrySet()) {
                boolean empty = isEmpty(entry.getValue());
                if (empty) {
                    options.put(entry.getKey(), "Empty array index/value pair");
                } else {
                    options.put(entry.getKey(), "Invalid array index/value: " + entry.getValue());
                }
            }
        }

        // build a table with the error summary nicely formatted
        // lets use 24 as min length
        int maxLen = 24;
        for (String key : options.keySet()) {
            maxLen = Math.max(maxLen, key.length());
        }
        String format = "%" + maxLen + "s    %s";

        // build the human error summary
        StringBuilder sb = new StringBuilder();
        if (includeHeader) {
            sb.append("Configuration properties error\n");
            sb.append("---------------------------------------------------------------------------------------------------------------------------------------\n");
            sb.append("\n");
        }
        if (text != null) {
            sb.append("\t").append(text).append("\n");
        } else {
            sb.append("\n");
        }
        for (Map.Entry<String, String> option : options.entrySet()) {
            String out = String.format(format, shortKey(option.getKey()), option.getValue());
            sb.append("\n\t").append(out);
        }

        return sb.toString();
    }

    private static String shortKey(String key) {
        if (key.indexOf('.') > 0) {
            return key.substring(key.lastIndexOf('.') + 1);
        } else {
            return key;
        }
    }

}
