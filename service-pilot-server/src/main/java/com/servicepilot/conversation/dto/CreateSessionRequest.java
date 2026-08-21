package com.servicepilot.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateSessionRequest {

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 100, message = "客户名称不能超过100个字符")
    private String customerName;
}
