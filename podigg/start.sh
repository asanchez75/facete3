#!/bin/sh
mkdir -p /srv/httpd/podigg

./generate-env /srv/httpd/podigg/latest
http-server -p80 /srv/httpd

