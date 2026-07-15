package com.acespade.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateNicknameResponse {
    private String nickname;
}
