# syntax=docker.io/docker/dockerfile:1.7-labs@sha256:b99fecfe00268a8b556fad7d9c37ee25d716ae08a5d7320e6d51c4dd83246894
ARG HOME="/home/unitycatalog"

# Build stage, using Amazon Corretto jdk 17 on alpine with arm64 support
FROM amazoncorretto:17-alpine3.20-jdk@sha256:c045f0537bc890f9e61924f33f35e9667f696b4f372dad4a73861a9396b5d0b5 as base

ARG HOME
ENV HOME=$HOME

# Corporate Maven mirror for the sbt launcher / Ivy (see build/sbt).
# Pass at build time: --build-arg MAVEN_PROXY_URL=$MAVEN_PROXY_URL
ARG MAVEN_PROXY_URL
ENV MAVEN_PROXY_URL=${MAVEN_PROXY_URL}

WORKDIR $HOME

COPY --parents dev/ build/ project/ examples/ server/ api/ clients/ bin/ etc/ version.sbt build.sbt ./

# Build the self-contained deployment tarball. It bundles every runtime jar
# under jars/ together with a portable jars/classpath file (paths relative to
# the tarball root), the launcher scripts (bin/), and default config (etc/).
# This avoids the fragile absolute-cache-path classpath that `sbt package`
# alone produces, which does not survive the copy into the runtime image.
RUN apk add --no-cache bash && ./build/sbt -info clean createTarball

# Unpack the tarball into a staging dir so the runtime stage copies a clean,
# self-contained tree (bin/, etc/, jars/) with no build cache or sources.
RUN mkdir -p /uc-dist && \
    tar -xzf "$(ls target/unitycatalog-*.tar.gz | head -n 1)" -C /uc-dist

# Small runtime image
FROM alpine:3.20@sha256:a4f4213abb84c497377b8544c81b3564f313746700372ec4fe84653e4fb03805 as runtime

# Specific JAVA_HOME from Amazon Corretto
ARG JAVA_HOME="/usr/lib/jvm/default-jvm"
ARG USER="unitycatalog"
ARG HOME

# Copy Java from base
COPY --from=base $JAVA_HOME $JAVA_HOME

ENV HOME=$HOME \
    JAVA_HOME=$JAVA_HOME \
    PATH="${JAVA_HOME}/bin:${PATH}"

# Copy the unpacked, self-contained distribution (bin/, etc/, jars/).
COPY --from=base /uc-dist/ $HOME/

# Create a service user with read and execute permissions and write permissions of the ./etc directory
RUN <<EOF
apk add --no-cache bash
addgroup -S $USER
adduser -S -G $USER $USER
chmod -R 550 $HOME
chmod -R 770 $HOME/etc/
chown -R $USER:$USER $HOME
EOF

USER $USER

WORKDIR $HOME

CMD ["./bin/start-uc-server"]
