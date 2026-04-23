FROM eclipse-temurin:21-jdk AS builder

RUN wget "https://github.com/sbt/sbt/releases/download/v1.12.9/sbt-1.12.9.tgz" -O /tmp/sbt.tgz && \
    tar -xzf /tmp/sbt.tgz -C /usr/local

ENV PATH="/usr/local/sbt/bin:$PATH"

WORKDIR /build

COPY build.sbt .
COPY project/ project/
RUN sbt update

COPY . .
RUN sbt stage


FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=builder /build/target/universal/stage .

EXPOSE 9000

ENTRYPOINT ["bin/wavesfaucet"]
