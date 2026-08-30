package com.servicepilot.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateKnowledgeRequest {

    @NotBlank(message = "知识标题不能为空")
    @Size(max = 200, message = "知识标题不能超过200个字符")
    private String title;

    @NotBlank(message = "知识内容不能为空")
    @Size(max = 100_000, message = "知识内容不能超过100000个字符")
    private String content;
}
