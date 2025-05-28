FROM nvidia/cuda:12.8.0-runtime-ubuntu22.04
LABEL maintainer="octavio.roncal <octavioroncal@siani.es>, josejuan.hernandez.galvez <jose.galvez@ulpgc.es>"

LABEL version="1.0.0"
LABEL description="Picota Digital Twin"
LABEL targets="Java, Python"
RUN apt-get update && apt-get install -y \
    python3 \
    python3-venv \
    python3-dev \
    python3-pip \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

RUN update-alternatives --install /usr/bin/python python /usr/bin/python3 1 && \
    update-alternatives --install /usr/bin/pip pip /usr/bin/pip3 1

RUN pip install --upgrade pip
WORKDIR /app
RUN pip install --pre torch --index-url https://download.pytorch.org/whl/nightly/cu128
COPY ./requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
ENV PYTHONPATH="${PYTHONPATH}:/app"
COPY out/build/digital-twin/digital-twin.jar /app/
COPY out/build/digital-twin/dependency /app/dependency
COPY docker/run-dt.sh /app/run-dt.sh
RUN chmod a+rx /app/run-dt.sh
WORKDIR /app
EXPOSE 7070
#dckr_pat_OsYJ6AK2KtthuxKc9TjKT5jfkvs
