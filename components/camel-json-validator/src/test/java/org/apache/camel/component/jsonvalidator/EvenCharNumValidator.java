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
package org.apache.camel.component.jsonvalidator;

import com.github.fge.jackson.NodeType;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.format.AbstractFormatAttribute;
import com.github.fge.jsonschema.format.FormatAttribute;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.msgsimple.bundle.MessageBundle;

public final class EvenCharNumValidator extends AbstractFormatAttribute {
    
    private static FormatAttribute instance = new EvenCharNumValidator();

    private EvenCharNumValidator() {
        super("evenlength", NodeType.STRING);
    }

    public static FormatAttribute getInstance() {
        return instance;
    }

    @Override
    public void validate(ProcessingReport report, MessageBundle bundle, FullData data) throws ProcessingException {
        final String value = data.getInstance().getNode().textValue();
        if (value.length() % 2 != 0) {
            report.error(newMsg(data, bundle, "oddlength").put("evenlength", value));
        }
    }
}