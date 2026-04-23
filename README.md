```
docker build -t waves-faucet .

docker run -p 9000:9000 --rm waves-faucet \
    -Dfaucet.chain-id=T \
    -Dfaucet.waves-node-url=https://nodes-testnet.wavesnodes.com \
    -Dfaucet.private-key=YOUR_BASE58_PRIVATE_KEY \
    -Dfaucet.pay-amount=1000000000 \
    -Dfaucet.recaptcha-secret=YOUR_SECRET" \
 ```
