package ch.gotthard.api;

import ch.gotthard.service.CustomerNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Where a use case's complaint becomes a status code.
 *
 * <p>The services throw about their own subject — no customer answers to that reference, that window
 * runs backwards — and know nothing about HTTP. Mapping those to statuses in one place is what lets
 * them stay that way, and stops each controller growing its own opinion about what a missing row is
 * worth.
 *
 * <p>Nothing catches {@code Exception} here on purpose. An unhandled failure is a 500 and should
 * look like one in the logs; a catch-all would also swallow the exceptions Spring Security throws
 * through the filter chain, turning a 403 into a tidy 400.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiError> onCustomerNotFound(final CustomerNotFoundException notFound) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(notFound.getMessage()));
    }

    /** A window that ends before it starts, or any other argument a use case refused. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> onIllegalArgument(final IllegalArgumentException illegal) {
        return ResponseEntity.badRequest().body(new ApiError(illegal.getMessage()));
    }

    /** A path variable or query parameter that was not the type the route declared. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> onTypeMismatch(final MethodArgumentTypeMismatchException mismatch) {
        return ResponseEntity.badRequest()
                .body(new ApiError("'%s' is not a valid %s".formatted(mismatch.getValue(), mismatch.getName())));
    }
}
