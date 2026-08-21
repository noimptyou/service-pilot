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
@TableName("customer_session")
public class CustomerSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String customerName;

    private SessionStatus status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
