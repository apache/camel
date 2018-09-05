The test certificates have been generated with the following commands:

Creating Key Store:
-----------------------

$ keytool -genkeypair -alias certificatekey -keyalg RSA -validity 3650 -keystore keystore.jks

$ keytool -export -alias certificatekey -keystore keystore.jks -rfc -file cert.cer

Creating Trust Store:
------------------------

$ keytool -import -alias certificatekey -file cert.cer -keystore truststore.jks
