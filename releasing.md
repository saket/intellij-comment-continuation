# Releasing

Every push to `trunk` that passes tests is automatically signed and published to the [JetBrains Marketplace](https://plugins.jetbrains.com). Version is generated from the UTC timestamp (e.g., `2026.3.24.1422`).

## First-time setup

The very first version must be uploaded manually:

1. Run `./gradlew buildPlugin`
2. Go to [plugins.jetbrains.com](https://plugins.jetbrains.com) > Add new plugin
3. Upload the zip from `build/distributions/continue-line-comment-<version>.zip`

After that, CI handles everything.

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
