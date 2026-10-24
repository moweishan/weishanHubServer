package com.ruoyi.project.notification.domain;

/** 问题持久化快照，JSON 只保存原生问题和结构化答案。 */
public class NotificationQuestion
{
    private Long recordId;
    private Long credentialId;
    private String requestId;
    private String contentHash;
    private String questionsJson;
    private String answerJson;
    private String state;
    private String source;
    private String instanceId;
    private String agentId;
    private String callId;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long value) { recordId = value; }
    public Long getCredentialId() { return credentialId; }
    public void setCredentialId(Long value) { credentialId = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String value) { contentHash = value; }
    public String getQuestionsJson() { return questionsJson; }
    public void setQuestionsJson(String value) { questionsJson = value; }
    public String getAnswerJson() { return answerJson; }
    public void setAnswerJson(String value) { answerJson = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getSource() { return source; }
    public void setSource(String value) { source = value; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String value) { instanceId = value; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String value) { agentId = value; }
    public String getCallId() { return callId; }
    public void setCallId(String value) { callId = value; }
}
