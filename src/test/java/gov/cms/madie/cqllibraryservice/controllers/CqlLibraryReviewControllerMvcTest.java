package gov.cms.madie.cqllibraryservice.controllers;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.cms.madie.cqllibraryservice.services.CqlLibraryReviewService;
import gov.cms.madie.cqllibraryservice.services.UserServiceClient;
import gov.cms.madie.models.common.Comment;
import gov.cms.madie.models.library.CqlLibraryReview;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(CqlLibraryReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class CqlLibraryReviewControllerMvcTest {
  @Autowired private MockMvc mockMvc;
  @MockitoBean private CqlLibraryReviewService reviewService;
  @MockitoBean private UserServiceClient userServiceClient;

  @Test
  void reviewApiRoundTripsDeletedCommentWithNullContentAndReplies() throws Exception {
    Comment reply = Comment.builder().id("r1").content("reply").build();
    Comment deleted = Comment.builder().id("c1").content(null).replies(List.of(reply)).build();
    CqlLibraryReview review = CqlLibraryReview.builder().comments(List.of(deleted)).build();
    when(reviewService.getReviewByLibraryId("lib-1")).thenReturn(review);
    when(reviewService.updateReview(eq("lib-1"), any(), eq("user")))
        .thenAnswer(invocation -> invocation.getArgument(1));

    mockMvc
        .perform(get("/cql-libraries/lib-1/review"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comments[0].content").hasJsonPath())
        .andExpect(jsonPath("$.comments[0].content").value(nullValue()))
        .andExpect(jsonPath("$.comments[0].replies[0].content").value("reply"));

    mockMvc
        .perform(
            put("/cql-libraries/lib-1/review")
                .principal(() -> "user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"comments\":[{\"id\":\"c1\",\"content\":null,"
                        + "\"replies\":[{\"id\":\"r1\",\"content\":\"reply\"}]}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comments[0].content").hasJsonPath())
        .andExpect(jsonPath("$.comments[0].content").value(nullValue()))
        .andExpect(jsonPath("$.comments[0].replies[0].content").value("reply"));
  }
}
