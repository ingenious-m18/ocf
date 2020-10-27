#!/bin/sh
#
# Script to setup m18 wildfly (jboss) as a service in CentOS 7
# Need to run this script as root

cp wildfly-init-redhat.sh /usr/bin/wildfly
chmod +x /usr/bin/wildfly

cp wildfly.conf /etc/default/wildfly.conf

cp wildfly.service /usr/lib/systemd/system/wildfly.service

systemctl daemon-reload
systemctl enable wildfly
