This local image extends QingdaoU JudgeServer 1.6.1. Its build patches
`/app/judge_client.py` in the base image and fails if the expected scheduler is
absent. The patch limits each request to one sandbox worker and cancels remaining
test cases when a CPU or wall-clock timeout is returned. The sandbox itself still
enforces `max_cpu_time` for each case.

Gunicorn uses threads to serve requests. The sandbox pool starts its worker with
Python's `spawn` context so it does not inherit locks from those threads.

The judge service runs two RabbitMQ consumers. With one sandbox worker per
request, this caps the single Compose deployment at two simultaneous judging
workers; excess submissions stay in `judge.request.queue.v2`. The legacy queue
is consumed during migration, while new requests use the durable v2 queue
without a two-minute TTL. Scaling the judge service to multiple replicas would
require a shared concurrency limit.

Thanks to the [QingdaoU Online Judge team](https://github.com/QingdaoU/JudgeServer)
for the JudgeServer source and its SATA license.
