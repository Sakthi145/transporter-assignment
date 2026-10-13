package com.freightfox;

import com.freightfox.support.TestDatasets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransporterAssignmentEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void nineLaneDatasetWithThreeTransportersCostsOptimum() throws Exception {
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestDatasets.nineLaneJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Input data saved successfully."));

        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxTransporters\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.totalCost").value(134876))
                .andExpect(jsonPath("$.selectedTransporters", contains(1, 4, 6)))
                .andExpect(jsonPath("$.assignments.length()").value(9));
    }

    @Test
    void openApiDocumentIsServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Transporter Assignment on Lanes API"));
    }
}
