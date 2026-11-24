package cl.duoc.autocare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autocare.demo")
public record ConfiguracionDemostracion(boolean registro, boolean escrituras) {
}
