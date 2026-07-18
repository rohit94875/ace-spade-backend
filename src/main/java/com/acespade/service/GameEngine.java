package com.acespade.service;

import com.acespade.model.*;
import com.acespade.rating.TierUtil;
import com.acespade.model.enums.GamePhase;
import com.acespade.model.enums.Rank;
import com.acespade.model.enums.Suit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure game logic engine. All methods are stateless — they receive a GameState,
 * compute the next state, and return it. Persistence is handled by callers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameEngine {

    private static final int DEFAULT_MAX_ROUNDS = 13;
    private static final int MAX_PLAYERS = 8;

    private final TrickResolver trickResolver;

    // -------------------------------------------------------------------------
    // Round lifecycle
    // -------------------------------------------------------------------------

    /**
     * Deals cards for the current round and transitions to BIDDING.
     * The round leader index is set to (round - 1) % numPlayers.
     */
    public GameState startRound(GameState state) {
        int round = state.getRound();
        int numPlayers = state.getPlayers().size();

        // Reset per-round state for each player
        state.getPlayers().forEach(Player::resetForRound);
        // Determine the round leader (rotates every round)
        int roundLeaderIdx = (round - 1) % numPlayers;
        // Create and shuffle the deck
        List<Card> deck = createShuffledDoubleDeck();

        // Deal cards

        // Prepare hands
        List<List<Card>> hands = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) {
            hands.add(new ArrayList<>());
        }

        int deckIndex = 0;

        for (int c = 0; c < round; c++) {
            for (int offset = 0; offset < numPlayers; offset++) {
                int playerIndex = (roundLeaderIdx + offset) % numPlayers;
                hands.get(playerIndex).add(deck.get(deckIndex++));
            }
        }

        for (int i = 0; i < numPlayers; i++) {
            state.getPlayers().get(i).setHand(hands.get(i));
        }

        // Bidding starts with the round leader
        state.setRoundLeaderIndex(roundLeaderIdx);
        state.setLeadPlayerIndex(roundLeaderIdx);
        state.setCurrentPlayerIndex(roundLeaderIdx);
        state.setTricksPlayedInRound(0);
        state.setCurrentTrick(new ArrayList<>());
        state.setPhase(GamePhase.BIDDING);

        log.debug("Round {} started. Leader index: {}. Cards dealt: {} per player",
                round, roundLeaderIdx, round);
        return state;
    }

    /**
     * Records a bid for the given player and advances the bidding turn.
     * When all players have bid, transitions to PLAYING.
     *
     * @throws IllegalStateException if it is not this player's turn or bid is invalid
     */
    public GameState placeBid(GameState state, String playerId, int bidAmount) {
        validatePhase(state, GamePhase.BIDDING);
        validateTurn(state, playerId);

        int round = state.getRound();
        if (bidAmount < 0 || bidAmount > round) {
            throw new IllegalArgumentException("Bid must be between 0 and " + round);
        }

        Player player = state.findPlayer(playerId);
        player.setBid(bidAmount);
        log.debug("Player {} bid {} in round {}", player.getUsername(), bidAmount, round);

        // Advance to next player in clockwise order
        int numPlayers = state.getPlayers().size();
        int nextIndex = (state.getCurrentPlayerIndex() + 1) % numPlayers;

        boolean allBid = state.getPlayers().stream().allMatch(p -> p.getBid() != null);
        if (allBid) {
            // All bids placed — transition to playing phase
            state.setPhase(GamePhase.PLAYING);
            state.setCurrentPlayerIndex(state.getRoundLeaderIndex());
            state.setLeadPlayerIndex(state.getRoundLeaderIndex());
            log.debug("All bids placed. Playing starts. Leader: {}", state.getRoundLeaderIndex());
        } else {
            state.setCurrentPlayerIndex(nextIndex);
        }

        return state;
    }

    /**
     * Plays a card for the given player. Adds it to the current trick.
     * If all players have played, resolves the trick.
     *
     * @throws IllegalStateException if the play is invalid
     */
    public GameState playCard(GameState state, String playerId, Suit suit, Rank rank, int deckIndex) {
        validatePhase(state, GamePhase.PLAYING);
        validateTurn(state, playerId);

        Player player = state.findPlayer(playerId);

        // Find the card in hand (match by suit + rank + deckIndex)
        Optional<Card> cardInHand = player.getHand().stream()
                .filter(c -> c.getSuit() == suit && c.getRank() == rank && c.getDeckIndex() == deckIndex)
                .findFirst();

        if (!cardInHand.isPresent()) {
            // Fallback: match by suit+rank only if client omits deckIndex correctly
            cardInHand = player.getHand().stream()
                    .filter(c -> c.getSuit() == suit && c.getRank() == rank)
                    .findFirst();
        }

        if (!cardInHand.isPresent()) {
            throw new IllegalArgumentException("Card not found in player's hand");
        }

        Card card = cardInHand.get();

        if (!trickResolver.isValidPlay(card, player.getHand(), state.getCurrentTrick())) {
            throw new IllegalArgumentException("Invalid play — must follow lead suit");
        }

        // Remove from hand
        player.getHand().remove(card);

        // Build trick entry
        int playOrder = state.getCurrentTrick().size();
        Card playedCard = Card.builder()
                .suit(card.getSuit())
                .rank(card.getRank())
                .deckIndex(card.getDeckIndex())
                .playOrder(playOrder)
                .build();

        TrickCard trickCard = TrickCard.builder()
                .playerId(playerId)
                .username(player.getUsername())
                .card(playedCard)
                .playOrder(playOrder)
                .build();

        state.getCurrentTrick().add(trickCard);
        log.debug("Player {} played {} of {}", player.getUsername(), rank, suit);

        // If all players have played, resolve the trick
        int numPlayers = state.getPlayers().size();
        if (state.getCurrentTrick().size() == numPlayers) {
            state = resolveTrick(state);
        } else {
            // Advance to next player clockwise
            state.setCurrentPlayerIndex((state.getCurrentPlayerIndex() + 1) % numPlayers);
        }

        return state;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private GameState resolveTrick(GameState state) {
        TrickCard winner = trickResolver.resolveWinner(state.getCurrentTrick());
        Player winningPlayer = state.findPlayer(winner.getPlayerId());
        winningPlayer.setTricksWon(winningPlayer.getTricksWon() + 1);

        state.setTricksPlayedInRound(state.getTricksPlayedInRound() + 1);
        log.debug("Trick {} won by {}", state.getTricksPlayedInRound(), winningPlayer.getUsername());

        int winnerIndex = state.findPlayerIndex(winner.getPlayerId());

        // Check if round is over
        if (state.getTricksPlayedInRound() == state.getRound()) {
            state = endRound(state);
        } else {
            // Winner leads the next trick
            state.setLeadPlayerIndex(winnerIndex);
            state.setCurrentPlayerIndex(winnerIndex);
            state.setCurrentTrick(new ArrayList<>());
            state.setPhase(GamePhase.PLAYING);
        }

        return state;
    }

    private GameState endRound(GameState state) {
        // Calculate scores
        for (Player player : state.getPlayers()) {
            int roundScore = (player.getBid() != null && player.getTricksWon() == player.getBid())
                    ? calculateRoundScore(player.getBid())
                    : 0;
            state.getScores().merge(player.getId(), roundScore, Integer::sum);
        }

        log.debug("Round {} ended. Scores: {}", state.getRound(), state.getScores());

        if (state.getRound() == effectiveMaxRounds(state)) {
            state.setPhase(GamePhase.GAME_END);
        } else {
            state.setRound(state.getRound() + 1);
            state.setPhase(GamePhase.ROUND_END);
        }

        return state;
    }

    /**
     * Scoring formula:
     *   bid == 0 → 10 pts
     *   bid N   → 10 + (N * 11) pts
     */
    public int calculateRoundScore(int bid) {
        return bid == 0 ? 10 : 10 + (bid * 11);
    }

    private List<Card> createShuffledDoubleDeck() {
        List<Card> deck = new ArrayList<>(104);
        for (int deckIdx = 0; deckIdx < 2; deckIdx++) {
            for (Suit suit : Suit.values()) {
                for (Rank rank : Rank.values()) {
                    deck.add(Card.builder()
                            .suit(suit)
                            .rank(rank)
                            .deckIndex(deckIdx)
                            .playOrder(0)
                            .build());
                }
            }
        }
        Collections.shuffle(deck);
        return deck;
    }

    private void validatePhase(GameState state, GamePhase expected) {
        if (state.getPhase() != expected) {
            throw new IllegalStateException("Expected phase " + expected + " but was " + state.getPhase());
        }
    }

    private void validateTurn(GameState state, String playerId) {
        Player currentPlayer = state.getPlayers().get(state.getCurrentPlayerIndex());
        if (!currentPlayer.getId().equals(playerId)) {
            throw new IllegalStateException("Not player's turn: " + playerId);
        }
    }

    // -------------------------------------------------------------------------
    // Accessors for computed round summaries
    // -------------------------------------------------------------------------

    public Map<String, Integer> getRoundScores(GameState state) {
        return state.getPlayers().stream().collect(Collectors.toMap(
                Player::getId,
                p -> (p.getBid() != null && p.getTricksWon() == p.getBid().intValue())
                        ? calculateRoundScore(p.getBid())
                        : 0
        ));
    }

    public Map<String, Integer> getBids(GameState state) {
        return state.getPlayers().stream()
                .filter(p -> p.getBid() != null)
                .collect(Collectors.toMap(Player::getId, Player::getBid));
    }

    public Map<String, Integer> getTricksWon(GameState state) {
        return state.getPlayers().stream()
                .collect(Collectors.toMap(Player::getId, Player::getTricksWon));
    }

    public String getWinnerUsername(GameState state) {
        return state.getPlayers().stream()
                .max(Comparator.comparingInt(p -> state.getScores().getOrDefault(p.getId(), 0)))
                .map(Player::getUsername)
                .orElse("Unknown");
    }

    public int getWinnerScore(GameState state) {
        return state.getScores().values().stream()
                .max(Integer::compareTo)
                .orElse(0);
    }

    public int getMaxPlayers() {
        return MAX_PLAYERS;
    }

    private static int effectiveMaxRounds(GameState state) {
        int max = state.getMaxRounds();
        if (max == TierUtil.CASUAL_MAX_ROUNDS) {
            return TierUtil.CASUAL_MAX_ROUNDS;
        }
        if (max >= TierUtil.RANKED_MIN_ROUNDS && max <= TierUtil.RANKED_MAX_ROUNDS) {
            return max;
        }
        return DEFAULT_MAX_ROUNDS;
    }
}
