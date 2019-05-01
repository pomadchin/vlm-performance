#!/bin/bash

set -ex

S3_URI=$1
RPM_URI=$S3_URI/rpms/$2

# Parses a configuration file put in place by EMR to determine the role of this node
is_master() {
  if [ $(jq '.isMaster' /mnt/var/lib/info/instance.json) = 'true' ]; then
    return 0
  else
    return 1
  fi
}

if is_master; then

    # Download packages
    mkdir -p /tmp/blobs/
    aws s3 sync $RPM_URI /tmp/blobs/

    # Install binary packages
    (cd /tmp/blobs; sudo yum localinstall -y /tmp/blobs/*.rpm)

    # Linkage
    echo '/usr/local/lib' > /tmp/local.conf
    echo '/usr/local/lib64' >> /tmp/local.conf
    sudo cp /tmp/local.conf /etc/ld.so.conf.d/local.conf
    sudo ldconfig
    rm -f /tmp/local.conf

    # Environment setup
    cat <<EOF > /tmp/extra_profile.sh
export AWS_DNS_NAME=$(aws ec2 describe-network-interfaces --filters Name=private-ip-address,Values=$(hostname -i) | jq -r '.[] | .[] | .Association.PublicDnsName')
export LD_LIBRARY_PATH=/usr/local/lib:$LD_LIBRARY_PATH
EOF
    sudo mv /tmp/extra_profile.sh /etc/profile.d
    . /etc/profile.d/extra_profile.sh

else

    # Download packages
    mkdir -p /tmp/blobs/
    aws s3 sync $RPM_URI /tmp/blobs/

    # Install binary packages
    (cd /tmp/blobs; sudo yum localinstall -y /tmp/blobs/*.rpm)

    # Linkage
    echo '/usr/local/lib' > /tmp/local.conf
    echo '/usr/local/lib64' >> /tmp/local.conf
    sudo cp /tmp/local.conf /etc/ld.so.conf.d/local.conf
    sudo ldconfig
    rm -f /tmp/local.conf
fi
