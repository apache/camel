package org.apache.camel.opentracing;

import io.opentracing.Span;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A processor which adds a tag on the active {@link io.opentracing.Span} with an {@link org.apache.camel.Expression}
 */
public class TagProcessor extends AsyncProcessorSupport implements Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(TagProcessor.class);

    private String id;
    private String routeId;
    private final Expression tagName;
    private final Expression expression;

    public TagProcessor(Expression tagName, Expression expression) {
        this.tagName = tagName;
        this.expression = expression;
        ObjectHelper.notNull(tagName, "headerName");
        ObjectHelper.notNull(expression, "expression");
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            Span span = ActiveSpanManager.getSpan(exchange);
            if (span != null) {
                String key = tagName.evaluate(exchange, String.class);
                String tag = expression.evaluate(exchange, String.class);
                span.setTag(key, tag);
            } else {
                LOG.warn("OpenTracing: could not find managed span for exchange={}", exchange);
            }
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            // callback must be invoked
            callback.done(true);
        }

        return true;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "tag[" + tagName + ", " + expression + "]";
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getTagName() {
        return tagName.toString();
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
