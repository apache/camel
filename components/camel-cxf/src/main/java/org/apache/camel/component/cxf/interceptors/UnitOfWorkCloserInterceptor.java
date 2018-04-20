package org.apache.camel.component.cxf.interceptors;

import org.apache.camel.component.cxf.util.CxfUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.OutgoingChainInterceptor;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.model.BindingOperationInfo;

//closes UnitOfWork in good case
public class UnitOfWorkCloserInterceptor extends AbstractPhaseInterceptor<Message> {
    boolean handleOneWayMessage;

    public UnitOfWorkCloserInterceptor(String phase, boolean handleOneWayMessage) {
        super(phase);
        // Just make sure this interceptor is add after the OutgoingChainInterceptor
        if (phase.equals(Phase.POST_INVOKE)) {
            addAfter(OutgoingChainInterceptor.class.getName());
        }
        this.handleOneWayMessage = handleOneWayMessage;
    }
    public UnitOfWorkCloserInterceptor() {
        super(Phase.POST_LOGICAL_ENDING);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        if (handleOneWayMessage) {
            if (isOneWay(message)) {
                CxfUtils.closeCamelUnitOfWork(message);
            }
        } else { // Just do the normal process
            CxfUtils.closeCamelUnitOfWork(message);
        }
    }

    private boolean isOneWay(Message message) {
        Exchange ex = message.getExchange();
        BindingOperationInfo binding = ex.getBindingOperationInfo();
        if (null != binding && null != binding.getOperationInfo() && binding.getOperationInfo().isOneWay()) {
           return true;
        }
        return false;
    }
}
