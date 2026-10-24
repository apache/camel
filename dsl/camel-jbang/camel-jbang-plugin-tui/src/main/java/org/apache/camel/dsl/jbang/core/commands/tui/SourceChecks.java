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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.networknt.schema.Error;
import org.apache.camel.dsl.yaml.validator.YamlValidator;

/**
 * The checks of the file in the source editor, by file type: what they find is either an error or a note. An error of a
 * YAML, properties or XML file keeps it from being saved; a Java file is the application's code, and is always saved
 * with its errors said. A note is something that works but is not right: marked and said, never blocking a save.
 * <p/>
 * The same checks run when a file opens, while typing and on save. A save also checks a YAML file against the YAML DSL
 * schema, and leaves out the notes of a file whose errors keep it from being saved.
 */
final class SourceChecks {

    /** The kind of file, which decides the checks and whether an error blocks the save. */
    enum Kind {
        YAML,
        PROPERTIES,
        XML,
        /** a Java route file, or a file the route checks do not read */
        OTHER
    }

    /** What the checks found, each as "Line N: message", or a message of no line. */
    record Found(List<String> errors, List<String> notes) {

        static final Found NONE = new Found(List.of(), List.of());

        /** The errors then the notes. */
        List<String> all() {
            if (notes.isEmpty()) {
                return errors;
            }
            List<String> answer = new ArrayList<>(errors);
            answer.addAll(notes);
            return answer;
        }

        /** The problem of each line, by line from 0; a line with an error and a note shows the error. */
        Map<Integer, String> byLine(String content) {
            return SourceValidationSupport.buildInlineErrors(all(), content);
        }

        /** The lines, from 0, whose problem is a note. */
        Set<Integer> noteLines(String content) {
            if (notes.isEmpty()) {
                return Set.of();
            }
            Set<Integer> answer = new HashSet<>(SourceValidationSupport.buildInlineErrors(notes, content).keySet());
            answer.removeAll(SourceValidationSupport.buildInlineErrors(errors, content).keySet());
            return answer;
        }

        /** Whether the errors keep a file of the kind from being saved. */
        boolean blocksSave(Kind kind) {
            return !errors.isEmpty() && kind != Kind.OTHER;
        }
    }

    private YamlValidator yamlValidator;
    private SourceViewer.PropertiesValidator propertiesValidator;
    private SourceViewer.EndpointValidator endpointValidator;
    private SourceViewer.EndpointValidator simpleValidator;
    // the Kamelet checks of a YAML file (CAMEL-25411)
    private SourceViewer.EndpointValidator kameletValidator;
    // the Camel checks of a Java or XML DSL route file (CAMEL-25208)
    private SourceViewer.EndpointValidator routeValidator;
    // the notes of a YAML, Java or XML route file
    private SourceViewer.EndpointValidator notes;

    void setPropertiesValidator(SourceViewer.PropertiesValidator propertiesValidator) {
        this.propertiesValidator = propertiesValidator;
    }

    void setEndpointValidator(SourceViewer.EndpointValidator endpointValidator) {
        this.endpointValidator = endpointValidator;
    }

    void setSimpleValidator(SourceViewer.EndpointValidator simpleValidator) {
        this.simpleValidator = simpleValidator;
    }

    void setKameletValidator(SourceViewer.EndpointValidator kameletValidator) {
        this.kameletValidator = kameletValidator;
    }

    void setNotes(SourceViewer.EndpointValidator notes) {
        this.notes = notes;
    }

    void setRouteValidator(SourceViewer.EndpointValidator routeValidator) {
        this.routeValidator = routeValidator;
    }

    /** Forgets the checks of the file shown. */
    void clear() {
        propertiesValidator = null;
        endpointValidator = null;
        simpleValidator = null;
        kameletValidator = null;
        routeValidator = null;
        notes = null;
    }

    /** The checks of a file just opened, or being typed. */
    Found check(Kind kind, String content) {
        return check(kind, content, false);
    }

    /** The checks of a file being saved: a YAML file is checked against the YAML DSL schema too. */
    Found checkOnSave(Kind kind, String content) {
        return check(kind, content, true);
    }

    private Found check(Kind kind, String content, boolean save) {
        List<String> errors = new ArrayList<>();
        if (kind == Kind.YAML) {
            if (save) {
                errors.addAll(SourceValidationSupport.formatSchemaErrors(validateYaml(content), content));
            }
            addAll(errors, endpointValidator, content);
            addAll(errors, simpleValidator, content);
            addAll(errors, kameletValidator, content);
        } else if (kind == Kind.PROPERTIES) {
            if (propertiesValidator != null) {
                errors.addAll(SourceValidationSupport.validatePropertiesLines(content, propertiesValidator::validate));
            }
            return errors.isEmpty() ? Found.NONE : new Found(errors, List.of());
        } else {
            addAll(errors, routeValidator, content);
        }
        List<String> found = new ArrayList<>();
        if (!save || !new Found(errors, List.of()).blocksSave(kind)) {
            addAll(found, notes, content);
        }
        return errors.isEmpty() && found.isEmpty() ? Found.NONE : new Found(errors, found);
    }

    private static void addAll(List<String> msgs, SourceViewer.EndpointValidator validator, String content) {
        if (validator != null) {
            List<String> found = validator.validate(content);
            if (found != null) {
                msgs.addAll(found);
            }
        }
    }

    private List<Error> validateYaml(String content) {
        try {
            if (yamlValidator == null) {
                yamlValidator = new YamlValidator();
            }
            return yamlValidator.validate(content);
        } catch (Exception e) {
            return List.of();
        }
    }
}
