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
    wget \
    gnupg \
    && rm -rf /var/lib/apt/lists/*

RUN wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | apt-key add - && \
    echo "deb https://packages.adoptium.net/artifactory/deb jammy main" > /etc/apt/sources.list.d/adoptium.list && \
    apt-get update && \
    apt-get install -y temurin-21-jdk && \
    rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN update-alternatives --install /usr/bin/python python /usr/bin/python3 1 && \
    update-alternatives --install /usr/bin/pip pip /usr/bin/pip3 1

RUN pip install --upgrade pip
WORKDIR /app
RUN pip install --pre torch --index-url https://download.pytorch.org/whl/cu128
COPY ./requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
ENV PYTHONPATH="${PYTHONPATH}:/app"
COPY out/build/digital-twin/picota-dt.jar /app/
COPY out/build/digital-twin/dependency /app/dependency
WORKDIR /app
ENTRYPOINT ["java", "-jar", "/app/picota-dt.jar", "python_venv=/usr", "api_port=7070", "home=/workspace"]
EXPOSE 7070

