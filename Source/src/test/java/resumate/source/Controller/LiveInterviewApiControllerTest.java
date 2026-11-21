package resumate.source.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import resumate.source.DTO.InterviewProgress;
import resumate.source.DTO.LiveMessageResponse;
import resumate.source.DTO.LiveTokenResponse;
import resumate.source.Model.InterviewNotFoundException;
import resumate.source.Model.InterviewSession;
import resumate.source.Service.LiveInterviewSessionService;
import resumate.source.Service.LiveInterviewTextService;
import resumate.source.Service.LiveTokenService;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LiveInterviewApiController.class)
class LiveInterviewApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LiveInterviewSessionService sessionService;

    @MockitoBean
    private LiveInterviewTextService textService;

    @MockitoBean
    private LiveTokenService tokenService;

    private final InterviewSession session = InterviewSession.builder().id("abc").build();

    @Test
    void otherHttpSessionGets404Json() throws Exception {
        when(sessionService.getOwned(eq("abc"), anyString()))
                .thenThrow(new InterviewNotFoundException());

        mockMvc.perform(post("/api/interview/live/abc/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"xin chào\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Không tìm thấy buổi phỏng vấn"));

        verify(textService, never()).reply(any(), any());
    }

    @Test
    void messageReturnsReplyAndProgress() throws Exception {
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);
        when(textService.reply(session, "xin chào")).thenReturn(LiveMessageResponse.builder()
                .reply("Chào bạn")
                .progress(new InterviewProgress(1, 5, "q1"))
                .ended(false)
                .remainingSeconds(590)
                .build());

        mockMvc.perform(post("/api/interview/live/abc/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"xin chào\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Chào bạn"))
                .andExpect(jsonPath("$.progress.current").value(1))
                .andExpect(jsonPath("$.progress.total").value(5))
                .andExpect(jsonPath("$.ended").value(false));
    }

    @Test
    void validationErrorBecomes400WithMessage() throws Exception {
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);
        when(textService.reply(eq(session), any()))
                .thenThrow(new IllegalArgumentException("Câu trả lời không được để trống"));

        mockMvc.perform(post("/api/interview/live/abc/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Câu trả lời không được để trống"));
    }

    @Test
    void malformedJsonBecomes400() throws Exception {
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);

        mockMvc.perform(post("/api/interview/live/abc/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Dữ liệu gửi lên không hợp lệ"));
    }

    @Test
    void transcriptForTextModeIsRejected() throws Exception {
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);
        doThrow(new IllegalStateException("Chỉ buổi phỏng vấn bằng giọng nói mới gửi transcript lên máy chủ"))
                .when(sessionService).syncTranscript(eq(session), any());

        mockMvc.perform(post("/api/interview/live/abc/transcript")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"turns\":[{\"role\":\"CANDIDATE\",\"text\":\"hi\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void tokenIsReturnedWithNoStoreHeader() throws Exception {
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);
        when(tokenService.mint(session)).thenReturn(LiveTokenResponse.builder()
                .token("auth_tokens/xyz")
                .model("gemini-3.8-live")
                .apiVersion("v1beta")
                .expiresAt(Instant.parse("2026-11-21T15:00:00Z"))
                .remainingMints(4)
                .build());

        mockMvc.perform(post("/api/interview/live/abc/token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").value("auth_tokens/xyz"))
                .andExpect(jsonPath("$.model").value("gemini-3.8-live"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-21T15:00:00Z"));
    }

    @Test
    void tokenForAnotherHttpSessionIs404() throws Exception {
        when(sessionService.getOwned(eq("abc"), anyString())).thenThrow(new InterviewNotFoundException());

        mockMvc.perform(post("/api/interview/live/abc/token"))
                .andExpect(status().isNotFound());

        verify(tokenService, never()).mint(any());
    }

    @Test
    void tokenLimitBecomes409() throws Exception {
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);
        when(tokenService.mint(session))
                .thenThrow(new IllegalStateException("Đã vượt quá số lần kết nối cho phép."));

        mockMvc.perform(post("/api/interview/live/abc/token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Đã vượt quá số lần kết nối cho phép."));
    }

    @Test
    void finishReturns204() throws Exception {
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);

        mockMvc.perform(post("/api/interview/live/abc/finish"))
                .andExpect(status().isNoContent());

        verify(sessionService).finish(session);
    }
}
