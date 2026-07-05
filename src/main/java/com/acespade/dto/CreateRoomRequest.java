package com.acespade.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import com.acespade.model.enums.DisconnectPolicy;

@Data
public class CreateRoomRequest {
    @NotBlank
    @Size(min = 1, max = 20)
    private String username;

    /** When true, BOT Vitality joins the lobby so the host can start solo. */
    private boolean playWithBot = false;

    @NotNull
    private DisconnectPolicy disconnectPolicy = DisconnectPolicy.FORFEIT_WIN;
}
