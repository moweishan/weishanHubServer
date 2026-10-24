package com.ruoyi.project.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.notification.domain.*;
import com.ruoyi.project.notification.mapper.NotificationQuestionMapper;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

class NotificationQuestionServiceTest
{
    private final ObjectMapper json = new ObjectMapper();
    private final NotificationKeyService keys = new NotificationKeyService();
    private NotificationQuestionMapper mapper;
    private NotificationRecordMapper records;
    private NotificationQuestionService service;
    private NotificationQuestion question;
    private NotificationRecord record;
    private String token;
    private final Instant now = Instant.parse("2026-10-24T08:00:00Z");

    @BeforeEach
    void setup() throws Exception
    {
        mapper = mock(NotificationQuestionMapper.class);
        records = mock(NotificationRecordMapper.class);
        NotificationCredentialService credentials = mock(NotificationCredentialService.class);
        NotificationCredential credential = new NotificationCredential();
        credential.setCredentialId(1L);
        when(credentials.authenticate("key")).thenReturn(credential);
        NotificationDetailService detail = new NotificationDetailService(records, keys, Clock.fixed(now, ZoneOffset.UTC));
        service = new NotificationQuestionService(mapper, records, credentials, mock(NotificationReceiveService.class), detail, keys, json);
        record = new NotificationRecord();
        record.setRecordId(2L);
        record.setDeleted("0");
        record.setRevoked("0");
        record.setSentAt(Date.from(now.minusSeconds(60)));
        record.setExpiresAt(Date.from(now.plusSeconds(3540)));
        token = keys.generateDetailToken();
        when(records.selectRecordByTokenHash(keys.sha256Hex(token))).thenReturn(record);
        when(records.selectRecordForUpdate(2L)).thenReturn(record);
        question = new NotificationQuestion();
        question.setRecordId(2L);
        question.setState("WAITING");
        question.setQuestionsJson("[{\"id\":\"q\",\"question\":\"choose\",\"options\":[{\"label\":\"A\"},{\"label\":\"B\"}]}]");
        when(mapper.selectByRecord(2L)).thenReturn(question);
        when(mapper.selectByRequest(1L, "request")).thenReturn(question);
    }

    private JsonNode answer(String label) throws Exception
    {
        return json.readTree("{\"answers\":[{\"id\":\"q\",\"selected\":[\"" + label + "\"]}]}");
    }

    @Test
    void webSubmissionAndPluginApplicationAreSeparateAndDuplicateCannotReplace() throws Exception
    {
        NotificationAccessContext wechat = new NotificationAccessContext(false, false, "MicroMessenger");
        assertEquals("ANSWERED", service.publicQuestion(token, wechat, answer("B")).path("state").asText());
        assertThrows(ServiceException.class, () -> service.publicQuestion(token, wechat, answer("A")));
        JsonNode receipt = json.createObjectNode().put("state", "APPLIED").put("source", "REMOTE").set("answer", answer("B"));
        assertEquals("APPLIED", service.settle("key", "request", receipt).path("state").asText());
        assertEquals("APPLIED", service.settle("key", "request", receipt).path("state").asText());
    }

    @Test
    void localWinnerOverridesRemoteSubmissionButNotAnAppliedAnswer() throws Exception
    {
        question.setState("ANSWERED");
        question.setAnswerJson(answer("B").toString());
        JsonNode local = json.createObjectNode().put("state", "APPLIED").put("source", "LOCAL").set("answer", answer("A"));
        assertEquals("LOCAL", service.settle("key", "request", local).path("source").asText());
        JsonNode lateRemote = json.createObjectNode().put("state", "APPLIED").put("source", "REMOTE").set("answer", answer("B"));
        assertThrows(ServiceException.class, () -> service.settle("key", "request", lateRemote));
    }

    @Test
    void everySubmissionChecksExpiryPermissionAndRevocation() throws Exception
    {
        NotificationAccessContext wechat = new NotificationAccessContext(false, false, "MicroMessenger");
        record.setExpiresAt(Date.from(now));
        ServiceException expired = assertThrows(ServiceException.class, () -> service.publicQuestion(token, wechat, answer("B")));
        assertEquals(401, expired.getCode());
        assertThrows(ServiceException.class, () -> service.publicQuestion(token, new NotificationAccessContext(true, false, "Chrome"), answer("B")));
        assertEquals("WAITING", service.publicQuestion(token, new NotificationAccessContext(true, true, "Chrome"), null).path("state").asText());
        record.setRevoked("1");
        assertThrows(ServiceException.class, () -> service.publicQuestion(token, new NotificationAccessContext(true, true, "Chrome"), answer("B")));
        verify(mapper, never()).update(any());
    }
}
