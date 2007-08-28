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
package org.apache.camel.model.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.processor.Resequencer;

/**
 * Defines the configuration parameters for the batch-processing
 * {@link Resequencer}. Usage example:
 * 
 * <pre>
 * from(&quot;direct:start&quot;).resequencer(body()).batch(
 *         BatchResequencerConfig.getDefault()).to(&quot;mock:result&quot;)
 * </pre>
 * is equivalent to
 * 
 * <pre>
 * from(&quot;direct:start&quot;).resequencer(body()).batch().to(&quot;mock:result&quot;)
 * </pre>
 * 
 * or
 * 
 * <pre>
 * from(&quot;direct:start&quot;).resequencer(body()).to(&quot;mock:result&quot;)
 * </pre>
 * 
 * Custom values for <code>batchSize</code> and <code>batchTimeout</code>
 * can be set like in this example:
 * 
 * <pre>
 * from(&quot;direct:start&quot;).resequencer(body()).batch(
 *         new BatchResequencerConfig(300, 400L)).to(&quot;mock:result&quot;)
 * </pre>
 * 
 * @author Martin Krasser
 * 
 * @version $Revision$
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class BatchResequencerConfig {

    @XmlAttribute
    private Integer batchSize; // optional XML attribute requires wrapper object 

    @XmlAttribute
    private Long batchTimeout; // optional XML attribute requires wrapper object

    /**
     * Creates a new {@link BatchResequencerConfig} instance using default
     * values for <code>batchSize</code> (100) and <code>batchTimeout</code>
     * (1000L).
     */
    public BatchResequencerConfig() {
        this(100, 1000L);
    }
    
    /**
     * Creates a new {@link BatchResequencerConfig} instance using the given
     * values for <code>batchSize</code> and <code>batchTimeout</code>.
     * 
     * @param batchSize
     *            size of the batch to be re-ordered.
     * @param batchTimeout
     *            timeout for collecting elements to be re-ordered.
     */
    public BatchResequencerConfig(int batchSize, long batchTimeout) {
        this.batchSize = batchSize;
        this.batchTimeout = batchTimeout;
    }
    
    /**
     * Returns a new {@link BatchResequencerConfig} instance using default
     * values for <code>batchSize</code> (100) and <code>batchTimeout</code>
     * (1000L).
     * 
     * @return a default {@link BatchResequencerConfig}.
     */
    public static BatchResequencerConfig getDefault() {
        return new BatchResequencerConfig();
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getBatchTimeout() {
        return batchTimeout;
    }

    public void setBatchTimeout(long batchTimeout) {
        this.batchTimeout = batchTimeout;
    }
    
}
