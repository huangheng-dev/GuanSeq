CREATE SCHEMA platform;
CREATE SCHEMA identity;
CREATE SCHEMA masterdata;
CREATE SCHEMA sales;

COMMENT ON SCHEMA platform IS '平台级请求、审计与通用治理事实';
COMMENT ON SCHEMA identity IS '用户、组织、工作区与授权事实';
COMMENT ON SCHEMA masterdata IS '客户、物料、BOM 与工艺路线事实';
COMMENT ON SCHEMA sales IS '销售订单、审核、变更与交付承诺事实';
