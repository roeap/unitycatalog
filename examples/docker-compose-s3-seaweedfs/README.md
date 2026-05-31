# Unity Catalog + SeaweedFS (local S3)

This example runs Unity Catalog against a local [SeaweedFS](https://github.com/seaweedfs/seaweedfs)
instance acting as an S3-compatible object store. It needs **no AWS account and
no network access** — everything runs in Docker.

It's intended for demos and local development of features that depend on S3
storage (managed tables, credential vending, the Iceberg/Delta read paths).

## How it works

| Piece | Role |
| --- | --- |
| `seaweedfs` | S3-compatible store, S3 API on `:8333`, filer UI on `:8888` |
| `create-bucket` | one-shot job that creates the `uc-demo` bucket, then exits |
| `server` | Unity Catalog server on `:8080`, pointed at SeaweedFS |

The S3 endpoint is provided to the server via the standard
`AWS_ENDPOINT_URL_S3` environment variable (`http://seaweedfs:8333`), which the
AWS SDK for Java v2 reads automatically. Unity Catalog additionally enables S3
**path-style addressing** whenever that variable is set, because SeaweedFS (like
MinIO) requires path-style (`http://host/bucket/key`) rather than virtual-hosted
style (`http://bucket.host/key`).

### Credential model — why static credentials, not STS

Unity Catalog normally vends temporary credentials by having a *master* IAM role
assume a customer *storage* role through **AWS STS** (the external-location +
storage-credential path). **SeaweedFS does not implement STS**, so that path
cannot work here.

Instead this example uses the legacy per-bucket **static** credential path in
`etc/conf/server.properties`: `s3.bucketPath.0`, `s3.accessKey.0`,
`s3.secretKey.0`, and a non-empty `s3.sessionToken.0`. The session token makes
Unity Catalog return those static credentials directly, with no STS call.

> **Note:** Do not register a Unity Catalog *external location* over the bucket
> in this setup. Doing so would route credential vending back through the STS
> path, which SeaweedFS cannot serve. Managed tables work because they fall
> through to the static per-bucket configuration.

This is intentionally a **soft/demo-grade** integration, not a production
deployment pattern.

## Run it

```bash
cd examples/docker-compose-s3-seaweedfs
docker compose up
```

Wait until SeaweedFS is healthy, the `create-bucket` job has completed, and the
server logs show it is listening on `:8080`.

## Walkthrough

Using the bundled Unity Catalog CLI (adjust host/port if you changed them):

```bash
# Create a catalog whose storage root lives in the SeaweedFS bucket.
bin/uc catalog create --name demo \
  --storage_root s3://uc-demo/demo

# Create a schema.
bin/uc schema create --catalog demo --name sales

# Create a MANAGED table on S3 (data is written to SeaweedFS).
bin/uc table create \
  --full_name demo.sales.orders \
  --columns "id INT, amount DOUBLE" \
  --format DELTA

# Write/read using your engine of choice (e.g. the Spark example in this repo),
# then confirm the objects landed in SeaweedFS.
```

### Confirm objects in SeaweedFS

```bash
# List what UC wrote (managed data lives under __unitystorage/).
aws --endpoint-url http://localhost:8333 s3 ls --recursive s3://uc-demo/
```

You can also browse the filer UI at <http://localhost:8888>.

## Tear down

```bash
docker compose down -v   # -v also removes the SeaweedFS + UC data volumes
```
