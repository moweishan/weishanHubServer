package com.ruoyi.project.notification.domain;

import javax.validation.constraints.Size;
import javax.validation.constraints.NotBlank;
import com.fasterxml.jackson.databind.JsonNode;

/** 原生问题批次，继承通知内容和路径配置。 */
public class NotificationQuestionRequest extends NotificationAcceptRequest
{
    private JsonNode questions;
    @NotBlank @Size(max = 128)
    private String instanceId;
    @Size(max = 128)
    private String agentId;
    @Size(max = 128)
    private String callId;

    public JsonNode getQuestions() { return questions; }
    public void setQuestions(JsonNode value) { questions = value; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String value) { instanceId = value; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String value) { agentId = value; }
    public String getCallId() { return callId; }
    public void setCallId(String value) { callId = value; }
}
