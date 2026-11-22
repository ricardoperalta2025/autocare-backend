package cl.duoc.autocare.exception;

import org.springframework.http.HttpStatus;

public class CatalogoException extends RuntimeException {

    private final HttpStatus estado;

    public CatalogoException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus estado() {
        return estado;
    }
}
