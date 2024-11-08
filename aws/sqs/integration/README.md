# How to run tests

Tests are using [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-quickstart.html).
Make sure to have the necessary permissions for accessing AWS resources: [AmazonSQSFullAccess](https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AmazonSQSFullAccess.html).

Steps:
- `aws configure`
- `sbt "project awsSqsIt" test`
