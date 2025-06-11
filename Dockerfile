FROM io.picota.base-1.0.0:amd
LABEL maintainer="octavio.roncal <octavioroncal@siani.es>, josejuan.hernandez.galvez <jose.galvez@ulpgc.es>"
LABEL version="1.0.0"
LABEL description="Picota Digital Twin"
LABEL targets="Java, Python"

WORKDIR /app
COPY out/build/digital-twin/picota-dt.jar /app/
COPY out/build/digital-twin/dependency /app/dependency
WORKDIR /app
ENTRYPOINT ["java", "-jar", "/app/picota-dt.jar", "python_venv=/usr", "api_port=7070", "home=/workspace"]
EXPOSE 7070

