package us.dot.its.jpo.timvalidator.api.controller;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.pojo.ValidationFieldError;
import us.dot.its.jpo.timvalidator.pojo.ValidationResult;
import us.dot.its.jpo.timvalidator.service.TimValidationService;

@WebMvcTest(TimValidationController.class)
class TimValidationControllerTest {

    private static final String VALID_UPER_HEX = "001F6970138ED764E8ABE0BBA9B4D5240F775D9B0309C269A6E4D166420B77FFF93F51D3C5801EA107F92937E4AD64D6FD38352FB783062C360DE24000000004D34DC9A2CC8416E271180004420C0F23A84179FF2461BE25D59F405F03B8C82F1574AE109002009EEEBB36006001830002848A859B4B280002848AF0E51D2881010100030180C620FB90CAAD3B9C50820826550919D5729A7639692100032A3649C88400A983010180034801010001838182D6DDACDEEEE30D5990CA8E531F4562161223F5418FD9A82BE7219686AA70CD938080BE6942DDAC14F4007CC8F8BD6CAEA835F02C7BBA3354ED2856E5977879ECEF5205A37A1CD9A26E12A6CFF6550202138D3F5CA0D3AE158B18895F0BBF16176971";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TimValidationService timValidationService;

    @Test
    void validateJer_validMessage_returnsValidResponse() throws Exception {
        ValidationResult validationResult = new ValidationResult();
        validationResult.setValid(true);
        validationResult.addValidationCheck("Schema Validation", true, "Message conforms to J2735 schema");

        when(timValidationService.validateTimJer(anyString())).thenReturn(validationResult);

        mockMvc.perform(post("/api/v1/tim/validate/jer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\":31}"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.errors", hasSize(0)))
            .andExpect(jsonPath("$.warnings", hasSize(0)))
            .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
            .andExpect(jsonPath("$.checks[0].name").value("Schema Validation"))
            .andExpect(jsonPath("$.checks[0].passed").value(true));
    }

    @Test
    void validateJer_schemaFailure_returnsOkInvalidResponseWithFieldErrors() throws Exception {
        ValidationResult validationResult = new ValidationResult();
        validationResult.setValid(false);
        validationResult.setErrorMessage("Schema validation failed");
        validationResult.addValidationCheck("Schema Validation", false, "required property 'value' missing");
        validationResult.addFieldErrors(List.of(
            new ValidationFieldError("$.value", "required property 'value' missing")
        ));

        when(timValidationService.validateTimJer(anyString())).thenThrow(
            new ValidationException("TIM validation failed", new ValidationException("Schema validation failed"), validationResult)
        );

        mockMvc.perform(post("/api/v1/tim/validate/jer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\":31}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors[0]", containsString("Schema Validation")))
            .andExpect(jsonPath("$.warnings", hasSize(0)))
            .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
            .andExpect(jsonPath("$.fieldErrors[0].path").value("$.value"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("required property 'value' missing"));
    }

    @Test
    void validateJer_malformedBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/tim/validate/jer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors[0]").value("Request body must be valid application/json JER"));

        verifyNoInteractions(timValidationService);
    }

    @Test
    void validateUper_validHexPayload_returnsValidResponse() throws Exception {
        ValidationResult validationResult = new ValidationResult();
        validationResult.setValid(true);
        validationResult.addValidationCheck("Schema Validation", true, "Message conforms to J2735 schema");

        when(timValidationService.validateTim(VALID_UPER_HEX)).thenReturn(validationResult);

        mockMvc.perform(post("/api/v1/tim/validate/uper")
                .contentType(MediaType.TEXT_PLAIN)
                .content(VALID_UPER_HEX))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.errors", hasSize(0)))
            .andExpect(jsonPath("$.warnings", hasSize(0)))
            .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
            .andExpect(jsonPath("$.checks[0].name").value("Schema Validation"))
            .andExpect(jsonPath("$.checks[0].passed").value(true));

        verify(timValidationService).validateTim(VALID_UPER_HEX);
    }
}
