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
package org.apache.camel.component.tika;

/**
 * 
 *  Tika Output Format. Supported output formats. 
 * <ul>
 *   <li>xml: Returns Parsed Content as XML. </li>
 *   <li>html: Returns Parsed Content as HTML. </li>
 *   <li>text: Returns Parsed Content as Text. </li>
 *   <li>textMain: Uses the <a href="http://code.google.com/p/boilerpipe/">boilerpipe</a> library to automatically extract the main content from a web page. </li>
 * </ul>
 *
 */
public enum TikaParseOutputFormat {
    xml, html, text, textMain;
}