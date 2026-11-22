package cl.duoc.autocare.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import cl.duoc.autocare.controller.CategoriaController;
import cl.duoc.autocare.controller.ProductoController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {ProductoController.class, CategoriaController.class})
public class CatalogoExceptionHandler {

    @ExceptionHandler(CatalogoException.class)
    public ProblemDetail manejarReglaDeCatalogo(CatalogoException excepcion) {
        return ProblemDetail.forStatusAndDetail(excepcion.estado(), excepcion.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacion(MethodArgumentNotValidException excepcion) {
        Map<String, String> campos = new LinkedHashMap<>();
        excepcion.getBindingResult().getFieldErrors()
                .forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revisa los campos indicados.");
        problema.setProperty("campos", campos);
        return problema;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail manejarIdentificadorInvalido() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El identificador no tiene un formato válido.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail manejarJsonInvalido() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El JSON es inválido o contiene campos de tipo incorrecto.");
    }
}
