#/bin/bash
DOWNLOADED=0
CERT_URL='https://certs.godaddy.com/anonymous/repository.pki?streamfilename=gdroot-g2.crt&actionMethod=anonymous%2Frepository.xhtml%3Arepository.streamFile%28%27%27%29&cid=1601132'
HAS_CURL=command -v curl >/dev/null 2>&1
if [ -n HAS_CURL ]; then
  curl -o gdroot-g2.crt "$CERT_URL"
  DOWNLOADED=1
else
  echo 'Could not find wget or curl.  Download this in your browser:'
  echo $CERT_URL
  exit 1
fi
HAS_WGET=command -v wget >/dev/null 2>&1
if [ DOWNLOADED == 0 -a -n HAS_WGET ]; then
  wget -O gdroot-g2.crt "$CERT_URL"
fi

sudo $JAVA_HOME/bin/keytool -import -file gdroot-g2.crt -alias gdrootg2 -storepass changeit -trustcacerts -keystore ${JAVA_HOME}/jre/lib/security/cacerts
