package com.ruoyi.project.notification.service;

import java.util.ArrayList;
import java.util.Collections;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.notification.domain.*;
import com.ruoyi.project.notification.mapper.NotificationQuestionMapper;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/** 通知问题与答案。父通知锁串行化网页提交及插件完成回执。 */
@Service
public class NotificationQuestionService
{
    private final NotificationQuestionMapper mapper;
    private final NotificationRecordMapper records;
    private final NotificationCredentialService credentials;
    private final NotificationReceiveService receiver;
    private final NotificationDetailService details;
    private final NotificationKeyService keys;
    private final ObjectMapper json;

    public NotificationQuestionService(NotificationQuestionMapper mapper, NotificationRecordMapper records,
            NotificationCredentialService credentials, NotificationReceiveService receiver,
            NotificationDetailService details, NotificationKeyService keys, ObjectMapper json)
    {
        this.mapper = mapper;
        this.records = records;
        this.credentials = credentials;
        this.receiver = receiver;
        this.details = details;
        this.keys = keys;
        this.json = json;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public NotificationAcceptResponse create(String key, NotificationQuestionRequest request)
    {
        NotificationCredential credential = credentials.authenticate(key);
        request.setRequestId(request.getRequestId().trim());
        mapper.lockCredential(credential.getCredentialId());
        if (!"CARD".equals(request.getMessageType())) throw new ServiceException("交互问题必须使用 CARD", 400);
        NotificationQuestionValidator.questions(request.getQuestions());
        ArrayList<String> routes = new ArrayList<>(request.getRouteCodes());
        Collections.sort(routes);
        request.setTitle(StringUtils.trimToEmpty(request.getTitle()));
        request.setSummary(StringUtils.trimToNull(request.getSummary()));
        ObjectNode fingerprint = json.valueToTree(request);
        fingerprint.set("routeCodes", json.valueToTree(routes));
        String hash = keys.sha256Hex(fingerprint.toString());
        NotificationQuestion existing = mapper.selectByRequest(credential.getCredentialId(), request.getRequestId());
        if (existing != null && !hash.equals(existing.getContentHash())) throw new ServiceException("requestId 内容冲突", 409);
        if (existing == null && records.selectRecordByRequestId(credential.getCredentialId(), request.getRequestId()) != null)
            throw new ServiceException("requestId 已用于普通通知", 409);
        NotificationAcceptResponse accepted = receiver.accept(key, request);
        if (existing == null)
        {
            NotificationQuestion question = new NotificationQuestion();
            question.setRecordId(accepted.getRecordId());
            question.setCredentialId(credential.getCredentialId());
            question.setRequestId(request.getRequestId());
            question.setContentHash(hash);
            question.setQuestionsJson(request.getQuestions().toString());
            question.setInstanceId(request.getInstanceId());
            question.setAgentId(request.getAgentId());
            question.setCallId(request.getCallId());
            mapper.insert(question);
        }
        return accepted;
    }

    public ObjectNode poll(String key, String requestId)
    {
        NotificationQuestion question = owned(key, requestId);
        NotificationRecord record = records.selectRecordById(question.getRecordId());
        ObjectNode view = view(question);
        if (!available(record)) view.put("state", "CANCELLED");
        return view;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ObjectNode settle(String key, String requestId, JsonNode body)
    {
        NotificationQuestion owned = owned(key, requestId);
        NotificationRecord record = records.selectRecordForUpdate(owned.getRecordId());
        NotificationQuestion question = mapper.selectByRecord(owned.getRecordId());
        String state = body.path("state").asText();
        if (!"APPLIED".equals(state) && !"CANCELLED".equals(state)) throw new ServiceException("完成状态不正确", 400);
        String source = body.path("source").asText();
        JsonNode answer = null;
        if ("APPLIED".equals(state))
        {
            if (!available(record)) throw new ServiceException("通知已撤销或删除", 409);
            if (!"LOCAL".equals(source) && !"REMOTE".equals(source)) throw new ServiceException("答案来源不正确", 400);
            answer = NotificationQuestionValidator.answer(read(question.getQuestionsJson()), body.get("answer"));
            if ("REMOTE".equals(source) && (!"ANSWERED".equals(question.getState()) && !"APPLIED".equals(question.getState())
                    || !answer.equals(read(question.getAnswerJson())))) throw new ServiceException("远程答案不一致", 409);
        }
        if ("APPLIED".equals(question.getState()) || "CANCELLED".equals(question.getState()))
        {
            if (!state.equals(question.getState()) || (answer != null && !answer.equals(read(question.getAnswerJson())))
                    || (answer != null && !source.equals(question.getSource()))) throw new ServiceException("问题已结束", 409);
            return view(question);
        }
        // 本地 UI 先完成时，以原生调用实际采用的答案为准，覆盖尚未应用的网页提交。
        question.setState(state);
        question.setSource(answer == null ? null : source);
        question.setAnswerJson(answer == null ? null : answer.toString());
        mapper.update(question);
        return view(question);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ObjectNode publicQuestion(String token, NotificationAccessContext context, JsonNode answer)
    {
        if (!keys.isDetailTokenFormat(token)) throw new ServiceException("链接无效", 404);
        NotificationRecord found = records.selectRecordByTokenHash(keys.sha256Hex(token));
        if (found == null) throw new ServiceException("链接无效", 404);
        records.selectRecordForUpdate(found.getRecordId());
        // 锁定父记录后重新授权，每次提交都重新计算一小时窗口与登录权限。
        NotificationDetailResult authorized = details.load(token, context);
        if (!authorized.isSuccess()) throw new ServiceException(authorized.getMessage(), authorized.getCode());
        NotificationQuestion question = mapper.selectByRecord(found.getRecordId());
        if (question == null)
        {
            if (answer != null) throw new ServiceException("该通知没有交互问题", 404);
            return null;
        }
        if (answer != null)
        {
            if (!"WAITING".equals(question.getState())) throw new ServiceException("答案已提交或问题已结束，请刷新查看", 409);
            JsonNode checked = NotificationQuestionValidator.answer(read(question.getQuestionsJson()), answer);
            question.setAnswerJson(checked.toString());
            question.setState("ANSWERED");
            question.setSource("REMOTE");
            mapper.update(question);
        }
        return view(question);
    }

    private NotificationQuestion owned(String key, String requestId)
    {
        Long credentialId = credentials.authenticate(key).getCredentialId();
        NotificationQuestion question = mapper.selectByRequest(credentialId, requestId);
        if (question == null) throw new ServiceException("问题不存在", 404);
        return question;
    }

    private boolean available(NotificationRecord record)
    {
        return record != null && !NotificationAccessPolicy.isDeleted(record) && !NotificationAccessPolicy.isRevoked(record);
    }

    private ObjectNode view(NotificationQuestion question)
    {
        ObjectNode view = json.createObjectNode();
        view.put("state", question.getState());
        view.put("source", question.getSource());
        view.set("questions", read(question.getQuestionsJson()));
        view.set("answer", read(question.getAnswerJson()));
        return view;
    }

    private JsonNode read(String value)
    {
        if (value == null) return json.nullNode();
        try { return json.readTree(value); }
        catch (Exception e) { throw new ServiceException("问题数据无法读取", 500); }
    }
}
