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
package org.apache.camel.component.salesforce.api.utils;

import java.io.Writer;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.basic.DateConverter;
import com.thoughtworks.xstream.converters.reflection.FieldDictionary;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.TreeMarshallingStrategy;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;
import com.thoughtworks.xstream.security.AnyTypePermission;
import com.thoughtworks.xstream.security.ExplicitTypePermission;
import com.thoughtworks.xstream.security.TypePermission;
import com.thoughtworks.xstream.security.WildcardTypePermission;
import org.apache.camel.component.salesforce.api.dto.AnnotationFieldKeySorter;
import org.apache.camel.component.salesforce.internal.dto.RestChoices;
import org.apache.camel.component.salesforce.internal.dto.RestErrors;

public final class XStreamUtils {
    private static final String PERMISSIONS_PROPERTY_DEFAULT = "java.lang.*,java.util.*";
    private static final String PERMISSIONS_PROPERTY_KEY = "org.apache.camel.xstream.permissions";

    private XStreamUtils() {
    }

    public static void addDefaultPermissions(final XStream xstream) {
        addPermissions(xstream, System.getProperty(PERMISSIONS_PROPERTY_KEY, PERMISSIONS_PROPERTY_DEFAULT));
    }

    public static void addPermissions(final XStream xstream, final String permissions) {
        for (String pterm : permissions.split(",")) {
            boolean aod;
            pterm = pterm.trim();
            if (pterm.startsWith("-")) {
                aod = false;
                pterm = pterm.substring(1);
            } else {
                aod = true;
                if (pterm.startsWith("+")) {
                    pterm = pterm.substring(1);
                }
            }
            TypePermission typePermission = null;
            if ("*".equals(pterm)) {
                // accept or deny any
                typePermission = AnyTypePermission.ANY;
            } else if (pterm.indexOf('*') < 0) {
                // exact type
                typePermission = new ExplicitTypePermission(new String[] {pterm});
            } else if (pterm.length() > 0) {
                // wildcard type
                typePermission = new WildcardTypePermission(new String[] {pterm});
            }
            if (typePermission != null) {
                if (aod) {
                    xstream.addPermission(typePermission);
                } else {
                    xstream.denyPermission(typePermission);
                }
            }
        }
    }

    public static XStream createXStream(final Class<?>... additionalTypes) {
        final PureJavaReflectionProvider reflectionProvider = new PureJavaReflectionProvider(new FieldDictionary(new AnnotationFieldKeySorter()));

        // use NoNameCoder to avoid escaping __ in custom field names
        // and CompactWriter to avoid pretty printing
        final XppDriver hierarchicalStreamDriver = new XppDriver(new NoNameCoder()) {
            @Override
            public HierarchicalStreamWriter createWriter(final Writer out) {
                return new CompactWriter(out, getNameCoder());
            }

        };

        final XStream result = new XStream(reflectionProvider, hierarchicalStreamDriver);
        result.aliasSystemAttribute(null, "class");
        result.ignoreUnknownElements();
        XStreamUtils.addDefaultPermissions(result);

        result.registerConverter(new DateConverter("yyyy-MM-dd'T'HH:mm:ss.SSSZ", null), XStream.PRIORITY_VERY_HIGH);
        result.registerConverter(LocalDateTimeConverter.INSTANCE, XStream.PRIORITY_VERY_HIGH);
        result.registerConverter(OffsetDateTimeConverter.INSTANCE, XStream.PRIORITY_VERY_HIGH);
        result.registerConverter(ZonedDateTimeConverter.INSTANCE, XStream.PRIORITY_VERY_HIGH);
        result.registerConverter(InstantConverter.INSTANCE, XStream.PRIORITY_VERY_HIGH);
        result.registerConverter(OffsetTimeConverter.INSTANCE, XStream.PRIORITY_VERY_HIGH);

        result.setMarshallingStrategy(new TreeMarshallingStrategy());

        result.processAnnotations(RestErrors.class);
        result.processAnnotations(RestChoices.class);
        result.processAnnotations(additionalTypes);

        return result;
    }
}
