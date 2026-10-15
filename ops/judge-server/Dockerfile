FROM registry.cn-hongkong.aliyuncs.com/oj-image/judge:1.6.1

COPY patch_judge_client.py /tmp/patch_judge_client.py
RUN /app/.venv/bin/python3 /tmp/patch_judge_client.py /app/judge_client.py && \
    /app/.venv/bin/python3 -m py_compile /app/judge_client.py
