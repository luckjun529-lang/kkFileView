# Reuse the published runtime because the upstream base repository is unavailable.
FROM luckjun529/kkfileview:5.0.4@sha256:fddb5b95c86811cdd036e7dcd6fbc675620ef7aa8c7fe08ffebb50fdd908fc49
RUN rm -rf /opt/kkFileView-5.0.4
ADD server/target/kkFileView-5.0.5.tar.gz /opt/
ENV KKFILEVIEW_BIN_FOLDER=/opt/kkFileView-5.0.5/bin
ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-Dspring.config.location=/opt/kkFileView-5.0.5/config/application.properties","-jar","/opt/kkFileView-5.0.5/bin/kkFileView-5.0.5.jar"]
