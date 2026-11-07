# Tandem Phone

A tiny Android companion for [Tandem](https://github.com/) that sends your daily screen time to Tandem's journal.

- Reads Android's own usage stats (the same data Digital Wellbeing uses) after you switch on **usage access** for it.
- Every hour it sends today's and yesterday's totals, plus the top apps, to your Tandem with a private key from
  Tandem › Settings › Connected apps › Phone screen time. The key can only write your screen time; it can't read anything.
- No accounts, ads or third parties.

## Install
Download `tandem-phone.apk` from the latest release and open it. Android asks once to allow installs from your browser.

## Build
GitHub Actions builds and signs a release on every push to `main` (`.github/workflows/build.yml`).
Signing uses the `TP_KEYSTORE_B64` and `TP_STORE_PASS` repo secrets so every build updates over the previous one.
Locally: `gradle :app:assembleRelease` (debug-signed without those).
