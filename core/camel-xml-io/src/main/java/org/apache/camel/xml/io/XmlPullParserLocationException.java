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

/* -*-             c-basic-offset: 4; indent-tabs-mode: nil; -*-  //------100-columns-wide------>|*/
// for license please see accompanying LICENSE.txt file (available also at http://www.xmlpull.org/)
package org.apache.camel.xml.io;

import org.apache.camel.spi.Resource;
import org.apache.camel.util.IOHelper;

/**
 * This exception is thrown to signal XML Pull Parser related faults.
 */
public class XmlPullParserLocationException extends RuntimeException {
    private final int row;
    private final int column;
    private final Resource resource;

    public XmlPullParserLocationException(String s, Resource resource, int row, int column, Throwable cause) {
        super(createMessage(s, resource, row, column, cause), cause);
        this.row = row;
        this.column = column;
        this.resource = resource;
    }

    public int getLineNumber() {
        return row;
    }

    public int getColumnNumber() {
        return column;
    }

    public Resource getResource() {
        return resource;
    }

    private static String createMessage(String s, Resource resource, int row, int column, Throwable cause) {
        StringBuilder sb = new StringBuilder();
        sb.append(cause.getMessage()).append("\n");
        if (resource != null) {
            sb.append("in ").append(resource.getLocation()).append(", line ")
                    .append(row).append(", column ").append(column)
                    .append(":\n");
            try {
                String line = IOHelper.loadTextLine(resource.getInputStream(), row);
                if (line != null) {
                    sb.append(line).append("\n");
                    if (column > 1) {
                        String pad = " ".repeat(column - 2);
                        sb.append(pad);
                    }
                    sb.append("^\n");
                }
            } catch (Exception e) {
                // ignore
            }
            sb.append("\n");
        }
        return sb.toString();
    }

}
