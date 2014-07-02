package org.apache.camel.processor;

import java.util.concurrent.RejectedExecutionException;

/**
 * Created by david on 02/07/14.
 */
public class ThrottlerRejectedExecutionException
    extends RejectedExecutionException
{
    public ThrottlerRejectedExecutionException(String message) {
        super(message);
    }
}

