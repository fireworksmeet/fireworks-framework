# 架构决策记录（Architecture Decision Records）

本目录记录框架中**有意为之的设计决策**。

【强制】改动已有实现前先浏览本目录，确认该设计是否是有意的。若某个"看似不合理"的实现没有对应 ADR，**不要擅自改动，先询问**。

## 为什么需要 ADR

编码代理与后来者常把"有意为之的设计"误判为"可优化的坏代码"（例如：为什么枚举存 `SMALLINT` 而不是可读的字符串、为什么软删除用 `deleted_at` 而不是 `is_deleted`）。ADR 的价值就是回答：**这是有意的，改动前先确认。**

## 模板

文件命名 `NNNN-kebab-english-title.md`：编号 4 位、从 `0001` 连续递增；**文件名英文 kebab、文档内标题中文**。

```markdown
# NNNN. 标题

- **状态**：已接受 / 已否决 / 已废弃 / 被 NNNN 替代
- **日期**：YYYY-MM-DD

## 背景
（遇到的问题是什么？）

## 决策
（决定怎么做？用【强制】【推荐】【禁止】【说明】标注条款强度）

## 理由
（为什么这么做？权衡了哪些方案？）

## 后果
（正面/负面影响？有什么必须注意的？）

## 相关
（关联的 ADR / 规范文档）
```

## 目录

| 编号 | 标题 | 状态 | 日期 |
| --- | --- | --- | --- |
| [0001](0001-enum-stored-as-smallint.md) | 枚举字段存储使用 SMALLINT，状态与布尔字段命名规则 | 已接受 | 2026-08-17 |
| [0002](0002-audit-fields-and-time-type.md) | 审计字段三字段，时间列表达「绝对时刻」 | 已接受 | 2026-08-17 |
| [0003](0003-soft-delete-and-unique-constraint.md) | 逻辑删除用 `deleted_at`，MySQL 以 `deleted_flag` 满足唯一约束 | 已接受 | 2026-08-17 |
| [0004](0004-compensation-idempotency-and-race.md) | 补偿场景的幂等与竞态处理（CAS 取代 `FOR UPDATE`） | 已接受 | 2026-09-17 |
| [0005](0005-api-versioning-strategy.md) | 接口版本演进：原地兼容优先，内部接口协同改造 | 已接受 | 2026-09-20 |
| [0006](0006-token-verification-scope.md) | Token 验签边界：只做自签自验，外部 IdP 由业务接入 | 已接受 | 2026-10-21 |
