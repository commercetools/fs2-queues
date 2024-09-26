# How to run tests

Tests are using [Application Default Credentials](https://cloud.google.com/docs/authentication/application-default-credentials).
Make sure your service account is assigned with the right scopes, e.g.: `roles/pubsub.admin`, `roles/monitoring.viewer`.
Steps:
- `export GOOGLE_APPLICATION_CREDENTIALS="~/keyfile.json"`
- `export GCP_PUBSUB_USE_EMULATOR=false`
- `export GCP_PUBSUB_PROJECT=<your-project>`
- `sbt "project gcpPubSubIt" test`
