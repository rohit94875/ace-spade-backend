package com.acespade.service;

import com.acespade.model.Card;
import com.acespade.model.GameState;
import com.acespade.model.Player;
import com.acespade.model.enums.GamePhase;
import com.acespade.model.enums.PokerActionType;
import com.acespade.model.enums.PokerStreet;
import com.acespade.model.enums.Rank;
import com.acespade.model.enums.Suit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fixed-limit Texas Hold'em lite: SB 25 / BB 50 / unit 50, no side pots, no bots.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PokerEngine {

    public static final int STARTING_CHIPS = 1000;
    public static final int MAX_PLAYERS = 6;
    public static final int MIN_PLAYERS = 2;

    private final PokerHandEvaluator handEvaluator;

    public void initMatch(GameState state) {
        state.setSmallBlind(25);
        state.setBigBlind(50);
        state.setBetUnit(50);
        state.setMaxRaisesPerStreet(3);
        state.setDealerIndex(0);
        state.setRound(0);
        state.setMaxRounds(999);
        for (Player p : state.getPlayers()) {
            p.setChips(STARTING_CHIPS);
            p.resetForPokerHand();
            state.getScores().put(p.getId(), STARTING_CHIPS);
        }
    }

    /**
     * Starts a new hand. Returns false if the match is over (≤1 player with chips).
     */
    public boolean startHand(GameState state) {
        List<Player> seated = playersWithChips(state);
        if (seated.size() < 2) {
            state.setPhase(GamePhase.GAME_END);
            syncScores(state);
            return false;
        }

        state.setRound(state.getRound() + 1);
        state.setPhase(GamePhase.POKER_HAND);
        state.setPokerStreet(PokerStreet.PREFLOP);
        state.setCommunityCards(new ArrayList<>());
        state.setPot(0);
        state.setCurrentBet(0);
        state.setRaisesThisStreet(0);
        state.setLastPokerAction(null);
        state.setSmallBlindPlayerId(null);
        state.setBigBlindPlayerId(null);

        for (Player p : state.getPlayers()) {
            p.resetForPokerHand();
        }

        int n = state.getPlayers().size();
        int dealer;
        if (state.getRound() == 1) {
            dealer = firstWithChips(state);
        } else {
            dealer = state.getDealerIndex();
            for (int i = 0; i < n; i++) {
                dealer = (dealer + 1) % n;
                if (state.getPlayers().get(dealer).getChips() > 0) {
                    break;
                }
            }
        }
        state.setDealerIndex(dealer);

        List<Card> deck = createShuffledSingleDeck();
        state.setPokerDeck(deck);

        // Deal 2 hole cards to each player with chips
        for (int c = 0; c < 2; c++) {
            for (int offset = 1; offset <= n; offset++) {
                int idx = (dealer + offset) % n;
                Player p = state.getPlayers().get(idx);
                if (p.getChips() <= 0) continue;
                p.getHand().add(draw(state));
            }
        }

        postBlinds(state);
        // First to act preflop: left of BB (UTG). Heads-up: dealer/SB acts first.
        int sbIdx = nextWithChips(state, dealer);
        int bbIdx = nextWithChips(state, sbIdx);
        if (seated.size() == 2) {
            state.setCurrentPlayerIndex(sbIdx);
        } else {
            state.setCurrentPlayerIndex(nextWithChips(state, bbIdx));
        }

        syncScores(state);
        log.debug("Poker hand {} started room={} dealer={} pot={}",
                state.getRound(), state.getRoomCode(), dealer, state.getPot());
        return true;
    }

    public void applyAction(GameState state, String playerId, PokerActionType action) {
        if (state.getPhase() != GamePhase.POKER_HAND) {
            throw new IllegalStateException("Not in a poker hand");
        }
        if (state.getPokerStreet() == PokerStreet.SHOWDOWN) {
            throw new IllegalStateException("Hand is at showdown");
        }

        Player player = state.findPlayer(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Player not found");
        }
        int idx = state.findPlayerIndex(playerId);
        if (idx != state.getCurrentPlayerIndex()) {
            throw new IllegalStateException("Not your turn");
        }
        if (player.isFolded() || player.getChips() <= 0 && player.isAllIn()) {
            throw new IllegalStateException("You cannot act");
        }
        if (player.getChips() <= 0 && !player.isAllIn()) {
            throw new IllegalStateException("You are out of chips");
        }

        int toCall = state.getCurrentBet() - player.getBetThisStreet();
        List<PokerActionType> legal = legalActions(state, player);
        if (!legal.contains(action)) {
            throw new IllegalArgumentException("Illegal action: " + action + " (allowed: " + legal + ")");
        }

        switch (action) {
            case FOLD:
                player.setFolded(true);
                player.setActedThisStreet(true);
                state.setLastPokerAction(player.getUsername() + " folds");
                break;
            case CHECK:
                player.setActedThisStreet(true);
                state.setLastPokerAction(player.getUsername() + " checks");
                break;
            case CALL:
                putChips(state, player, toCall);
                player.setActedThisStreet(true);
                state.setLastPokerAction(player.getUsername() + " calls " + toCall);
                break;
            case BET: {
                int amount = Math.min(state.getBetUnit(), player.getChips());
                putChips(state, player, amount);
                state.setCurrentBet(player.getBetThisStreet());
                state.setRaisesThisStreet(0);
                resetActedExcept(state, playerId);
                player.setActedThisStreet(true);
                state.setLastPokerAction(player.getUsername() + " bets " + amount);
                break;
            }
            case RAISE: {
                int raiseBy = Math.min(state.getBetUnit(), player.getChips() - toCall);
                if (raiseBy < 0) raiseBy = 0;
                int pay = toCall + raiseBy;
                // If can't cover full raise, treat as all-in call/raise with remaining
                pay = Math.min(pay, player.getChips());
                putChips(state, player, pay);
                state.setCurrentBet(Math.max(state.getCurrentBet(), player.getBetThisStreet()));
                state.setRaisesThisStreet(state.getRaisesThisStreet() + 1);
                resetActedExcept(state, playerId);
                player.setActedThisStreet(true);
                state.setLastPokerAction(player.getUsername() + " raises to " + state.getCurrentBet());
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown action");
        }

        if (countNotFolded(state) <= 1) {
            awardToLastStanding(state);
            return;
        }

        if (isStreetComplete(state)) {
            advanceStreet(state);
        } else {
            state.setCurrentPlayerIndex(nextToAct(state, idx));
        }
        syncScores(state);
    }

    public List<PokerActionType> legalActions(GameState state, Player player) {
        List<PokerActionType> out = new ArrayList<>();
        if (player.isFolded() || player.isAllIn() || player.getChips() <= 0) {
            return out;
        }
        int toCall = state.getCurrentBet() - player.getBetThisStreet();
        out.add(PokerActionType.FOLD);
        if (toCall <= 0) {
            out.add(PokerActionType.CHECK);
            if (state.getCurrentBet() == 0) {
                if (player.getChips() > 0) {
                    out.add(PokerActionType.BET);
                }
            } else if (state.getRaisesThisStreet() < state.getMaxRaisesPerStreet()
                    && player.getChips() >= state.getBetUnit()) {
                out.add(PokerActionType.RAISE);
            }
        } else {
            if (player.getChips() > 0) {
                out.add(PokerActionType.CALL);
            }
            if (state.getRaisesThisStreet() < state.getMaxRaisesPerStreet()
                    && player.getChips() > toCall) {
                out.add(PokerActionType.RAISE);
            }
        }
        return out;
    }

    public Map<String, Object> publicTable(GameState state, String viewerId, boolean revealHands) {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("street", state.getPokerStreet() != null ? state.getPokerStreet().name() : null);
        table.put("communityCards", state.getCommunityCards());
        table.put("pot", state.getPot());
        table.put("currentBet", state.getCurrentBet());
        table.put("betUnit", state.getBetUnit());
        table.put("smallBlind", state.getSmallBlind());
        table.put("bigBlind", state.getBigBlind());
        table.put("dealerPlayerId", state.getPlayers().get(state.getDealerIndex()).getId());
        table.put("smallBlindPlayerId", state.getSmallBlindPlayerId());
        table.put("bigBlindPlayerId", state.getBigBlindPlayerId());
        table.put("handNumber", state.getRound());
        table.put("lastAction", state.getLastPokerAction());
        table.put("raisesThisStreet", state.getRaisesThisStreet());

        String sbName = null;
        String bbName = null;
        if (state.getSmallBlindPlayerId() != null) {
            Player sbp = state.findPlayer(state.getSmallBlindPlayerId());
            if (sbp != null) sbName = sbp.getUsername();
        }
        if (state.getBigBlindPlayerId() != null) {
            Player bbp = state.findPlayer(state.getBigBlindPlayerId());
            if (bbp != null) bbName = bbp.getUsername();
        }
        table.put("smallBlindUsername", sbName);
        table.put("bigBlindUsername", bbName);

        String turnId = null;
        if (state.getPhase() == GamePhase.POKER_HAND
                && state.getPokerStreet() != PokerStreet.SHOWDOWN
                && !state.getPlayers().isEmpty()) {
            turnId = state.getPlayers().get(state.getCurrentPlayerIndex()).getId();
        }
        table.put("currentTurnPlayerId", turnId);

        List<Map<String, Object>> seats = new ArrayList<>();
        for (Player p : state.getPlayers()) {
            Map<String, Object> seat = new LinkedHashMap<>();
            seat.put("id", p.getId());
            seat.put("username", p.getUsername());
            seat.put("chips", p.getChips());
            seat.put("betThisStreet", p.getBetThisStreet());
            seat.put("totalBetThisHand", p.getTotalBetThisHand());
            seat.put("folded", p.isFolded());
            seat.put("allIn", p.isAllIn());
            seat.put("inHand", p.getChips() > 0 || p.getTotalBetThisHand() > 0);
            seat.put("isDealer", p.getId().equals(table.get("dealerPlayerId")));
            seat.put("isSmallBlind", p.getId().equals(state.getSmallBlindPlayerId()));
            seat.put("isBigBlind", p.getId().equals(state.getBigBlindPlayerId()));
            boolean showCards = revealHands
                    || (viewerId != null && viewerId.equals(p.getId()))
                    || (state.getPokerStreet() == PokerStreet.SHOWDOWN && !p.isFolded());
            if (showCards && p.getHand() != null) {
                seat.put("holeCards", new ArrayList<>(p.getHand()));
            } else {
                seat.put("holeCardCount", p.getHand() == null ? 0 : p.getHand().size());
            }
            if (turnId != null && p.getId().equals(turnId)) {
                seat.put("legalActions", legalActions(state, p).stream()
                        .map(Enum::name).collect(Collectors.toList()));
            }
            seats.add(seat);
        }
        table.put("seats", seats);

        if (turnId != null) {
            Player actor = state.findPlayer(turnId);
            if (actor != null) {
                List<String> acts = legalActions(state, actor).stream()
                        .map(Enum::name).collect(Collectors.toList());
                table.put("legalActions", acts);
                table.put("toCall", Math.max(0, state.getCurrentBet() - actor.getBetThisStreet()));
            }
        }
        // Viewer-specific hole cards already handled per-seat above
        if (viewerId != null && turnId != null && viewerId.equals(turnId)) {
            // already set
        }
        return table;
    }

    public String getWinnerUsername(GameState state) {
        return playersWithChips(state).stream()
                .max((a, b) -> Integer.compare(a.getChips(), b.getChips()))
                .map(Player::getUsername)
                .orElse(state.getPlayers().isEmpty() ? null : state.getPlayers().get(0).getUsername());
    }

    public int getWinnerScore(GameState state) {
        return playersWithChips(state).stream().mapToInt(Player::getChips).max().orElse(0);
    }

    // ---- internals ----

    private void postBlinds(GameState state) {
        int dealer = state.getDealerIndex();
        int sbIdx = nextWithChips(state, dealer);
        int bbIdx = nextWithChips(state, sbIdx);
        Player sb = state.getPlayers().get(sbIdx);
        Player bb = state.getPlayers().get(bbIdx);
        int sbAmt = Math.min(state.getSmallBlind(), sb.getChips());
        int bbAmt = Math.min(state.getBigBlind(), bb.getChips());
        putChips(state, sb, sbAmt);
        putChips(state, bb, bbAmt);
        state.setSmallBlindPlayerId(sb.getId());
        state.setBigBlindPlayerId(bb.getId());
        state.setCurrentBet(bb.getBetThisStreet());
        // Blinds count as acted only for BB matching purposes — SB/BB must still act later
        sb.setActedThisStreet(false);
        bb.setActedThisStreet(false);
        state.setLastPokerAction(sb.getUsername() + " posts SB " + sbAmt
                + " · " + bb.getUsername() + " posts BB " + bbAmt);
    }

    private void putChips(GameState state, Player player, int amount) {
        if (amount <= 0) return;
        int pay = Math.min(amount, player.getChips());
        player.setChips(player.getChips() - pay);
        player.setBetThisStreet(player.getBetThisStreet() + pay);
        player.setTotalBetThisHand(player.getTotalBetThisHand() + pay);
        state.setPot(state.getPot() + pay);
        if (player.getChips() == 0) {
            player.setAllIn(true);
        }
    }

    private void resetActedExcept(GameState state, String exceptId) {
        for (Player p : state.getPlayers()) {
            if (p.getId().equals(exceptId)) continue;
            if (!p.isFolded() && !p.isAllIn() && p.getChips() > 0) {
                p.setActedThisStreet(false);
            }
        }
    }

    private boolean isStreetComplete(GameState state) {
        List<Player> active = state.getPlayers().stream()
                .filter(p -> !p.isFolded())
                .filter(p -> p.getChips() > 0 || p.isAllIn() || p.getTotalBetThisHand() > 0)
                .filter(p -> p.getHand() != null && !p.getHand().isEmpty())
                .collect(Collectors.toList());
        // Players still able to bet must have acted and matched currentBet (or be all-in)
        for (Player p : active) {
            if (p.isAllIn() || p.getChips() == 0) continue;
            if (!p.isActedThisStreet()) return false;
            if (p.getBetThisStreet() < state.getCurrentBet()) return false;
        }
        // At least one decision made, or everyone all-in
        boolean anyoneCanAct = active.stream().anyMatch(p -> !p.isAllIn() && p.getChips() > 0);
        if (!anyoneCanAct) return true;
        return active.stream().filter(p -> !p.isAllIn() && p.getChips() > 0)
                .allMatch(Player::isActedThisStreet);
    }

    private void advanceStreet(GameState state) {
        // Reset street bets
        for (Player p : state.getPlayers()) {
            p.setBetThisStreet(0);
            p.setActedThisStreet(false);
        }
        state.setCurrentBet(0);
        state.setRaisesThisStreet(0);

        PokerStreet street = state.getPokerStreet();
        switch (street) {
            case PREFLOP:
                burnAndDeal(state, 3);
                state.setPokerStreet(PokerStreet.FLOP);
                break;
            case FLOP:
                burnAndDeal(state, 1);
                state.setPokerStreet(PokerStreet.TURN);
                break;
            case TURN:
                burnAndDeal(state, 1);
                state.setPokerStreet(PokerStreet.RIVER);
                break;
            case RIVER:
                runShowdown(state);
                return;
            default:
                runShowdown(state);
                return;
        }

        // If all remaining are all-in, run out board
        boolean anyoneCanBet = state.getPlayers().stream()
                .anyMatch(p -> !p.isFolded() && !p.isAllIn() && p.getChips() > 0);
        if (!anyoneCanBet) {
            while (state.getPokerStreet() != PokerStreet.SHOWDOWN
                    && state.getPokerStreet() != PokerStreet.RIVER) {
                // deal remaining
                if (state.getPokerStreet() == PokerStreet.FLOP) {
                    burnAndDeal(state, 1);
                    state.setPokerStreet(PokerStreet.TURN);
                } else if (state.getPokerStreet() == PokerStreet.TURN) {
                    burnAndDeal(state, 1);
                    state.setPokerStreet(PokerStreet.RIVER);
                } else break;
            }
            if (state.getCommunityCards().size() < 5 && state.getPokerStreet() == PokerStreet.RIVER
                    && state.getCommunityCards().size() == 4) {
                // already on river with 5? FLOP deals 3, TURN+1, RIVER+1
            }
            // Ensure 5 community cards then showdown
            while (state.getCommunityCards().size() < 5) {
                burnAndDeal(state, 1);
            }
            state.setPokerStreet(PokerStreet.RIVER);
            runShowdown(state);
            return;
        }

        int dealer = state.getDealerIndex();
        state.setCurrentPlayerIndex(nextToAct(state, dealer));
        state.setLastPokerAction(state.getPokerStreet().name() + " dealt");
        log.debug("Poker street {} room={} pot={}", state.getPokerStreet(), state.getRoomCode(), state.getPot());
    }

    private void burnAndDeal(GameState state, int count) {
        if (!state.getPokerDeck().isEmpty()) {
            state.getPokerDeck().remove(0); // burn
        }
        for (int i = 0; i < count; i++) {
            state.getCommunityCards().add(draw(state));
        }
    }

    private void runShowdown(GameState state) {
        state.setPokerStreet(PokerStreet.SHOWDOWN);
        List<Player> contenders = state.getPlayers().stream()
                .filter(p -> !p.isFolded())
                .filter(p -> p.getHand() != null && p.getHand().size() >= 2)
                .collect(Collectors.toList());

        if (contenders.isEmpty()) {
            awardToLastStanding(state);
            return;
        }
        if (contenders.size() == 1) {
            awardPot(state, Collections.singletonList(contenders.get(0)));
            return;
        }

        long best = -1;
        List<Player> winners = new ArrayList<>();
        Map<String, Long> scores = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Player p : contenders) {
            long score = handEvaluator.evaluate(p.getHand(), state.getCommunityCards());
            scores.put(p.getId(), score);
            names.put(p.getId(), handEvaluator.categoryName(score));
            if (score > best) {
                best = score;
                winners.clear();
                winners.add(p);
            } else if (score == best) {
                winners.add(p);
            }
        }
        awardPot(state, winners);
        state.setLastPokerAction("Showdown: "
                + winners.stream().map(Player::getUsername).collect(Collectors.joining(", "))
                + " win with " + names.get(winners.get(0).getId()));
        // Attach showdown meta via last action; publicTable reveals hole cards
        syncScores(state);
    }

    private void awardToLastStanding(GameState state) {
        Player winner = state.getPlayers().stream()
                .filter(p -> !p.isFolded())
                .findFirst()
                .orElse(null);
        if (winner == null) return;
        awardPot(state, Collections.singletonList(winner));
        state.setPokerStreet(PokerStreet.SHOWDOWN);
        state.setLastPokerAction(winner.getUsername() + " wins pot (others folded)");
        syncScores(state);
    }

    private void awardPot(GameState state, List<Player> winners) {
        int pot = state.getPot();
        if (pot <= 0 || winners.isEmpty()) {
            state.setPot(0);
            return;
        }
        int share = pot / winners.size();
        int rem = pot % winners.size();
        for (int i = 0; i < winners.size(); i++) {
            int gain = share + (i == 0 ? rem : 0);
            winners.get(i).setChips(winners.get(i).getChips() + gain);
        }
        state.setPot(0);
        state.setPhase(GamePhase.ROUND_END);
    }

    private int nextToAct(GameState state, int fromIdx) {
        int n = state.getPlayers().size();
        for (int i = 1; i <= n; i++) {
            int idx = (fromIdx + i) % n;
            Player p = state.getPlayers().get(idx);
            if (p.isFolded() || p.isAllIn()) continue;
            if (p.getChips() <= 0) continue;
            if (p.getHand() == null || p.getHand().isEmpty()) continue;
            return idx;
        }
        return fromIdx;
    }

    private int nextWithChips(GameState state, int fromIdx) {
        int n = state.getPlayers().size();
        for (int i = 1; i <= n; i++) {
            int idx = (fromIdx + i) % n;
            if (state.getPlayers().get(idx).getChips() > 0) return idx;
        }
        return fromIdx;
    }

    private int firstWithChips(GameState state) {
        for (int i = 0; i < state.getPlayers().size(); i++) {
            if (state.getPlayers().get(i).getChips() > 0) return i;
        }
        return 0;
    }

    private int countNotFolded(GameState state) {
        return (int) state.getPlayers().stream()
                .filter(p -> !p.isFolded())
                .filter(p -> p.getHand() != null && !p.getHand().isEmpty())
                .count();
    }

    private List<Player> playersWithChips(GameState state) {
        return state.getPlayers().stream()
                .filter(p -> p.getChips() > 0)
                .collect(Collectors.toList());
    }

    private void syncScores(GameState state) {
        for (Player p : state.getPlayers()) {
            state.getScores().put(p.getId(), p.getChips());
        }
    }

    private Card draw(GameState state) {
        if (state.getPokerDeck().isEmpty()) {
            throw new IllegalStateException("Poker deck empty");
        }
        return state.getPokerDeck().remove(0);
    }

    private List<Card> createShuffledSingleDeck() {
        List<Card> deck = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(Card.builder().suit(suit).rank(rank).deckIndex(0).playOrder(0).build());
            }
        }
        Collections.shuffle(deck);
        return deck;
    }
}
