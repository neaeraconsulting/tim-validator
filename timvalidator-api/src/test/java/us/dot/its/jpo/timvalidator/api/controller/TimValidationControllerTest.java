package us.dot.its.jpo.timvalidator.api.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
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
        validationResult.addValidationCheck("J2735 Schema Validation", true, "Message conforms to generated J2735 schema");
        validationResult.addValidationCheck("ITWG Schema Validation", true, "Message conforms to ITWG TIM profile schema");

        when(timValidationService.validateTimJer(anyString())).thenReturn(validationResult);

        mockMvc.perform(post("/api/v1/tim/validate/jer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\":31}"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.issues", hasSize(0)))
            .andExpect(jsonPath("$.checks[?(@.name == 'J2735 Schema Validation')].passed").value(hasItem(true)))
            .andExpect(jsonPath("$.checks[?(@.name == 'ITWG Schema Validation')].passed").value(hasItem(true)));
    }

    @Test
    void validateJer_schemaFailure_returnsOkInvalidResponseWithIssues() throws Exception {
        ValidationResult validationResult = new ValidationResult();
        validationResult.setErrorMessage("J2735 schema validation failed");
        validationResult.addValidationCheck("J2735 Schema Validation", false, "required property 'value' missing");
        validationResult.addError("J2735 Schema Validation", "required property 'value' missing", "/value");

        when(timValidationService.validateTimJer(anyString())).thenReturn(validationResult);

        mockMvc.perform(post("/api/v1/tim/validate/jer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\":31}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.issues", hasSize(1)))
            .andExpect(jsonPath("$.issues[0].severity").value("ERROR"))
            .andExpect(jsonPath("$.issues[0].checkName").value("J2735 Schema Validation"))
            .andExpect(jsonPath("$.issues[0].path").value("/value"))
            .andExpect(jsonPath("$.issues[0].message").value("required property 'value' missing"));
    }

    @Test
    void validateJer_malformedBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/tim/validate/jer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.issues[0].severity").value("ERROR"))
            .andExpect(jsonPath("$.issues[0].checkName").value("Request"))
            .andExpect(jsonPath("$.issues[0].message").value("Request body must be valid application/json JER"));

        verifyNoInteractions(timValidationService);
    }

    @Test
    void validateUper_validHexPayload_returnsValidResponse() throws Exception {
        ValidationResult validationResult = new ValidationResult();
        validationResult.addValidationCheck("J2735 Schema Validation", true, "Message conforms to generated J2735 schema");
        validationResult.addValidationCheck("ITWG Schema Validation", true, "Message conforms to ITWG TIM profile schema");

        when(timValidationService.validateTim(VALID_UPER_HEX)).thenReturn(validationResult);

        mockMvc.perform(post("/api/v1/tim/validate/uper")
                .contentType(MediaType.TEXT_PLAIN)
                .content(VALID_UPER_HEX))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.issues", hasSize(0)))
            .andExpect(jsonPath("$.checks[?(@.name == 'J2735 Schema Validation')].passed").value(hasItem(true)))
            .andExpect(jsonPath("$.checks[?(@.name == 'ITWG Schema Validation')].passed").value(hasItem(true)));

        verify(timValidationService).validateTim(VALID_UPER_HEX);
    }
}
