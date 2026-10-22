-- 新增“工具箱”一级目录、5 个分类与 63 个叶子菜单。
-- 只新增 sys_menu / sys_role_menu，不修改或删除任何已有菜单与授权。
-- 基于 parent_id + path 判断，可重复执行；不硬编码 menu_id。
-- 仅给启用且未删除的 admin 角色授予新增菜单，其余角色请后续在角色管理界面授权。
SET NAMES utf8mb4;
START TRANSACTION;

-- 一级目录：与 magicTools 同级
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '工具箱', 0, 6, 'toolbox', NULL, 'Toolbox',
       1, 0, 'M', '0', '0', '', 'tool',
       'admin', SYSDATE(), '工具箱目录'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'toolbox' AND menu_type = 'M'
);

SET @toolbox_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = 0 AND path = 'toolbox' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);

-- 5 个分类目录
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '文件处理', @toolbox_id, 10, 'files', NULL, 'ToolboxFiles',
       1, 0, 'M', '0', '0', 'toolbox:files:view', 'upload',
       'admin', SYSDATE(), '工具箱-文件处理'
FROM DUAL
WHERE @toolbox_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @toolbox_id AND path = 'files' AND menu_type = 'M'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '格式转换', @toolbox_id, 20, 'convert', NULL, 'ToolboxConvert',
       1, 0, 'M', '0', '0', 'toolbox:convert:view', 'switch',
       'admin', SYSDATE(), '工具箱-格式转换'
FROM DUAL
WHERE @toolbox_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @toolbox_id AND path = 'convert' AND menu_type = 'M'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '网页工具', @toolbox_id, 30, 'web', NULL, 'ToolboxWeb',
       1, 0, 'M', '0', '0', 'toolbox:web:view', 'language',
       'admin', SYSDATE(), '工具箱-网页工具'
FROM DUAL
WHERE @toolbox_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @toolbox_id AND path = 'web' AND menu_type = 'M'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '开发辅助', @toolbox_id, 40, 'development', NULL, 'ToolboxDevelopment',
       1, 0, 'M', '0', '0', 'toolbox:development:view', 'tool',
       'admin', SYSDATE(), '工具箱-开发辅助'
FROM DUAL
WHERE @toolbox_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @toolbox_id AND path = 'development' AND menu_type = 'M'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '加密与安全', @toolbox_id, 50, 'security', NULL, 'ToolboxSecurity',
       1, 0, 'M', '0', '0', 'toolbox:security:view', 'lock',
       'admin', SYSDATE(), '工具箱-加密与安全'
FROM DUAL
WHERE @toolbox_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @toolbox_id AND path = 'security' AND menu_type = 'M'
);

SET @files_id = (
    SELECT menu_id FROM sys_menu WHERE parent_id = @toolbox_id AND path = 'files' AND menu_type = 'M' ORDER BY menu_id LIMIT 1
);
SET @convert_id = (
    SELECT menu_id FROM sys_menu WHERE parent_id = @toolbox_id AND path = 'convert' AND menu_type = 'M' ORDER BY menu_id LIMIT 1
);
SET @web_id = (
    SELECT menu_id FROM sys_menu WHERE parent_id = @toolbox_id AND path = 'web' AND menu_type = 'M' ORDER BY menu_id LIMIT 1
);
SET @development_id = (
    SELECT menu_id FROM sys_menu WHERE parent_id = @toolbox_id AND path = 'development' AND menu_type = 'M' ORDER BY menu_id LIMIT 1
);
SET @security_id = (
    SELECT menu_id FROM sys_menu WHERE parent_id = @toolbox_id AND path = 'security' AND menu_type = 'M' ORDER BY menu_id LIMIT 1
);

-- ----------------------------
-- 文件处理（5）
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '图片格式转换', @files_id, 1, 'image-convert', 'toolbox/files/image-convert/index', 'ToolboxImageConvert',
       1, 1, 'C', '0', '0', 'toolbox:imageConvert:view', 'color',
       'admin', SYSDATE(), '图片格式转换'
FROM DUAL
WHERE @files_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @files_id AND path = 'image-convert' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '图片转 PDF', @files_id, 2, 'image-to-pdf', 'toolbox/files/image-to-pdf/index', 'ToolboxImageToPdf',
       1, 1, 'C', '0', '0', 'toolbox:imageToPdf:view', 'pdf',
       'admin', SYSDATE(), '图片转 PDF'
FROM DUAL
WHERE @files_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @files_id AND path = 'image-to-pdf' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'PDF 转图片', @files_id, 3, 'pdf-to-images', 'toolbox/files/pdf-to-images/index', 'ToolboxPdfToImages',
       1, 1, 'C', '0', '0', 'toolbox:pdfToImages:view', 'pdf',
       'admin', SYSDATE(), 'PDF 转图片'
FROM DUAL
WHERE @files_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @files_id AND path = 'pdf-to-images' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'PDF 页面处理', @files_id, 4, 'pdf-pages', 'toolbox/files/pdf-pages/index', 'ToolboxPdfPages',
       1, 1, 'C', '0', '0', 'toolbox:pdfPages:view', 'pdf',
       'admin', SYSDATE(), 'PDF 页面处理'
FROM DUAL
WHERE @files_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @files_id AND path = 'pdf-pages' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '表格文件转换', @files_id, 5, 'spreadsheet-convert', 'toolbox/files/spreadsheet-convert/index', 'ToolboxSpreadsheetConvert',
       1, 1, 'C', '0', '0', 'toolbox:spreadsheetConvert:view', 'excel',
       'admin', SYSDATE(), '表格文件转换'
FROM DUAL
WHERE @files_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @files_id AND path = 'spreadsheet-convert' AND menu_type = 'C'
);

-- ----------------------------
-- 格式转换（20）
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '日期时间转换', @convert_id, 1, 'date-time', 'toolbox/convert/date-time/index', 'ToolboxDateTime',
       1, 1, 'C', '0', '0', 'toolbox:dateTime:view', 'time',
       'admin', SYSDATE(), '日期时间转换'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'date-time' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '整数进制转换', @convert_id, 2, 'base-convert', 'toolbox/convert/base-convert/index', 'ToolboxBaseConvert',
       1, 1, 'C', '0', '0', 'toolbox:baseConvert:view', 'number',
       'admin', SYSDATE(), '整数进制转换'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'base-convert' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '罗马数字转换', @convert_id, 3, 'roman-numeral', 'toolbox/convert/roman-numeral/index', 'ToolboxRomanNumeral',
       1, 1, 'C', '0', '0', 'toolbox:romanNumeral:view', 'number',
       'admin', SYSDATE(), '罗马数字转换'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'roman-numeral' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'Base64 文本编解码', @convert_id, 4, 'base64-text', 'toolbox/convert/base64-text/index', 'ToolboxBase64Text',
       1, 1, 'C', '0', '0', 'toolbox:base64Text:view', 'code',
       'admin', SYSDATE(), 'Base64 文本编解码'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'base64-text' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'Base64 文件转换', @convert_id, 5, 'base64-file', 'toolbox/convert/base64-file/index', 'ToolboxBase64File',
       1, 1, 'C', '0', '0', 'toolbox:base64File:view', 'upload',
       'admin', SYSDATE(), 'Base64 文件转换'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'base64-file' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '颜色格式转换', @convert_id, 6, 'color-convert', 'toolbox/convert/color-convert/index', 'ToolboxColorConvert',
       1, 1, 'C', '0', '0', 'toolbox:colorConvert:view', 'color',
       'admin', SYSDATE(), '颜色格式转换'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'color-convert' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '大小写与命名转换', @convert_id, 7, 'case-convert', 'toolbox/convert/case-convert/index', 'ToolboxCaseConvert',
       1, 1, 'C', '0', '0', 'toolbox:caseConvert:view', 'edit',
       'admin', SYSDATE(), '大小写与命名转换'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'case-convert' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '北约音标字母转换', @convert_id, 8, 'nato-alphabet', 'toolbox/convert/nato-alphabet/index', 'ToolboxNatoAlphabet',
       1, 1, 'C', '0', '0', 'toolbox:natoAlphabet:view', 'language',
       'admin', SYSDATE(), '北约音标字母转换'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'nato-alphabet' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '文本与二进制转换', @convert_id, 9, 'text-binary', 'toolbox/convert/text-binary/index', 'ToolboxTextBinary',
       1, 1, 'C', '0', '0', 'toolbox:textBinary:view', 'code',
       'admin', SYSDATE(), '文本与二进制转换'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'text-binary' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '文本与 Unicode 转换', @convert_id, 10, 'text-unicode', 'toolbox/convert/text-unicode/index', 'ToolboxTextUnicode',
       1, 1, 'C', '0', '0', 'toolbox:textUnicode:view', 'code',
       'admin', SYSDATE(), '文本与 Unicode 转换'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'text-unicode' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'YAML 转 JSON', @convert_id, 11, 'yaml-to-json', 'toolbox/convert/yaml-to-json/index', 'ToolboxYamlToJson',
       1, 1, 'C', '0', '0', 'toolbox:yamlToJson:view', 'code',
       'admin', SYSDATE(), 'YAML 转 JSON'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'yaml-to-json' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'YAML 转 TOML', @convert_id, 12, 'yaml-to-toml', 'toolbox/convert/yaml-to-toml/index', 'ToolboxYamlToToml',
       1, 1, 'C', '0', '0', 'toolbox:yamlToToml:view', 'code',
       'admin', SYSDATE(), 'YAML 转 TOML'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'yaml-to-toml' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'JSON 转 YAML', @convert_id, 13, 'json-to-yaml', 'toolbox/convert/json-to-yaml/index', 'ToolboxJsonToYaml',
       1, 1, 'C', '0', '0', 'toolbox:jsonToYaml:view', 'code',
       'admin', SYSDATE(), 'JSON 转 YAML'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'json-to-yaml' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'JSON 转 TOML', @convert_id, 14, 'json-to-toml', 'toolbox/convert/json-to-toml/index', 'ToolboxJsonToToml',
       1, 1, 'C', '0', '0', 'toolbox:jsonToToml:view', 'code',
       'admin', SYSDATE(), 'JSON 转 TOML'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'json-to-toml' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '列表整理', @convert_id, 15, 'list-tools', 'toolbox/convert/list-tools/index', 'ToolboxListTools',
       1, 1, 'C', '0', '0', 'toolbox:listTools:view', 'list',
       'admin', SYSDATE(), '列表整理'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'list-tools' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'TOML 转 JSON', @convert_id, 16, 'toml-to-json', 'toolbox/convert/toml-to-json/index', 'ToolboxTomlToJson',
       1, 1, 'C', '0', '0', 'toolbox:tomlToJson:view', 'code',
       'admin', SYSDATE(), 'TOML 转 JSON'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'toml-to-json' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'TOML 转 YAML', @convert_id, 17, 'toml-to-yaml', 'toolbox/convert/toml-to-yaml/index', 'ToolboxTomlToYaml',
       1, 1, 'C', '0', '0', 'toolbox:tomlToYaml:view', 'code',
       'admin', SYSDATE(), 'TOML 转 YAML'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'toml-to-yaml' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'XML 转 JSON', @convert_id, 18, 'xml-to-json', 'toolbox/convert/xml-to-json/index', 'ToolboxXmlToJson',
       1, 1, 'C', '0', '0', 'toolbox:xmlToJson:view', 'code',
       'admin', SYSDATE(), 'XML 转 JSON'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'xml-to-json' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'JSON 转 XML', @convert_id, 19, 'json-to-xml', 'toolbox/convert/json-to-xml/index', 'ToolboxJsonToXml',
       1, 1, 'C', '0', '0', 'toolbox:jsonToXml:view', 'code',
       'admin', SYSDATE(), 'JSON 转 XML'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'json-to-xml' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'Markdown 转 HTML', @convert_id, 20, 'markdown-to-html', 'toolbox/convert/markdown-to-html/index', 'ToolboxMarkdownToHtml',
       1, 1, 'C', '0', '0', 'toolbox:markdownToHtml:view', 'edit',
       'admin', SYSDATE(), 'Markdown 转 HTML'
FROM DUAL
WHERE @convert_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @convert_id AND path = 'markdown-to-html' AND menu_type = 'C'
);

-- ----------------------------
-- 网页工具（16）
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'URL 编解码', @web_id, 1, 'url-codec', 'toolbox/web/url-codec/index', 'ToolboxUrlCodec',
       1, 1, 'C', '0', '0', 'toolbox:urlCodec:view', 'link',
       'admin', SYSDATE(), 'URL 编解码'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'url-codec' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'HTML 实体编解码', @web_id, 2, 'html-entities', 'toolbox/web/html-entities/index', 'ToolboxHtmlEntities',
       1, 1, 'C', '0', '0', 'toolbox:htmlEntities:view', 'code',
       'admin', SYSDATE(), 'HTML 实体编解码'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'html-entities' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'URL 解析', @web_id, 3, 'url-parser', 'toolbox/web/url-parser/index', 'ToolboxUrlParser',
       1, 1, 'C', '0', '0', 'toolbox:urlParser:view', 'link',
       'admin', SYSDATE(), 'URL 解析'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'url-parser' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '设备与浏览器信息', @web_id, 4, 'device-info', 'toolbox/web/device-info/index', 'ToolboxDeviceInfo',
       1, 1, 'C', '0', '0', 'toolbox:deviceInfo:view', 'monitor',
       'admin', SYSDATE(), '设备与浏览器信息'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'device-info' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'Basic Auth 生成', @web_id, 5, 'basic-auth', 'toolbox/web/basic-auth/index', 'ToolboxBasicAuth',
       1, 1, 'C', '0', '0', 'toolbox:basicAuth:view', 'password',
       'admin', SYSDATE(), 'Basic Auth 生成'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'basic-auth' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'Meta 标签生成', @web_id, 6, 'meta-tags', 'toolbox/web/meta-tags/index', 'ToolboxMetaTags',
       1, 1, 'C', '0', '0', 'toolbox:metaTags:view', 'code',
       'admin', SYSDATE(), 'Meta 标签生成'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'meta-tags' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'OTP 口令生成与校验', @web_id, 7, 'otp', 'toolbox/web/otp/index', 'ToolboxOtp',
       1, 1, 'C', '0', '0', 'toolbox:otp:view', 'password',
       'admin', SYSDATE(), 'OTP 口令生成与校验'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'otp' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'MIME 类型查询', @web_id, 8, 'mime-types', 'toolbox/web/mime-types/index', 'ToolboxMimeTypes',
       1, 1, 'C', '0', '0', 'toolbox:mimeTypes:view', 'documentation',
       'admin', SYSDATE(), 'MIME 类型查询'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'mime-types' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'JWT 解析', @web_id, 9, 'jwt-parser', 'toolbox/web/jwt-parser/index', 'ToolboxJwtParser',
       1, 1, 'C', '0', '0', 'toolbox:jwtParser:view', 'password',
       'admin', SYSDATE(), 'JWT 解析'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'jwt-parser' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '键盘按键查询', @web_id, 10, 'keyboard-events', 'toolbox/web/keyboard-events/index', 'ToolboxKeyboardEvents',
       1, 1, 'C', '0', '0', 'toolbox:keyboardEvents:view', 'input',
       'admin', SYSDATE(), '键盘按键查询'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'keyboard-events' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'URL Slug 生成', @web_id, 11, 'slug-generator', 'toolbox/web/slug-generator/index', 'ToolboxSlugGenerator',
       1, 1, 'C', '0', '0', 'toolbox:slugGenerator:view', 'link',
       'admin', SYSDATE(), 'URL Slug 生成'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'slug-generator' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'HTML 可视化编辑', @web_id, 12, 'html-editor', 'toolbox/web/html-editor/index', 'ToolboxHtmlEditor',
       1, 1, 'C', '0', '0', 'toolbox:htmlEditor:view', 'edit',
       'admin', SYSDATE(), 'HTML 可视化编辑'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'html-editor' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'User-Agent 解析', @web_id, 13, 'ua-parser', 'toolbox/web/ua-parser/index', 'ToolboxUaParser',
       1, 1, 'C', '0', '0', 'toolbox:uaParser:view', 'monitor',
       'admin', SYSDATE(), 'User-Agent 解析'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'ua-parser' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'HTTP 状态码查询', @web_id, 14, 'http-status', 'toolbox/web/http-status/index', 'ToolboxHttpStatus',
       1, 1, 'C', '0', '0', 'toolbox:httpStatus:view', 'server',
       'admin', SYSDATE(), 'HTTP 状态码查询'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'http-status' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'JSON 差异对比', @web_id, 15, 'json-diff', 'toolbox/web/json-diff/index', 'ToolboxJsonDiff',
       1, 1, 'C', '0', '0', 'toolbox:jsonDiff:view', 'code',
       'admin', SYSDATE(), 'JSON 差异对比'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'json-diff' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'Safe Links 解码', @web_id, 16, 'safe-links', 'toolbox/web/safe-links/index', 'ToolboxSafeLinks',
       1, 1, 'C', '0', '0', 'toolbox:safeLinks:view', 'link',
       'admin', SYSDATE(), 'Safe Links 解码'
FROM DUAL
WHERE @web_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @web_id AND path = 'safe-links' AND menu_type = 'C'
);

-- ----------------------------
-- 开发辅助（14）
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'Git 命令速查', @development_id, 1, 'git-cheatsheet', 'toolbox/development/git-cheatsheet/index', 'ToolboxGitCheatsheet',
       1, 1, 'C', '0', '0', 'toolbox:gitCheatsheet:view', 'github',
       'admin', SYSDATE(), 'Git 命令速查'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'git-cheatsheet' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '随机端口号生成', @development_id, 2, 'random-port', 'toolbox/development/random-port/index', 'ToolboxRandomPort',
       1, 1, 'C', '0', '0', 'toolbox:randomPort:view', 'server',
       'admin', SYSDATE(), '随机端口号生成'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'random-port' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'Crontab 表达式生成', @development_id, 3, 'cron-generator', 'toolbox/development/cron-generator/index', 'ToolboxCronGenerator',
       1, 1, 'C', '0', '0', 'toolbox:cronGenerator:view', 'time',
       'admin', SYSDATE(), 'Crontab 表达式生成'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'cron-generator' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'JSON 查看与格式化', @development_id, 4, 'json-viewer', 'toolbox/development/json-viewer/index', 'ToolboxJsonViewer',
       1, 1, 'C', '0', '0', 'toolbox:jsonViewer:view', 'code',
       'admin', SYSDATE(), 'JSON 查看与格式化'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'json-viewer' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'JSON 压缩', @development_id, 5, 'json-minify', 'toolbox/development/json-minify/index', 'ToolboxJsonMinify',
       1, 1, 'C', '0', '0', 'toolbox:jsonMinify:view', 'code',
       'admin', SYSDATE(), 'JSON 压缩'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'json-minify' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'JSON 转 CSV', @development_id, 6, 'json-to-csv', 'toolbox/development/json-to-csv/index', 'ToolboxJsonToCsv',
       1, 1, 'C', '0', '0', 'toolbox:jsonToCsv:view', 'excel',
       'admin', SYSDATE(), 'JSON 转 CSV'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'json-to-csv' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'SQL 格式化', @development_id, 7, 'sql-formatter', 'toolbox/development/sql-formatter/index', 'ToolboxSqlFormatter',
       1, 1, 'C', '0', '0', 'toolbox:sqlFormatter:view', 'code',
       'admin', SYSDATE(), 'SQL 格式化'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'sql-formatter' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'chmod 权限计算', @development_id, 8, 'chmod-calc', 'toolbox/development/chmod-calc/index', 'ToolboxChmodCalc',
       1, 1, 'C', '0', '0', 'toolbox:chmodCalc:view', 'lock',
       'admin', SYSDATE(), 'chmod 权限计算'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'chmod-calc' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'Docker 命令转 Compose', @development_id, 9, 'docker-compose', 'toolbox/development/docker-compose/index', 'ToolboxDockerCompose',
       1, 1, 'C', '0', '0', 'toolbox:dockerCompose:view', 'component',
       'admin', SYSDATE(), 'Docker 命令转 Compose'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'docker-compose' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'XML 格式化', @development_id, 10, 'xml-formatter', 'toolbox/development/xml-formatter/index', 'ToolboxXmlFormatter',
       1, 1, 'C', '0', '0', 'toolbox:xmlFormatter:view', 'code',
       'admin', SYSDATE(), 'XML 格式化'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'xml-formatter' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'YAML 查看与格式化', @development_id, 11, 'yaml-viewer', 'toolbox/development/yaml-viewer/index', 'ToolboxYamlViewer',
       1, 1, 'C', '0', '0', 'toolbox:yamlViewer:view', 'code',
       'admin', SYSDATE(), 'YAML 查看与格式化'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'yaml-viewer' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '邮箱地址规范化', @development_id, 12, 'email-normalize', 'toolbox/development/email-normalize/index', 'ToolboxEmailNormalize',
       1, 1, 'C', '0', '0', 'toolbox:emailNormalize:view', 'email',
       'admin', SYSDATE(), '邮箱地址规范化'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'email-normalize' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '正则表达式测试', @development_id, 13, 'regex-tester', 'toolbox/development/regex-tester/index', 'ToolboxRegexTester',
       1, 1, 'C', '0', '0', 'toolbox:regexTester:view', 'code',
       'admin', SYSDATE(), '正则表达式测试'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'regex-tester' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '正则语法速查', @development_id, 14, 'regex-cheatsheet', 'toolbox/development/regex-cheatsheet/index', 'ToolboxRegexCheatsheet',
       1, 1, 'C', '0', '0', 'toolbox:regexCheatsheet:view', 'code',
       'admin', SYSDATE(), '正则语法速查'
FROM DUAL
WHERE @development_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @development_id AND path = 'regex-cheatsheet' AND menu_type = 'C'
);

-- ----------------------------
-- 加密与安全（8）
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '随机字符串生成', @security_id, 1, 'random-string', 'toolbox/security/random-string/index', 'ToolboxRandomString',
       1, 1, 'C', '0', '0', 'toolbox:randomString:view', 'password',
       'admin', SYSDATE(), '随机字符串生成'
FROM DUAL
WHERE @security_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @security_id AND path = 'random-string' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '文本哈希计算', @security_id, 2, 'text-hash', 'toolbox/security/text-hash/index', 'ToolboxTextHash',
       1, 1, 'C', '0', '0', 'toolbox:textHash:view', 'lock',
       'admin', SYSDATE(), '文本哈希计算'
FROM DUAL
WHERE @security_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @security_id AND path = 'text-hash' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'BCrypt 生成与校验', @security_id, 3, 'bcrypt', 'toolbox/security/bcrypt/index', 'ToolboxBcrypt',
       1, 1, 'C', '0', '0', 'toolbox:bcrypt:view', 'lock',
       'admin', SYSDATE(), 'BCrypt 生成与校验'
FROM DUAL
WHERE @security_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @security_id AND path = 'bcrypt' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'UUID 生成', @security_id, 4, 'uuid', 'toolbox/security/uuid/index', 'ToolboxUuid',
       1, 1, 'C', '0', '0', 'toolbox:uuid:view', 'number',
       'admin', SYSDATE(), 'UUID 生成'
FROM DUAL
WHERE @security_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @security_id AND path = 'uuid' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'ULID 生成', @security_id, 5, 'ulid', 'toolbox/security/ulid/index', 'ToolboxUlid',
       1, 1, 'C', '0', '0', 'toolbox:ulid:view', 'number',
       'admin', SYSDATE(), 'ULID 生成'
FROM DUAL
WHERE @security_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @security_id AND path = 'ulid' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '文本加密与解密', @security_id, 6, 'text-encrypt', 'toolbox/security/text-encrypt/index', 'ToolboxTextEncrypt',
       1, 1, 'C', '0', '0', 'toolbox:textEncrypt:view', 'lock',
       'admin', SYSDATE(), '文本加密与解密'
FROM DUAL
WHERE @security_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @security_id AND path = 'text-encrypt' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'RSA 密钥对生成', @security_id, 7, 'rsa-keypair', 'toolbox/security/rsa-keypair/index', 'ToolboxRsaKeypair',
       1, 1, 'C', '0', '0', 'toolbox:rsaKeypair:view', 'lock',
       'admin', SYSDATE(), 'RSA 密钥对生成'
FROM DUAL
WHERE @security_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @security_id AND path = 'rsa-keypair' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT 'PDF 签名检查', @security_id, 8, 'pdf-signature', 'toolbox/security/pdf-signature/index', 'ToolboxPdfSignature',
       1, 1, 'C', '0', '0', 'toolbox:pdfSignature:view', 'pdf',
       'admin', SYSDATE(), 'PDF 签名检查'
FROM DUAL
WHERE @security_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @security_id AND path = 'pdf-signature' AND menu_type = 'C'
);

-- 只给 admin 管理员角色授予新增的 69 个菜单；重复执行不会重复授权
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id = @toolbox_id
    OR m.parent_id = @toolbox_id
    OR m.parent_id IN (
        SELECT c.menu_id FROM (
            SELECT menu_id FROM sys_menu WHERE parent_id = @toolbox_id AND menu_type = 'M'
        ) c
    )
WHERE r.role_key = 'admin' AND r.status = '0' AND r.del_flag = '0'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

COMMIT;
