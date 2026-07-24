FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY contract/pom.xml contract/pom.xml
COPY account-service/pom.xml account-service/pom.xml
COPY event-gateway/pom.xml event-gateway/pom.xml
COPY contract/src contract/src
COPY event-gateway/src event-gateway/src

RUN mvn -pl event-gateway -am package -DskipTests

FROM eclipse-temurin:25-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --create-home --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /workspace/event-gateway/target/event-gateway-*.jar app.jar
RUN mkdir /app/data && chown -R app:app /app

USER 10001:10001
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 CMD curl --fail --silent http://localhost:8080/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
