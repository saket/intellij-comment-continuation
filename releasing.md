# Releasing

Pushes to `trunk` run CI only. Publishing to the [JetBrains Marketplace](https://plugins.jetbrains.com)
happens only when the workflow is triggered manually from GitHub Actions.

Published versions are generated automatically from the current UTC timestamp,
for example `2026.4.2.1430`.

Local builds use the dummy version `0.0.0-dev` and cannot be published.

## First-time setup

The very first version must be uploaded manually:

1. Run `./gradlew buildPlugin`
2. Find the built plugin in `build/distributions/`
3. Go to [plugins.jetbrains.com](https://plugins.jetbrains.com) > Add new plugin
4. Upload the zip from `build/distributions/comment-continuation-<version>.zip`

After that, CI handles everything.

## Release flow

1. Merge changes to `trunk`
2. Open GitHub Actions
3. Run the `Publish` workflow manually on `trunk`

CI publishes the plugin with a UTC timestamp version.

## Secrets

Add these to GitHub repo settings > Secrets and variables > Actions:

### `PUBLISH_TOKEN`

Generate at [plugins.jetbrains.com](https://plugins.jetbrains.com) > Profile > Personal access tokens.

### `CERTIFICATE_CHAIN` and `PRIVATE_KEY`

Generate a self-signed certificate:

```bash
openssl genpkey -aes-256-cbc -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:4096
openssl req -key private.pem -new -x509 -days 3650 -out chain.crt
```

Base64-encode and save as secrets:

```bash
base64 -i chain.crt        # → CERTIFICATE_CHAIN
base64 -i private.pem      # → PRIVATE_KEY
```

### `PRIVATE_KEY_PASSWORD`

The passphrase you chose when generating the key.
