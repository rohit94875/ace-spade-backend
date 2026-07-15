package com.acespade.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import com.acespade.model.enums.DisconnectPolicy;

@Data
public class CreateRoomRequest {
    /** Display nickname shown in the match (2–20 chars). */
    @NotBlank
    @Size(min = 2, max = 20)
    private String username;

    /** When true, BOT Vitality joins the lobby so the host can start solo. */
    private boolean playWithBot = false;

    /** Ranked match — requires login; never combined with bots. */
    private boolean ranked = false;

    /** When true, the room is listed publicly so anyone can browse and join it. */
    private boolean publicRoom = false;

    @NotNull
    private DisconnectPolicy disconnectPolicy = DisconnectPolicy.FORFEIT_WIN;

    /** Ranked only: 8–13 rounds. Casual rooms always use 5 rounds. */
    private int maxRounds = 13;
}
