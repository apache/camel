package org.apache.camel.processor.exceptionpolicy;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;

public class ExceptionPolicy {

    private final OnExceptionDefinition def;

    public ExceptionPolicy(OnExceptionDefinition def) {
        this.def = def;
    }

    public String getId() {
        return def.getId();
    }

    public String getRouteId() {
        return ProcessorDefinitionHelper.getRouteId(def);
    }

    public boolean isRouteScoped() {
        return def.getRouteScoped() != null && def.getRouteScoped();
    }

    public Predicate getOnWhen() {
        return def.getOnWhen() != null ? def.getOnWhen().getExpression() : null;
    }

    public String getRedeliveryPolicyRef() {
        return def.getRedeliveryPolicyRef();
    }

    public boolean hasOutputs() {
        return def.getOutputs() != null && !def.getOutputs().isEmpty();
    }

    public RedeliveryPolicyDefinition getRedeliveryPolicyType() {
        return def.getRedeliveryPolicyType();
    }

    public Predicate getHandledPolicy() {
        return def.getHandledPolicy();
    }

    public Predicate getContinuedPolicy() {
        return def.getContinuedPolicy();
    }

    public Predicate getRetryWhilePolicy() {
        return def.getRetryWhilePolicy();
    }

    public boolean getUseOriginalInMessage() {
        return def.getUseOriginalMessagePolicy() != null && def.getUseOriginalMessagePolicy();
    }

    public Processor getOnRedelivery() {
        return def.getOnRedelivery();
    }

    public Processor getOnExceptionOccurred() {
        return def.getOnExceptionOccurred();
    }

    public List<String> getExceptions() {
        return def.getExceptions();
    }

    public boolean determineIfRedeliveryIsEnabled(CamelContext context) throws Exception {
        return ErrorHandlerReifier.determineIfRedeliveryIsEnabled(this, context);
    }

    public RedeliveryPolicy createRedeliveryPolicy(CamelContext context, RedeliveryPolicy parent) {
        return ErrorHandlerReifier.createRedeliveryPolicy(this, context, parent);
    }
}
