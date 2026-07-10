package us.dot.its.jpo.timvalidator.api.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import us.dot.its.jpo.timvalidator.api.dto.ValidationResponse;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.service.TimValidationService;

@RestController
@RequestMapping("/api/v1/tim")
public class TimValidationController {

    private final TimValidationService timValidationService;

    public TimValidationController(TimValidationService timValidationService) {
        this.timValidationService = timValidationService;
    }

    @PostMapping(
        path = "/validate/jer",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ValidationResponse validateJer(@RequestBody JsonNode jerMessage) throws ValidationException {
        return ValidationResponse.from(timValidationService.validateTimJer(jerMessage.toString()));
    }

    @PostMapping(
        path = "/validate/uper",
        consumes = MediaType.TEXT_PLAIN_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ValidationResponse validateUper(@RequestBody String uperMessage) throws ValidationException {
        return ValidationResponse.from(timValidationService.validateTim(uperMessage));
    }
}
