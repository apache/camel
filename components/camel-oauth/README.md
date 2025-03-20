
# Camel OAuth (Preview)

The camel-oauth module comes with a set of Processors that can be added to a route on the client and resource owner side
like [this|https://github.com/tdiesler/camel-cloud-examples/blob/main/camel-main/platform-http-oauth/platform-http-files/platform-http-route.yaml] ...

## Supported OIDC, OAuth functionality

* OIDC Authentication using Authorization Code Flow (OAuthCodeFlowProcessor, OAuthCodeFlowCallback)
* OAuth Client Credentials Grant (OAuthClientCredentialsProcessor, OAuthBearerTokenProcessor)
* Identity Provider (Keycloak only) logout (OAuthLogoutProcessor)

For details, see these specs ...

* [OAuth 2.0|https://datatracker.ietf.org/doc/html/rfc6749]
* [OIDC 1.0|https://openid.net/specs/openid-connect-core-1_0.html]

Our Identity Provider (Keycloak) can be deployed to a local k8s cluster with a [helm chart|./helm] that comes with this project.

Respective jbang projects live [here|https://github.com/tdiesler/camel-cloud-examples/tree/main/camel-main]

For Kafka we use strimzi kafka-oauth-client directly, which is documented [here|https://github.com/tdiesler/camel-cloud-examples/blob/main/camel-main/kafka-oauth/kafka-oauth-route.yaml].
