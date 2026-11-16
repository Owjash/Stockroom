package com.owjash.assistant;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    ResponseEntity<?> conflict(Exception e) {
        return ResponseEntity.status(409).body(Map.of("message","This change conflicts with an existing record or a concurrent update. Refresh and retry."));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> response(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Request failed.":e.getReason()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        var errors=e.getBindingResult().getFieldErrors();
        String message=errors.isEmpty()?"Check your input.":errors.getFirst().getField()+": "+errors.getFirst().getDefaultMessage();
        return ResponseEntity.badRequest().body(Map.of("message",message));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> size(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(Map.of("message","The PDF exceeds the 10 MB upload limit."));
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<?> json(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message","The request body is not valid JSON for this operation."));
    }
}
