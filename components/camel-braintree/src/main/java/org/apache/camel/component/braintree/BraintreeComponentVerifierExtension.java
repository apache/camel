package org.apache.camel.component.braintree;

import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.OptionsGroup;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;

import java.util.Map;

public class BraintreeComponentVerifierExtension extends DefaultComponentVerifierExtension {

    BraintreeComponentVerifierExtension() {
        super("braintree");
    }

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        // Validate mandatory component options, needed to be done here as these
        // options are not properly marked as mandatory in the catalog.
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS)
                .errors(ResultErrorHelper.requiresAny(parameters,
                        OptionsGroup.withName(AuthenticationType.PUBLIC_PRIVATE_KEYS)
                                .options("environment", "merchantId", "publicKey", "privateKey", "!accessToken"),
                        OptionsGroup.withName(AuthenticationType.ACCESS_TOKEN)
                                .options("!environment", "!merchantId", "!publicKey", "!privateKey", "accessToken")));

        // Validate using the catalog
        super.verifyParametersAgainstCatalog(builder, parameters);

        return builder.build();
    }
}
