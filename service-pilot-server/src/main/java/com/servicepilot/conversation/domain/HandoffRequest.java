package com.servicepilot.conversation.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("handoff_request")
public class HandoffRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private HandoffStatus status;

    private String reason;

    private String assignedAgent;

    private OffsetDateTime createdAt;

    private OffsetDateTime acceptedAt;

    private OffsetDateTime resolvedAt;
}
