FROM python:3.11-slim
ENV PYTHONUNBUFFERED=1 HF_HUB_DISABLE_XET=1 HF_HUB_DISABLE_TELEMETRY=1
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY server.py test_server.py ./
EXPOSE 8090
CMD ["python", "server.py"]
