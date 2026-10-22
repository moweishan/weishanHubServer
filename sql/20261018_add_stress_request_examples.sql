-- 为压力测试请求配置新增六条示例数据：GET/POST/PUT/DELETE/HEAD/OPTIONS 各一条，全部默认停用（enabled='1'，本项目 0 启用、1 停用）。
-- 在已执行 20261017_add_stress_test.sql 的数据库执行；不修改表结构，可重复执行。
-- 每条均以“示例名称 + http_method”判断是否已存在，已存在则跳过，不覆盖、不更新已有配置。
-- 示例 URL 均为 example.invalid 示意地址，不指向真实第三方接口；启用前请替换为已授权接口并手动启用。
SET NAMES utf8mb4;
START TRANSACTION;

-- ----------------------------
-- 1. GET：演示 URL 与 Query 中的 {{phone}} 替换，不配置请求体
-- ----------------------------
INSERT INTO tool_stress_request_config
    (config_name, request_url, http_method, headers_json, query_params_json,
     body_type, body_content, content_type, timeout_ms, enabled, create_by, create_time, remark)
SELECT '示例 - GET',
       'https://example.invalid/stress/get/{{phone}}',
       'GET',
       '{"Accept":"application/json","X-Example-Client":"weishanHub-stress"}',
       '{"phone":"{{phone}}","scene":"stress-test"}',
       'RAW', NULL, NULL, 10000, '1', 'admin', SYSDATE(),
       '示例配置，默认停用；启用前请先替换为已授权接口并确认 {{phone}} 替换位置，再手动启用。'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM tool_stress_request_config
    WHERE config_name = '示例 - GET' AND http_method = 'GET'
);

-- ----------------------------
-- 2. POST：演示 JSON 请求体，phone 占位符必须位于 JSON 字符串内
-- ----------------------------
INSERT INTO tool_stress_request_config
    (config_name, request_url, http_method, headers_json, query_params_json,
     body_type, body_content, content_type, timeout_ms, enabled, create_by, create_time, remark)
SELECT '示例 - POST',
       'https://example.invalid/stress/post/{{phone}}',
       'POST',
       '{"Accept":"application/json","X-Example-Client":"weishanHub-stress"}',
       '{}',
       'JSON', '{"phone":"{{phone}}","source":"stress-test"}',
       'application/json', 10000, '1', 'admin', SYSDATE(),
       '示例配置，默认停用；启用前请先替换为已授权接口并确认 JSON 中 phone 占位符为字符串，再手动启用。'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM tool_stress_request_config
    WHERE config_name = '示例 - POST' AND http_method = 'POST'
);

-- ----------------------------
-- 3. PUT：演示 FORM 请求体（配置为 JSON 对象，发送时按表单编码）
-- ----------------------------
INSERT INTO tool_stress_request_config
    (config_name, request_url, http_method, headers_json, query_params_json,
     body_type, body_content, content_type, timeout_ms, enabled, create_by, create_time, remark)
SELECT '示例 - PUT',
       'https://example.invalid/stress/put/{{phone}}',
       'PUT',
       '{"Accept":"application/json","X-Example-Client":"weishanHub-stress"}',
       '{}',
       'FORM', '{"phone":"{{phone}}","source":"stress-test"}',
       'application/x-www-form-urlencoded', 10000, '1', 'admin', SYSDATE(),
       '示例配置，默认停用；FORM 请求体使用 JSON 对象配置，启用前请替换为已授权接口，再手动启用。'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM tool_stress_request_config
    WHERE config_name = '示例 - PUT' AND http_method = 'PUT'
);

-- ----------------------------
-- 4. DELETE：演示 RAW 文本请求体
-- ----------------------------
INSERT INTO tool_stress_request_config
    (config_name, request_url, http_method, headers_json, query_params_json,
     body_type, body_content, content_type, timeout_ms, enabled, create_by, create_time, remark)
SELECT '示例 - DELETE',
       'https://example.invalid/stress/delete/{{phone}}',
       'DELETE',
       '{"Accept":"application/json","X-Example-Client":"weishanHub-stress"}',
       '{}',
       'RAW', 'phone={{phone}}&source=stress-test',
       'text/plain', 10000, '1', 'admin', SYSDATE(),
       '示例配置，默认停用；RAW 请求体按原始文本发送，启用前请替换为已授权接口，再手动启用。'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM tool_stress_request_config
    WHERE config_name = '示例 - DELETE' AND http_method = 'DELETE'
);

-- ----------------------------
-- 5. HEAD：演示 URL 中的 {{phone}} 替换，不配置请求体
-- ----------------------------
INSERT INTO tool_stress_request_config
    (config_name, request_url, http_method, headers_json, query_params_json,
     body_type, body_content, content_type, timeout_ms, enabled, create_by, create_time, remark)
SELECT '示例 - HEAD',
       'https://example.invalid/stress/head/{{phone}}',
       'HEAD',
       '{"Accept":"application/json"}',
       '{}',
       'RAW', NULL, NULL, 10000, '1', 'admin', SYSDATE(),
       '示例配置，默认停用；HEAD 不配置请求体，启用前请替换为已授权接口，再手动启用。'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM tool_stress_request_config
    WHERE config_name = '示例 - HEAD' AND http_method = 'HEAD'
);

-- ----------------------------
-- 6. OPTIONS：演示 Query/请求头，不发送请求体
-- ----------------------------
INSERT INTO tool_stress_request_config
    (config_name, request_url, http_method, headers_json, query_params_json,
     body_type, body_content, content_type, timeout_ms, enabled, create_by, create_time, remark)
SELECT '示例 - OPTIONS',
       'https://example.invalid/stress/options/{{phone}}',
       'OPTIONS',
       '{"Accept":"application/json","X-Example-Client":"weishanHub-stress"}',
       '{"phone":"{{phone}}"}',
       'RAW', NULL, NULL, 10000, '1', 'admin', SYSDATE(),
       '示例配置，默认停用；OPTIONS 仅演示 Query 与请求头、不发送请求体，启用前请替换为已授权接口，再手动启用。'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM tool_stress_request_config
    WHERE config_name = '示例 - OPTIONS' AND http_method = 'OPTIONS'
);

COMMIT;
