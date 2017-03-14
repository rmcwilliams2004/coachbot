#!/bin/bash
echo '************* This script has only been tested with a brand new cert --'
echo '** WARNING ** if trying a renewal make sure the file is generated to the right place'
echo '*************'
echo NOTE: Use a separate terminal to update the LETSENCRYPT_CHALLENGE and
echo LETSENCRYPT_CHALLENGE_RESPONSE to use the fields in the interactive
echo generator during this process.
read -n 1 -p "Hit any key to continue..."
sudo certbot certonly -d coachbot.couragelabs.com --manual
sudo heroku certs:update /etc/letsencrypt/live/coachbot.couragelabs.com/fullchain.pem /etc/letsencrypt/live/coachbot.couragelabs.com/privkey.pem
