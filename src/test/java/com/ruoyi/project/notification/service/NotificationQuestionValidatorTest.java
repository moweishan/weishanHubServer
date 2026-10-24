package com.ruoyi.project.notification.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;

class NotificationQuestionValidatorTest
{
    private final ObjectMapper json = new ObjectMapper();
    private JsonNode parse(String text) throws Exception { return json.readTree(text); }
    private JsonNode questions() throws Exception
    {
        return parse("[{\"id\":\"q\",\"question\":\"choose\",\"options\":[{\"label\":\"A\"},{\"label\":\"B\"}]}]");
    }

    @Test
    void validatesBatchAndAllowsExplicitSkipOrCustom() throws Exception
    {
        NotificationQuestionValidator.questions(questions());
        JsonNode skip = parse("{\"answers\":[{\"id\":\"q\",\"selected\":[]}]}");
        assertEquals(skip, NotificationQuestionValidator.answer(questions(), skip));
        JsonNode custom = parse("{\"answers\":[{\"id\":\"q\",\"selected\":[],\"custom\":\"  answer  \"}]}");
        assertEquals("answer", NotificationQuestionValidator.answer(questions(), custom).path("answers").get(0).path("custom").asText());
    }

    @Test
    void rejectsUnknownLabelsIdsAndInvalidSingleSelection() throws Exception
    {
        for (String row : new String[] {"{\"id\":\"q\",\"selected\":[\"unknown\"]}",
                "{\"id\":\"wrong\",\"selected\":[\"A\"]}", "{\"id\":\"q\",\"selected\":[\"A\",\"B\"]}",
                "{\"id\":\"q\",\"selected\":[\"A\"],\"custom\":\"other\"}", "{\"id\":\"q\",\"selected\":[\"A\",\"A\"]}"})
        {
            JsonNode answer = parse("{\"answers\":[" + row + "]}");
            assertThrows(ServiceException.class, () -> NotificationQuestionValidator.answer(questions(), answer));
        }
    }

    @Test
    void rejectsDuplicateIdsAndOptionLabels() throws Exception
    {
        JsonNode duplicate = parse("[{\"id\":\"q\",\"question\":\"q\",\"options\":[{\"label\":\"A\"},{\"label\":\"A\"}]}]");
        assertThrows(ServiceException.class, () -> NotificationQuestionValidator.questions(duplicate));
    }
}
