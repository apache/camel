package org.apache.camel.component.micrometer.spi;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.internal.TimedExecutorService;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Christian Ohr
 */
public class TimedScheduledExecutorService extends TimedExecutorService implements ScheduledExecutorService {

    private final ScheduledExecutorService delegate;
    private final MeterRegistry registry;

    public TimedScheduledExecutorService(MeterRegistry registry, ScheduledExecutorService delegate, String executorServiceName, Iterable<Tag> tags) {
        super(registry, delegate, executorServiceName, tags);
        this.registry = registry;
        this.delegate = delegate;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return delegate.schedule(meter().wrap(command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return delegate.schedule(meter().wrap(callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return delegate.scheduleAtFixedRate(meter().wrap(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return delegate.scheduleWithFixedDelay(meter().wrap(command), initialDelay, delay, unit);
    }

    private Timer meter() {
        return registry.find("executor").timer();
    }
}
