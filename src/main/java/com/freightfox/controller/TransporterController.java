package com.freightfox.controller;

import com.freightfox.dto.request.AssignmentRequest;
import com.freightfox.dto.request.TransporterInputRequest;
import com.freightfox.dto.response.AssignmentResponse;
import com.freightfox.service.TransporterAssignmentService;
import com.freightfox.service.TransporterInputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for submitting lane and quote data and for computing transporter assignments.
 */
@RestController
@RequestMapping(path = "/api/v1/transporters", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Transporters", description = "Transporter assignment on lanes")
public class TransporterController {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String SAVED_MESSAGE = "Input data saved successfully.";

    private final TransporterInputService inputService;
    private final TransporterAssignmentService assignmentService;

    public TransporterController(TransporterInputService inputService, TransporterAssignmentService assignmentService) {
        this.inputService = inputService;
        this.assignmentService = assignmentService;
    }

    @PostMapping(path = "/input", consumes = JSON)
    @Operation(summary = "Submit lanes and transporter quotes",
            description = "Validates and stores the dataset. Each call replaces the previously stored dataset.")
    @ApiResponse(responseCode = "200", description = "Input saved",
            content = @Content(mediaType = JSON,
                    schema = @Schema(implementation = com.freightfox.dto.response.ApiResponse.class),
                    examples = @ExampleObject(value = """
                            {"status": "success", "message": "Input data saved successfully."}""")))
    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON",
            content = @Content(mediaType = JSON,
                    schema = @Schema(implementation = com.freightfox.dto.response.ApiResponse.class),
                    examples = @ExampleObject(value = """
                            {"status": "error", "message": "Invalid input data",
                             "timestamp": "2026-01-01T10:00:00Z", "errors": ["Lane 3 has no quotes"]}""")))
    public com.freightfox.dto.response.ApiResponse submitInput(@Valid @RequestBody TransporterInputRequest request) {
        inputService.saveInput(request);
        return com.freightfox.dto.response.ApiResponse.success(SAVED_MESSAGE);
    }

    @PostMapping(path = "/assignment", consumes = JSON)
    @Operation(summary = "Compute the optimal assignment",
            description = "Covers every lane at minimum total cost using at most maxTransporters distinct "
                    + "transporters. Among equal-cost options, more transporters are used. A limit above the "
                    + "number of transporters is capped.")
    @ApiResponse(responseCode = "200", description = "Assignment computed",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = AssignmentResponse.class),
                    examples = @ExampleObject(value = """
                            {"status": "success", "totalCost": 60139,
                             "assignments": [{"laneId": 1, "transporterId": 5, "cost": 11601}],
                             "selectedTransporters": [1, 4, 5]}""")))
    @ApiResponse(responseCode = "400", description = "maxTransporters missing or below 1, or malformed JSON",
            content = @Content(mediaType = JSON,
                    schema = @Schema(implementation = com.freightfox.dto.response.ApiResponse.class),
                    examples = @ExampleObject(value = """
                            {"status": "error", "message": "Validation failed", "timestamp": "2026-01-01T10:00:00Z",
                             "errors": ["maxTransporters: maxTransporters must be at least 1"]}""")))
    @ApiResponse(responseCode = "409", description = "No input data submitted yet",
            content = @Content(mediaType = JSON,
                    schema = @Schema(implementation = com.freightfox.dto.response.ApiResponse.class),
                    examples = @ExampleObject(value = """
                            {"status": "error",
                             "message": "No input data submitted. Call /api/v1/transporters/input first.",
                             "timestamp": "2026-01-01T10:00:00Z"}""")))
    @ApiResponse(responseCode = "422", description = "Lanes cannot be covered with that many transporters",
            content = @Content(mediaType = JSON,
                    schema = @Schema(implementation = com.freightfox.dto.response.ApiResponse.class),
                    examples = @ExampleObject(value = """
                            {"status": "error",
                             "message": "Cannot cover all lanes with 2 transporters (minimum required: 3)",
                             "timestamp": "2026-01-01T10:00:00Z"}""")))
    public AssignmentResponse assign(@Valid @RequestBody AssignmentRequest request) {
        return assignmentService.assign(request.maxTransporters());
    }
}
