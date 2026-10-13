package com.freightfox.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Request body of {@code POST /api/v1/transporters/input}: the full set of lanes and transporter quotes.
 *
 * @param lanes        lanes to be covered
 * @param transporters transporters and the lanes they quote
 */
public record TransporterInputRequest(
        @NotEmpty(message = "lanes must not be empty")
        List<@NotNull(message = "lane must not be null") @Valid LaneRequest> lanes,
        @NotEmpty(message = "transporters must not be empty")
        List<@NotNull(message = "transporter must not be null") @Valid TransporterRequest> transporters) {

    /**
     * A lane in the input payload.
     *
     * @param id          lane identifier
     * @param origin      origin location
     * @param destination destination location
     */
    public record LaneRequest(
            @Schema(example = "1") @NotNull(message = "lane id is required") Long id,
            @Schema(example = "Mumbai") @NotBlank(message = "origin must not be blank") String origin,
            @Schema(example = "Delhi") @NotBlank(message = "destination must not be blank") String destination) {
    }

    /**
     * A transporter in the input payload.
     *
     * @param id         transporter identifier
     * @param name       display name
     * @param laneQuotes quotes for the lanes this transporter can serve
     */
    public record TransporterRequest(
            @Schema(example = "1") @NotNull(message = "transporter id is required") Long id,
            @Schema(example = "Transporter T1") @NotBlank(message = "transporter name is required") String name,
            @NotNull(message = "laneQuotes is required")
            List<@NotNull(message = "lane quote must not be null") @Valid LaneQuoteRequest> laneQuotes) {
    }

    /**
     * A single quote of a transporter for a lane.
     *
     * @param laneId quoted lane
     * @param quote  positive price
     */
    public record LaneQuoteRequest(
            @Schema(example = "1") @NotNull(message = "laneId is required") Long laneId,
            @Schema(example = "5000") @NotNull(message = "quote is required")
            @Positive(message = "quote must be positive") Long quote) {
    }
}
