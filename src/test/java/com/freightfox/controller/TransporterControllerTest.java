package com.freightfox.controller;

import com.freightfox.dto.request.TransporterInputRequest;
import com.freightfox.dto.response.AssignmentResponse;
import com.freightfox.dto.response.LaneAssignmentResponse;
import com.freightfox.exception.InfeasibleAssignmentException;
import com.freightfox.exception.InvalidInputException;
import com.freightfox.exception.NoInputDataException;
import com.freightfox.service.TransporterAssignmentService;
import com.freightfox.service.TransporterInputService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransporterController.class)
class TransporterControllerTest {

    private static final String INPUT_URL = "/api/v1/transporters/input";
    private static final String ASSIGNMENT_URL = "/api/v1/transporters/assignment";

    private static final String VALID_INPUT = """
            {
              "lanes": [{"id": 1, "origin": "Mumbai", "destination": "Delhi"}],
              "transporters": [{"id": 1, "name": "Transporter T1", "laneQuotes": [{"laneId": 1, "quote": 5000}]}]
            }""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransporterInputService inputService;

    @MockitoBean
    private TransporterAssignmentService assignmentService;

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static void assertErrorShape(ResultActions result, String message) throws Exception {
        result.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Nested
    class InputEndpoint {

        @Test
        void validInputReturnsSuccessMessage() throws Exception {
            postJson(INPUT_URL, VALID_INPUT)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("Input data saved successfully."))
                    .andExpect(jsonPath("$.timestamp").doesNotExist())
                    .andExpect(jsonPath("$.errors").doesNotExist());
            verify(inputService).saveInput(any(TransporterInputRequest.class));
        }

        @Test
        void beanValidationFailuresReturn400WithFieldErrors() throws Exception {
            String body = """
                    {
                      "lanes": [{"id": null, "origin": " ", "destination": "Delhi"}],
                      "transporters": [{"id": 1, "name": "", "laneQuotes": [{"laneId": 1, "quote": -5}]}]
                    }""";

            ResultActions result = postJson(INPUT_URL, body).andExpect(status().isBadRequest());

            assertErrorShape(result, "Validation failed");
            result.andExpect(jsonPath("$.errors", containsInAnyOrder(
                    "lanes[0].id: lane id is required",
                    "lanes[0].origin: origin must not be blank",
                    "transporters[0].name: transporter name is required",
                    "transporters[0].laneQuotes[0].quote: quote must be positive")));
            verifyNoInteractions(inputService);
        }

        @Test
        void emptyCollectionsReturn400() throws Exception {
            ResultActions result = postJson(INPUT_URL, "{\"lanes\": [], \"transporters\": []}")
                    .andExpect(status().isBadRequest());

            assertErrorShape(result, "Validation failed");
            result.andExpect(jsonPath("$.errors", containsInAnyOrder(
                    "lanes: lanes must not be empty",
                    "transporters: transporters must not be empty")));
        }

        @Test
        void crossFieldViolationsFromServiceReturn400() throws Exception {
            doThrow(new InvalidInputException("Invalid input data", List.of("Lane 2 has no quotes")))
                    .when(inputService).saveInput(any());

            ResultActions result = postJson(INPUT_URL, VALID_INPUT).andExpect(status().isBadRequest());

            assertErrorShape(result, "Invalid input data");
            result.andExpect(jsonPath("$.errors[0]").value("Lane 2 has no quotes"));
        }

        @Test
        void malformedJsonReturns400() throws Exception {
            ResultActions result = postJson(INPUT_URL, "{\"lanes\": [").andExpect(status().isBadRequest());

            assertErrorShape(result, "Malformed JSON request body");
            verifyNoInteractions(inputService);
        }

        @Test
        void fractionalQuoteIsRejectedAsMalformed() throws Exception {
            String body = VALID_INPUT.replace("5000", "5000.5");

            postJson(INPUT_URL, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
        }

        @Test
        void nonJsonContentTypeReturns415() throws Exception {
            ResultActions result = mockMvc.perform(post(INPUT_URL).contentType(MediaType.TEXT_PLAIN).content("x"))
                    .andExpect(status().isUnsupportedMediaType());

            assertErrorShape(result, "Content type must be application/json");
        }

        @Test
        void unsupportedMethodReturns405() throws Exception {
            mockMvc.perform(get(INPUT_URL))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.status").value("error"));
        }
    }

    @Nested
    class AssignmentEndpoint {

        @Test
        void returnsAssignmentResponse() throws Exception {
            when(assignmentService.assign(3)).thenReturn(new AssignmentResponse("success", 60139,
                    List.of(new LaneAssignmentResponse(1, 5, 11601), new LaneAssignmentResponse(2, 1, 10512)),
                    List.of(1L, 4L, 5L)));

            postJson(ASSIGNMENT_URL, "{\"maxTransporters\": 3}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.totalCost").value(60139))
                    .andExpect(jsonPath("$.assignments[0].laneId").value(1))
                    .andExpect(jsonPath("$.assignments[0].transporterId").value(5))
                    .andExpect(jsonPath("$.assignments[0].cost").value(11601))
                    .andExpect(jsonPath("$.assignments.length()").value(2))
                    .andExpect(jsonPath("$.selectedTransporters", containsInAnyOrder(1, 4, 5)));
        }

        @Test
        void limitBelowOneReturns400() throws Exception {
            ResultActions result = postJson(ASSIGNMENT_URL, "{\"maxTransporters\": 0}")
                    .andExpect(status().isBadRequest());

            assertErrorShape(result, "Validation failed");
            result.andExpect(jsonPath("$.errors[0]").value("maxTransporters: maxTransporters must be at least 1"));
            verifyNoInteractions(assignmentService);
        }

        @Test
        void missingLimitReturns400() throws Exception {
            postJson(ASSIGNMENT_URL, "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", hasItem("maxTransporters: maxTransporters is required")));
        }

        @Test
        void malformedJsonReturns400() throws Exception {
            ResultActions result = postJson(ASSIGNMENT_URL, "{\"maxTransporters\": \"three\"}")
                    .andExpect(status().isBadRequest());

            assertErrorShape(result, "Malformed JSON request body");
        }

        @Test
        void noInputDataReturns409() throws Exception {
            when(assignmentService.assign(3)).thenThrow(new NoInputDataException());

            ResultActions result = postJson(ASSIGNMENT_URL, "{\"maxTransporters\": 3}")
                    .andExpect(status().isConflict());

            assertErrorShape(result, "No input data submitted. Call /api/v1/transporters/input first.");
            result.andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        void infeasibleLimitReturns422() throws Exception {
            when(assignmentService.assign(2)).thenThrow(InfeasibleAssignmentException.belowMinimum(2, 3));

            ResultActions result = postJson(ASSIGNMENT_URL, "{\"maxTransporters\": 2}")
                    .andExpect(status().isUnprocessableEntity());

            assertErrorShape(result, "Cannot cover all lanes with 2 transporters (minimum required: 3)");
        }

        @Test
        void unexpectedErrorReturns500WithoutInternals() throws Exception {
            when(assignmentService.assign(3)).thenThrow(new IllegalStateException("secret internal detail"));

            ResultActions result = postJson(ASSIGNMENT_URL, "{\"maxTransporters\": 3}")
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(not(containsString("secret internal detail"))));

            assertErrorShape(result, "An unexpected error occurred");
        }
    }
}
