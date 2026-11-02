# Caddy con el módulo de Route 53: saca el certificado de Let's Encrypt validando por DNS (DNS-01), así el security
# group de la EC2 sigue abierto solo a CloudFront. La imagen oficial no trae módulos de DNS.
#
# Se compila en la plataforma de quien construye y se cruza a la de destino (arm64 en la EC2): Go bajo emulación
# (QEMU) se bloquea ("all goroutines are asleep").
FROM --platform=$BUILDPLATFORM caddy:2.11.2-builder AS build
ARG TARGETOS
ARG TARGETARCH
RUN GOOS=$TARGETOS GOARCH=$TARGETARCH xcaddy build --with github.com/caddy-dns/route53@v1.6.2

FROM caddy:2.11.2
COPY --from=build /usr/bin/caddy /usr/bin/caddy
