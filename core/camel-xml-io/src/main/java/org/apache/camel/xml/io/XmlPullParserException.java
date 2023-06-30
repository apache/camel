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

/**
 * This exception is thrown to signal XML Pull Parser related faults.
 *
 * @author <a href="http://www.extreme.indiana.edu/~aslom/">Aleksander Slominski</a>
 */
public class XmlPullParserException extends Exception {
    protected int row = -1;

    protected int column = -1;

    public XmlPullParserException(String s) {
        super(s);
    }

    /*
     * public XmlPullParserException(String s, Throwable throwable) { super(s);
     * this.detail = throwable; } public XmlPullParserException(String s, int
     * row, int column) { super(s); this.row = row; this.column = column; }
     */

    public XmlPullParserException(String msg, XmlPullParser parser, Throwable cause) {
        super((msg == null ? "" : msg + " ") + (parser == null ? "" : "(position:" + parser.getPositionDescription() + ") ")
              + (cause == null ? "" : "caused by: " + cause), cause);

        if (parser != null) {
            this.row = parser.getLineNumber();
            this.column = parser.getColumnNumber();
        }
        if (cause != null) {
            initCause(cause);
        }
    }

    public int getLineNumber() {
        return row;
    }

    public int getColumnNumber() {
        return column;
    }

}
