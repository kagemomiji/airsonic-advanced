ARG IMAGE_JAVA_VERSION=21.0.7_6
FROM eclipse-temurin:${IMAGE_JAVA_VERSION}-jdk AS builder
WORKDIR /target

COPY run.sh /usr/local/bin/run.sh
RUN chmod +x /usr/local/bin/run.sh
COPY entry.sh /usr/local/bin/entry.sh
RUN chmod +x /usr/local/bin/entry.sh

COPY target/dependency/airsonic-main.war airsonic.war
RUN jar -xf ./airsonic.war
RUN mv WEB-INF/lib-provided lib-provided
RUN mv WEB-INF/lib lib
RUN mv WEB-INF/classes/META-INF cMETA-INF
RUN mv WEB-INF/classes/liquibase cliquibase

FROM eclipse-temurin:${IMAGE_JAVA_VERSION}-jre-jammy

LABEL description="Airsonic-Advanced is a free, web-based media streamer, providing ubiquitous access to your music." \
      url="https://github.com/kagemomiji/airsonic-advanced"

ENV AIRSONIC_PORT=4040 AIRSONIC_DIR=/var CONTEXT_PATH=/ UPNP_PORT=4041 PUID=0 PGID=0

WORKDIR $AIRSONIC_DIR

RUN apt-get update \
    && apt-get install -y \
                       ffmpeg \
                       x264 \
                       x265 \
                       lame \
                       xmp \
                       bash \
                       fonts-dejavu \
                       gosu \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*


COPY --from=builder /usr/local/bin/run.sh /usr/local/bin/run.sh
COPY --from=builder /usr/local/bin/entry.sh /usr/local/bin/entry.sh

COPY --from=builder /target/cMETA-INF /app/WEB-INF/classes/META-INF
COPY --from=builder /target/lib /app/WEB-INF/lib
COPY --from=builder /target/lib-provided /app/WEB-INF/lib-provided
COPY --from=builder /target/org /app/org
COPY --from=builder /target/cliquibase /app/WEB-INF/classes/liquibase
COPY --from=builder /target/META-INF /app/META-INF
COPY --from=builder /target/WEB-INF /app/WEB-INF

EXPOSE $AIRSONIC_PORT

# Default DLNA/UPnP ports
EXPOSE $UPNP_PORT
EXPOSE 1900/udp

USER ${PUID}:${PGID}

VOLUME $AIRSONIC_DIR/airsonic $AIRSONIC_DIR/music $AIRSONIC_DIR/playlists $AIRSONIC_DIR/podcasts

HEALTHCHECK --interval=15s --timeout=3s CMD curl -f http://localhost:"$AIRSONIC_PORT""$CONTEXT_PATH"rest/ping || false

ENTRYPOINT ["entry.sh"]
