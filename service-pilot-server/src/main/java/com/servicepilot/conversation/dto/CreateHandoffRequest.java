package com.servicepilot.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateHandoffRequest {

    @NotBlank(message = "转人工原因不能为空")
    @Size(max = 500, message = "转人工原因不能超过500个字符")
    private String reason;
}
