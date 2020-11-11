package org.apache.camel.model.errorhandler;

import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.spi.Language;

@XmlTransient
public class DeadLetterChannelConfiguration extends DefaultErrorHandlerConfiguration implements DeadLetterChannelProperties {

    // has no additional configurations

    @Override
    public Predicate getRetryWhilePolicy(CamelContext context) {
        Predicate answer = getRetryWhile();

        if (getRetryWhileRef() != null) {
            // its a bean expression
            Language bean = context.resolveLanguage("bean");
            answer = bean.createPredicate(getRetryWhileRef());
        }

        return answer;
    }

}
