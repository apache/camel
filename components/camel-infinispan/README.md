# Camel Infinispan Component

This component aims at integrating Infinispan 8+ and Infinispan 9+ for both embedded and 
remote (HotRod) usage.

## Integration testing

Please note: the integration tests are disabled in the default Maven profile. 
If you wish to run them, enable the 'infinispan-itests' profile as follows:

    mvn clean verify -Pinfinispan-itests

