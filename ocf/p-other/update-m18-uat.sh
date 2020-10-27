#!/bin/sh
#
# Script to update m18 program files in jboss, and restart jboss
# Need to run this script as root

if [[ $# -lt 1 ]] ; then
    echo 'This shell script at least take ONE parameter: the client code'
    exit 1
fi

client=$1

#stop wildfly
systemctl stop "wildfly-${client}"

#change dir to jboss directory first
cd "/home/macremote/wildfly-${client}"

#update program files using the files in S:\macshare\JERRY\CE01
cp -rp "/home/macremote/m18update/${client}/ejb"/*.jar standalone/deployments/caw_ear.ear
cp -rp "/home/macremote/m18update/${client}/share"/*.jar standalone/deployments/caw_ear.ear/lib
cp -rp "/home/macremote/m18update/${client}/jsf"/*.jar standalone/deployments/caw_ear.ear/jsf.war/WEB-INF/lib

#change owner of the program files back to macremote
chown macremote:macremote standalone/deployments/caw_ear.ear/*.jar
chown macremote:macremote standalone/deployments/caw_ear.ear/lib/*.jar
chown macremote:macremote standalone/deployments/caw_ear.ear/jsf.war/WEB-INF/lib/*.jar

#start wildfly
systemctl start "wildfly-${client}"
