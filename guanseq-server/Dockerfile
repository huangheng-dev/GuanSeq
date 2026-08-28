FROM eclipse-temurin:25.0.3_9-jdk-noble AS builder
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,id=guanseq-maven,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src src
COPY docker docker
RUN --mount=type=cache,id=guanseq-maven,target=/root/.m2 \
    ./mvnw -B -DskipTests package \
    && javac -d /workspace/health docker/GuanSeqHttpHealthCheck.java

FROM eclipse-temurin:25.0.3_9-jre-noble AS runtime
RUN groupadd --system --gid 10001 guanseq \
    && useradd --system --uid 10001 --gid guanseq --home-dir /nonexistent --shell /usr/sbin/nologin guanseq
WORKDIR /opt/guanseq
COPY --from=builder --chown=guanseq:guanseq /workspace/target/guanseq-server-*.jar ./guanseq-server.jar
COPY --from=builder --chown=guanseq:guanseq /workspace/health /opt/guanseq/health
USER guanseq
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=4s --start-period=45s --retries=9 \
  CMD ["java", "-cp", "/opt/guanseq/health", "GuanSeqHttpHealthCheck", "http://127.0.0.1:8080/api/v1/platform/status"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/opt/guanseq/guanseq-server.jar"]
