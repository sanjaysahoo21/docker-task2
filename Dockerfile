FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Install OpenSSL for decrypting encrypted seed
RUN apt-get update && apt-get install -y --no-install-recommends openssl \
    && rm -rf /var/lib/apt/lists/*

# Copy required keys and Java source files
COPY student_private.pem /app/student_private.pem
COPY student_public.pem /app/student_public.pem
COPY instructor_public.pem /app/instructor_public.pem

COPY TotpUtil.java /app/TotpUtil.java
COPY TotpApiServer.java /app/TotpApiServer.java

COPY entrypoint.sh /app/entrypoint.sh

# Compile Java files
RUN javac TotpUtil.java TotpApiServer.java

# Make entrypoint executable
RUN chmod +x /app/entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
