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
package org.apache.camel.processor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.resequencer.ResequencerEngine;
import org.apache.camel.processor.resequencer.SequenceElementComparator;
import org.apache.camel.processor.resequencer.SequenceSender;

/**
 * A resequencer that re-orders a (continuous) stream of {@link Exchange}s. The
 * algorithm implemented by {@link ResequencerEngine} is based on the detection
 * of gaps in a message stream rather than on a fixed batch size. Gap detection
 * in combination with timeouts removes the constraint of having to know the
 * number of messages of a sequence (i.e. the batch size) in advance.
 * <p>
 * Messages must contain a unique sequence number for which a predecessor and a
 * successor is known. For example a message with the sequence number 3 has a
 * predecessor message with the sequence number 2 and a successor message with
 * the sequence number 4. The message sequence 2,3,5 has a gap because the
 * sucessor of 3 is missing. The resequencer therefore has to retain message 5
 * until message 4 arrives (or a timeout occurs).
 * 
 * @author Martin Krasser
 * 
 * @version $Revision$
 */
public class StreamResequencer extends DelegateProcessor implements Processor {

    private ResequencerEngine<Exchange> reseq;
    private BlockingQueue<Exchange> queue;
    private SequenceSender sender;
    
    /**
     * Creates a new {@link StreamResequencer} instance.
     * 
     * @param processor
     *            the next processor that processes the re-ordered exchanges.
     * @param comparator
     *            a {@link SequenceElementComparator} for comparing sequence
     *            number contained in {@link Exchange}s.
     * @param capacity
     *            the capacity of the inbound queue.
     */
    public StreamResequencer(Processor processor, SequenceElementComparator<Exchange> comparator, int capacity) {
        super(processor);
        queue = new LinkedBlockingQueue<Exchange>();
        reseq = new ResequencerEngine<Exchange>(comparator, capacity);
        reseq.setOutQueue(queue);
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        sender = new SequenceSender(getProcessor());
        sender.setQueue(queue);
        sender.start();

    }

    @Override
    protected void doStop() throws Exception {
        reseq.stop();
        sender.cancel();
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        reseq.put(exchange);
    }

    public long getTimeout() {
        return reseq.getTimeout();
    }

    public void setTimeout(long timeout) {
        reseq.setTimeout(timeout);
    }

    @Override
    public String toString() {
        return "StreamResequencer[to: " + getProcessor() + "]";
    }

}
