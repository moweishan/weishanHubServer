package com.ruoyi.project.notification.service;

import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.exception.ServiceException;

/** 问题 ID 与选项标签按原生协议严格匹配，不生成默认答案。 */
public final class NotificationQuestionValidator
{
    private NotificationQuestionValidator() { }

    public static void questions(JsonNode questions)
    {
        if (questions == null || !questions.isArray() || questions.size() < 1 || questions.size() > 50
                || questions.toString().length() > 100000) fail();
        Set<String> ids = new HashSet<>();
        for (JsonNode question : questions)
        {
            text(question.get("id"), 128, true);
            text(question.get("question"), 10000, true);
            if (!ids.add(question.get("id").asText())) fail();
            if (question.has("header")) text(question.get("header"), 200, false);
            if (question.has("multiSelect") && !question.get("multiSelect").isBoolean()) fail();
            if (question.has("options"))
            {
                JsonNode options = question.get("options");
                if (!options.isArray() || options.size() > 100) fail();
                Set<String> labels = new HashSet<>();
                for (JsonNode option : options)
                {
                    text(option.get("label"), 1000, true);
                    if (!labels.add(option.get("label").asText())) fail();
                    if (option.has("description")) text(option.get("description"), 10000, false);
                }
            }
        }
    }

    public static JsonNode answer(JsonNode questions, JsonNode answer)
    {
        if (answer == null || !answer.isObject() || !answer.path("answers").isArray()
                || answer.path("answers").size() != questions.size() || answer.toString().length() > 100000) fail();
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        ArrayNode rows = normalized.putArray("answers");
        Set<String> answered = new HashSet<>();
        for (JsonNode row : answer.path("answers"))
        {
            text(row.get("id"), 128, true);
            if (!answered.add(row.get("id").asText())) fail();
        }
        for (JsonNode question : questions)
        {
            JsonNode row = null;
            for (JsonNode candidate : answer.path("answers"))
                if (question.get("id").asText().equals(candidate.path("id").asText())) row = candidate;
            if (row == null || !row.path("selected").isArray()) fail();
            Set<String> labels = new HashSet<>();
            for (JsonNode option : question.path("options")) labels.add(option.path("label").asText());
            Set<String> selected = new HashSet<>();
            for (JsonNode value : row.path("selected"))
                if (!value.isTextual() || !labels.contains(value.asText()) || !selected.add(value.asText())) fail();
            String custom = "";
            if (row.has("custom"))
            {
                text(row.get("custom"), 10000, false);
                custom = row.get("custom").asText().trim();
            }
            if (!question.path("multiSelect").asBoolean(false)
                    && (selected.size() > 1 || (!custom.isEmpty() && !selected.isEmpty()))) fail();
            ObjectNode output = rows.addObject();
            output.put("id", question.get("id").asText());
            output.set("selected", row.get("selected").deepCopy());
            if (!custom.isEmpty()) output.put("custom", custom);
        }
        return normalized;
    }

    private static void text(JsonNode node, int max, boolean required)
    {
        if (node == null || !node.isTextual() || node.asText().length() > max
                || (required && node.asText().trim().isEmpty())) fail();
    }

    private static void fail() { throw new ServiceException("问题或答案格式不正确", 400); }
}
