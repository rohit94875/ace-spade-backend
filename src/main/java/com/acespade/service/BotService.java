package com.acespade.service;

import com.acespade.model.Card;
import com.acespade.model.GameState;
import com.acespade.model.Player;
import com.acespade.model.TrickCard;
import com.acespade.model.enums.Rank;
import com.acespade.model.enums.Suit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BotService {

    public static final String BOT_USERNAME = "BOT Vitality";

    private final TrickResolver trickResolver;

    public Player createBotPlayer() {
        return Player.builder()
                .id(UUID.randomUUID().toString())
                .username(uniqueBotUsername(1))
                .hand(new java.util.ArrayList<>())
                .bot(true)
                .build();
    }

    public String uniqueBotUsername(int existingBotCount) {
        if (existingBotCount <= 0) {
            return BOT_USERNAME;
        }
        return BOT_USERNAME + " #" + (existingBotCount + 1);
    }

    public int countBots(GameState state) {
        return (int) state.getPlayers().stream().filter(Player::isBot).count();
    }

    public void convertToBot(Player player, GameState state) {
        int existing = (int) state.getPlayers().stream()
                .filter(p -> p.isBot() && !p.getId().equals(player.getId()))
                .count();
        player.setBot(true);
        player.setUsername(uniqueBotUsername(existing));
    }

    public int decideBid(GameState state, Player bot) {
        int round = state.getRound();
        int estimate = estimateWinnableTricks(bot.getHand());
        return Math.min(Math.max(0, estimate), round);
    }

    public Card decideCard(GameState state, Player bot) {
        List<Card> valid = bot.getHand().stream()
                .filter(c -> trickResolver.isValidPlay(c, bot.getHand(), state.getCurrentTrick()))
                .collect(Collectors.toList());
        if (valid.isEmpty()) {
            return bot.getHand().get(0);
        }
        if (state.getCurrentTrick().isEmpty()) {
            return lowestCard(valid);
        }
        Card winningPlay = tryWinningCard(valid, state.getCurrentTrick());
        return winningPlay != null ? winningPlay : lowestCard(valid);
    }

    private int estimateWinnableTricks(List<Card> hand) {
        int estimate = 0;
        for (Card card : hand) {
            if (card.getSuit() == Suit.SPADES) {
                estimate += card.getRank().ordinal() >= Rank.TEN.ordinal() ? 1 : 0;
            } else if (card.getRank() == Rank.ACE) {
                estimate += 1;
            } else if (card.getRank().ordinal() >= Rank.KING.ordinal()) {
                estimate += 1;
            }
        }
        return Math.max(0, Math.min(estimate, hand.size()));
    }

    private Card tryWinningCard(List<Card> valid, List<TrickCard> trick) {
        TrickCard currentWinner = trickResolver.resolveWinner(trick);
        Card best = null;
        for (Card candidate : valid) {
            List<TrickCard> simulated = new java.util.ArrayList<>(trick);
            simulated.add(TrickCard.builder()
                    .playerId("sim")
                    .username("sim")
                    .card(candidate)
                    .playOrder(trick.size())
                    .build());
            TrickCard winner = trickResolver.resolveWinner(simulated);
            if (winner.getCard().matches(candidate)) {
                if (best == null || cardStrength(candidate) < cardStrength(best)) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private int cardStrength(Card card) {
        return card.getSuit().ordinal() * 20 + card.getRank().ordinal();
    }

    private Card lowestCard(List<Card> cards) {
        return cards.stream()
                .min(Comparator.comparingInt(this::cardStrength))
                .orElse(cards.get(0));
    }
}
