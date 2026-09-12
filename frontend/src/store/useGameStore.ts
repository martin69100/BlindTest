import { create } from 'zustand';
import { playBuzzerSound } from '../utils/audioEffects';
import type {
  AnswerWrongEvent,
  FreeAnswerCorrectEvent,
  FreeAnswerWrongEvent,
  LeaderboardEntry,
  LobbyData,
  MatchFinishedEvent,
  PlayerBuzzedEvent,
  RevealedTrack,
  RoundEndEvent,
  RoundHistoryItem,
  RoundStartEvent,
  StealOpenEvent,
  WrongGuess,
} from '../types';

export type GamePhase = 'LOBBY' | 'WAITING' | 'PLAYING' | 'BUZZED' | 'BONUS' | 'REVEAL' | 'FINISHED';

export interface FreeFeedItem {
  id: string;
  playerId: string;
  playerName: string;
  foundType: 'TITLE' | 'ARTIST';
  foundName: string;
  playerTeam?: string;
  timestamp: number;
}

interface GameState {
  gameId: string | null;
  isSolo: boolean;
  isCustom: boolean;
  lobbyCode: string | null;
  activeLobby: LobbyData | null;
  gameMode: 'BUZZER' | 'NO_BUZZER';
  teamMode: 'INDIVIDUAL' | 'TEAMS';
  teamScores: Record<string, number>;
  playerTeams: Record<string, string>;
  myFoundTitle: boolean;
  myFoundArtist: boolean;
  freeFeedEvents: FreeFeedItem[];
  phase: GamePhase;
  roundNumber: number;
  totalRounds: number;
  audioUrl: string | null;
  remainingAudioMs: number;

  // Buzzer & Saisie
  buzzerPlayerId: string | null;
  buzzerName: string | null;
  firstFoundType: 'TITLE' | 'ARTIST' | null;
  firstFoundName: string | null;
  inputTimeoutSeconds: number;
  bonusDurationSeconds: number;
  failedPlayerIds: string[];
  lastWrongGuess: WrongGuess | null;
  roundWrongGuesses: WrongGuess[];

  // Joueurs & Scores
  player1Id: string | null;
  player2Id: string | null;
  player1Name: string | null;
  player2Name: string | null;
  player1Score: number;
  player2Score: number;
  playerScores: Record<string, number>;
  leaderboard: LeaderboardEntry[];

  // Révélation & Fin
  revealedTrack: RevealedTrack | null;
  lastRoundResult: {
    titleFound: boolean;
    artistFound: boolean;
    titleFoundByPlayerId: string | null;
    artistFoundByPlayerId: string | null;
    titleFoundByName?: string;
    artistFoundByName?: string;
    wrongGuesses?: WrongGuess[];
  } | null;
  roundHistory: RoundHistoryItem[];
  isLastRound: boolean;
  matchResult: MatchFinishedEvent | null;

  // Actions
  initGame: (
    gameId: string,
    isSolo?: boolean,
    player1Id?: string,
    player2Id?: string,
    player1Name?: string,
    player2Name?: string
  ) => void;
  initCustomGame: (
    gameId: string,
    lobbyCode: string,
    totalRounds?: number,
    gameMode?: 'BUZZER' | 'NO_BUZZER',
    teamMode?: 'INDIVIDUAL' | 'TEAMS'
  ) => void;
  setActiveLobby: (lobby: LobbyData | null) => void;
  returnToCustomLobby: () => void;
  onRoundStart: (event: RoundStartEvent) => void;
  onPlayerBuzzed: (event: PlayerBuzzedEvent) => void;
  onFirstAnswerCorrect: (event: any) => void;
  onFreeAnswerCorrect: (event: FreeAnswerCorrectEvent, currentUserId?: string) => void;
  onFreeAnswerWrong: (event: FreeAnswerWrongEvent) => void;
  onStealOpen: (event: StealOpenEvent) => void;
  onAnswerWrong: (event: AnswerWrongEvent) => void;
  onRoundEnd: (event: RoundEndEvent) => void;
  onMatchFinished: (event: MatchFinishedEvent) => void;
  resetGame: () => void;
}

export const useGameStore = create<GameState>((set) => ({
  gameId: null,
  isSolo: false,
  isCustom: false,
  lobbyCode: null,
  activeLobby: null,
  gameMode: 'BUZZER',
  teamMode: 'INDIVIDUAL',
  teamScores: {},
  playerTeams: {},
  myFoundTitle: false,
  myFoundArtist: false,
  freeFeedEvents: [],
  phase: 'LOBBY',
  roundNumber: 0,
  totalRounds: 10,
  audioUrl: null,
  remainingAudioMs: 20000,

  buzzerPlayerId: null,
  buzzerName: null,
  firstFoundType: null,
  firstFoundName: null,
  inputTimeoutSeconds: 8,
  bonusDurationSeconds: 10,
  failedPlayerIds: [],
  lastWrongGuess: null,
  roundWrongGuesses: [],

  player1Id: null,
  player2Id: null,
  player1Name: null,
  player2Name: null,
  player1Score: 0,
  player2Score: 0,
  playerScores: {},
  leaderboard: [],

  revealedTrack: null,
  lastRoundResult: null,
  roundHistory: [],
  isLastRound: false,
  matchResult: null,

  setActiveLobby: (lobby) => set({ activeLobby: lobby }),

  initCustomGame: (gameId, lobbyCode, totalRounds = 10, gameMode = 'BUZZER', teamMode = 'INDIVIDUAL') => {
    set({
      gameId,
      isSolo: false,
      isCustom: true,
      lobbyCode,
      gameMode,
      teamMode,
      teamScores: { BLUE: 0, RED: 0 },
      playerTeams: {},
      myFoundTitle: false,
      myFoundArtist: false,
      freeFeedEvents: [],
      phase: 'WAITING',
      roundNumber: 0,
      totalRounds,
      player1Score: 0,
      player2Score: 0,
      playerScores: {},
      leaderboard: [],
      revealedTrack: null,
      lastRoundResult: null,
      roundHistory: [],
      matchResult: null,
      failedPlayerIds: [],
      lastWrongGuess: null,
      roundWrongGuesses: [],
    });
  },

  returnToCustomLobby: () => {
    set({
      phase: 'LOBBY',
      gameId: null,
      audioUrl: null,
      buzzerPlayerId: null,
      buzzerName: null,
      firstFoundType: null,
      firstFoundName: null,
      myFoundTitle: false,
      myFoundArtist: false,
      freeFeedEvents: [],
      revealedTrack: null,
      lastRoundResult: null,
      matchResult: null,
      failedPlayerIds: [],
      lastWrongGuess: null,
      roundWrongGuesses: [],
    });
  },

  initGame: (gameId, isSolo = false, player1Id, player2Id, player1Name, player2Name) => {
    set({
      gameId,
      isSolo,
      isCustom: false,
      lobbyCode: null,
      gameMode: 'BUZZER',
      teamMode: 'INDIVIDUAL',
      teamScores: {},
      playerTeams: {},
      myFoundTitle: false,
      myFoundArtist: false,
      freeFeedEvents: [],
      phase: 'WAITING',
      roundNumber: 0,
      player1Score: 0,
      player2Score: 0,
      playerScores: {},
      leaderboard: [],
      revealedTrack: null,
      lastRoundResult: null,
      roundHistory: [],
      matchResult: null,
      player1Id: player1Id || null,
      player2Id: player2Id || null,
      player1Name: player1Name || null,
      player2Name: player2Name || null,
      failedPlayerIds: [],
      lastWrongGuess: null,
      roundWrongGuesses: [],
    });
  },

  onRoundStart: (event) => {
    set((state) => ({
      phase: 'PLAYING',
      roundNumber: event.roundNumber,
      totalRounds: event.totalRounds,
      audioUrl: event.previewUrl,
      remainingAudioMs: event.durationSeconds * 1000,
      buzzerPlayerId: null,
      buzzerName: null,
      firstFoundType: null,
      firstFoundName: null,
      myFoundTitle: false,
      myFoundArtist: false,
      freeFeedEvents: [],
      revealedTrack: null,
      lastRoundResult: null,
      failedPlayerIds: [],
      lastWrongGuess: null,
      roundWrongGuesses: [],
      isCustom: event.isCustom ?? state.isCustom,
      lobbyCode: event.lobbyCode ?? state.lobbyCode,
      gameMode: (event.gameMode as any) ?? state.gameMode,
      teamMode: (event.teamMode as any) ?? state.teamMode,
      teamScores: event.teamScores ?? state.teamScores,
      playerTeams: event.playerTeams ?? state.playerTeams,
      playerScores: event.playerScores ?? state.playerScores,
      leaderboard: event.leaderboard ?? state.leaderboard,
      player1Id: event.player1Id || state.player1Id,
      player2Id: event.player2Id || state.player2Id,
      player1Name: event.player1Name || state.player1Name,
      player2Name: event.player2Name || state.player2Name,
    }));
  },

  onPlayerBuzzed: (event) => {
    playBuzzerSound();
    set({
      phase: 'BUZZED',
      buzzerPlayerId: event.buzzerPlayerId,
      buzzerName: event.buzzerName,
      inputTimeoutSeconds: event.inputTimeoutSeconds,
      remainingAudioMs: event.remainingAudioMs,
    });
  },

  onFirstAnswerCorrect: (event) => {
    set((state) => ({
      phase: 'BONUS',
      firstFoundType: event.foundType,
      firstFoundName: event.foundName,
      bonusDurationSeconds: event.bonusDurationSeconds,
      player1Score: event.currentScores?.player1 ?? state.player1Score,
      player2Score: event.currentScores?.player2 ?? state.player2Score,
      playerScores: event.playerScores ?? state.playerScores,
      teamScores: event.teamScores ?? state.teamScores,
      leaderboard: event.leaderboard ?? state.leaderboard,
    }));
  },

  onFreeAnswerCorrect: (event, currentUserId) => {
    set((state) => {
      const isMe = currentUserId && String(event.playerId) === String(currentUserId);
      const newFeedItem: FreeFeedItem = {
        id: `${Date.now()}-${Math.random()}`,
        playerId: event.playerId,
        playerName: event.playerName,
        foundType: event.foundType,
        foundName: event.foundName,
        playerTeam: event.playerTeam,
        timestamp: Date.now(),
      };
      return {
        myFoundTitle: isMe && event.foundType === 'TITLE' ? true : state.myFoundTitle,
        myFoundArtist: isMe && event.foundType === 'ARTIST' ? true : state.myFoundArtist,
        freeFeedEvents: [newFeedItem, ...state.freeFeedEvents].slice(0, 20),
        playerScores: event.playerScores ?? state.playerScores,
        teamScores: event.teamScores ?? state.teamScores,
        leaderboard: event.leaderboard ?? state.leaderboard,
      };
    });
  },

  onFreeAnswerWrong: (event) => {
    const wrongEntry: WrongGuess = {
      playerId: event.playerId,
      playerName: event.playerName || 'Joueur',
      guess: event.guess,
    };
    set((state) => ({
      lastWrongGuess: wrongEntry,
      roundWrongGuesses: [...state.roundWrongGuesses, wrongEntry],
    }));
  },

  onAnswerWrong: (event) => {
    const wrongEntry: WrongGuess = {
      playerId: event.playerId,
      playerName: event.playerName,
      guess: event.guess,
    };
    set((state) => ({
      lastWrongGuess: wrongEntry,
      roundWrongGuesses: [...state.roundWrongGuesses, wrongEntry],
    }));
  },

  onStealOpen: (event) => {
    set((state) => {
      let nextWrongGuesses = state.roundWrongGuesses;
      let nextLastWrong = state.lastWrongGuess;

      if (event.wrongGuess && event.failedPlayerName) {
        const entry: WrongGuess = {
          playerId: event.failedPlayerId,
          playerName: event.failedPlayerName,
          guess: event.wrongGuess,
        };
        nextLastWrong = entry;
        const exists = state.roundWrongGuesses.some(
          (w) => w.playerId === event.failedPlayerId && w.guess === event.wrongGuess
        );
        if (!exists) {
          nextWrongGuesses = [...state.roundWrongGuesses, entry];
        }
      }

      return {
        phase: 'PLAYING',
        buzzerPlayerId: null,
        buzzerName: null,
        remainingAudioMs: event.remainingAudioMs,
        lastWrongGuess: nextLastWrong,
        roundWrongGuesses: nextWrongGuesses,
        failedPlayerIds: state.failedPlayerIds.includes(event.failedPlayerId)
          ? state.failedPlayerIds
          : [...state.failedPlayerIds, event.failedPlayerId],
        ...(event.currentScores
          ? {
              player1Score: event.currentScores.player1,
              player2Score: event.currentScores.player2,
            }
          : {}),
        ...(event.playerScores ? { playerScores: event.playerScores } : {}),
        ...(event.teamScores ? { teamScores: event.teamScores } : {}),
        ...(event.leaderboard ? { leaderboard: event.leaderboard } : {}),
        ...(event.firstFoundType ? { firstFoundType: event.firstFoundType } : {}),
      };
    });
  },

  onRoundEnd: (event) => {
    set((state) => {
      const wrongGuesses =
        event.wrongGuesses && event.wrongGuesses.length > 0
          ? event.wrongGuesses
          : state.roundWrongGuesses;

      const historyItem: RoundHistoryItem = {
        roundNumber: event.roundNumber,
        title: event.track.title,
        artist: event.track.artist,
        albumName: event.track.albumName,
        albumCoverUrl: event.track.albumCoverUrl,
        previewUrl: event.track.previewUrl || state.audioUrl || undefined,
        titleFound: Boolean(event.titleFound),
        artistFound: Boolean(event.artistFound),
        titleFoundByPlayerId: event.titleFoundByPlayerId || null,
        artistFoundByPlayerId: event.artistFoundByPlayerId || null,
        titleFoundByName: event.titleFoundByName,
        artistFoundByName: event.artistFoundByName,
        player1Score: event.scores.player1,
        player2Score: event.scores.player2,
        playerScores: event.playerScores,
        teamScores: event.teamScores ?? state.teamScores,
        wrongGuesses,
      };

      const filtered = state.roundHistory.filter((r) => r.roundNumber !== event.roundNumber);

      return {
        phase: 'REVEAL',
        revealedTrack: event.track,
        player1Score: event.scores.player1,
        player2Score: event.scores.player2,
        playerScores: event.playerScores ?? state.playerScores,
        teamScores: event.teamScores ?? state.teamScores,
        playerTeams: event.playerTeams ?? state.playerTeams,
        leaderboard: event.leaderboard ?? state.leaderboard,
        isCustom: event.isCustom ?? state.isCustom,
        lobbyCode: event.lobbyCode ?? state.lobbyCode,
        isLastRound: event.isLastRound,
        lastRoundResult: {
          titleFound: Boolean(event.titleFound),
          artistFound: Boolean(event.artistFound),
          titleFoundByPlayerId: event.titleFoundByPlayerId || null,
          artistFoundByPlayerId: event.artistFoundByPlayerId || null,
          titleFoundByName: event.titleFoundByName,
          artistFoundByName: event.artistFoundByName,
          wrongGuesses,
        },
        roundWrongGuesses: wrongGuesses,
        roundHistory: [...filtered, historyItem],
        player1Id: event.player1Id || state.player1Id,
        player2Id: event.player2Id || state.player2Id,
        player1Name: event.player1Name || state.player1Name,
        player2Name: event.player2Name || state.player2Name,
      };
    });
  },

  onMatchFinished: (event) => {
    set((state) => {
      const p1 = event.player1Score ?? event.finalScore ?? state.player1Score ?? 0;
      const p2 = event.player2Score ?? state.player2Score ?? 0;
      return {
        phase: 'FINISHED',
        matchResult: event,
        player1Score: p1,
        player2Score: p2,
        isCustom: event.isCustom ?? state.isCustom,
        lobbyCode: event.lobbyCode ?? state.lobbyCode,
        playerScores: event.playerScores ?? event.scores ?? state.playerScores,
        teamScores: event.teamScores ?? state.teamScores,
        playerTeams: event.playerTeams ?? state.playerTeams,
        leaderboard: event.leaderboard ?? state.leaderboard,
        roundHistory: (event.roundHistory && event.roundHistory.length > 0) ? event.roundHistory : state.roundHistory,
        player1Id: event.player1Id || state.player1Id,
        player2Id: event.player2Id || state.player2Id,
        player1Name: event.player1Name || state.player1Name,
        player2Name: event.player2Name || state.player2Name,
      };
    });
  },

  resetGame: () => {
    set({
      gameId: null,
      isCustom: false,
      lobbyCode: null,
      activeLobby: null,
      gameMode: 'BUZZER',
      teamMode: 'INDIVIDUAL',
      teamScores: {},
      playerTeams: {},
      myFoundTitle: false,
      myFoundArtist: false,
      freeFeedEvents: [],
      phase: 'LOBBY',
      roundNumber: 0,
      audioUrl: null,
      buzzerPlayerId: null,
      buzzerName: null,
      firstFoundType: null,
      firstFoundName: null,
      revealedTrack: null,
      lastRoundResult: null,
      roundHistory: [],
      matchResult: null,
      player1Id: null,
      player2Id: null,
      player1Name: null,
      player2Name: null,
      failedPlayerIds: [],
      lastWrongGuess: null,
      roundWrongGuesses: [],
      player1Score: 0,
      player2Score: 0,
      playerScores: {},
      leaderboard: [],
    });
  },

}));
