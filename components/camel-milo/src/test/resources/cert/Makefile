.PHONY: clean all show

all:
	openssl req -batch -x509 -sha256 -nodes -days 36500 \
		-subj '/C=XX/L=End of the universe/O=Milliways' \
		-config cert.ini \
		-newkey rsa:2048 -keyout privateKey.key -out certificate.crt
	openssl pkcs12 -password pass:pwd1 -export -out cert.p12 -inkey privateKey.key -in certificate.crt
	openssl x509 -outform der -in certificate.crt -out certificate.der

show:
	openssl x509 -in certificate.crt -text -noout

clean:
	@-rm privateKey.key certificate.crt cert.p12 certificate.der

