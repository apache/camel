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
package org.apache.camel.spi;

import org.w3c.dom.Element;

/**
 * If a configuration bean needs to be aware of the point in the XML file
 * in which its defined then it can implement this method to have the
 * XML {@link Element} node injected so that it can grab the namespace context
 * or look at local comments etc.
 *
 * @version $Revision: 1.1 $
 */
public interface ElementAware {
    /**
     * Injects the XML Element which defines this bean so that it can
     * analyse the namespace context or grab the local comments etc.
     *
     * @param element the XML element
     */
    void setElement(Element element);
}
