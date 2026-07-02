package com.shllabs.recommender.controller;

import com.shllabs.recommender.model.ChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ChatResponse> handleValidation(MethodArgumentNotValidException ex) {
        ChatResponse body = new ChatResponse(
                "I didn't receive a valid conversation history — please include at least one message.",
                List.of(), false);
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ChatResponse> handleGeneric(Exception ex) {
        ChatResponse body = new ChatResponse(
                "Sorry, something went wrong on my end. Could you try again?",
                List.of(), false);
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }
}


