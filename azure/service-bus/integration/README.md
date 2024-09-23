# How to run tests

Tests are using [AzureCliCredential](https://learn.microsoft.com/en-us/java/api/com.azure.identity.azureclicredential?view=azure-java-stable).
Make sure to be assigned with the [Azure Service Bus Data Owner](https://learn.microsoft.com/en-us/azure/role-based-access-control/built-in-roles/integration#azure-service-bus-data-owner) role.

Steps:
- `az login`
- `export AZURE_SERVICEBUS_HOSTNAME=<your-servicebus-hostname>`
- `sbt "project azureServiceBusIt" test`
